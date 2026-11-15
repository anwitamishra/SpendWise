package com.spendwise.service;

import com.spendwise.dto.AuthResult;
import com.spendwise.exception.BadRequestException;
import com.spendwise.exception.ConflictException;
import com.spendwise.exception.RateLimitException;
import com.spendwise.exception.UnauthorizedException;
import com.spendwise.model.User;
import com.spendwise.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_TTL_SECONDS = 300;
    private static final int OTP_RESEND_DELAY_SECONDS = 60;
    private static final int OTP_MAX_REQUESTS_PER_WINDOW = 3;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String publicUrl;
    private final String mailFrom;

    public AuthService(UserRepository userRepository, ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${app.public-url:http://localhost:8080}") String publicUrl,
                       @Value("${app.mail.from:SpendWise <no-reply@localhost>}") String mailFrom) {
        this.userRepository = userRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.publicUrl = publicUrl;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public AuthResult register(String usernameInput, String emailInput, String password, String displayName, String guestIdentifier) {
        String username = usernameInput == null ? "" : usernameInput.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BadRequestException("Username must be 3–30 characters using letters, numbers, or underscores");
        }
        String email = normalizeEmail(emailInput);
        validatePassword(password);
        String name = displayName == null || displayName.isBlank() ? username : displayName.trim();
        if (name.length() > 100) throw new BadRequestException("Display name must be at most 100 characters");
        if (userRepository.findByEmail(email).isPresent()) throw new ConflictException("An account already exists for this email");
        if (userRepository.usernameExists(username)) throw new ConflictException("That username is already taken");

        User user;
        if (guestIdentifier != null && !guestIdentifier.isBlank()) {
            user = userRepository.findByGuestIdentifier(guestIdentifier.trim())
                    .orElseThrow(() -> new BadRequestException("Guest session not found; refresh and try again"));
            if (userRepository.hasAccount(user.getGuestIdentifier())) throw new ConflictException("This guest session already has an account");
            userRepository.updateDisplayName(user.getId(), name);
        } else {
            user = new User();
            user.setDisplayName(name);
            user.setGuestIdentifier(java.util.UUID.randomUUID().toString());
            userRepository.save(user);
        }
        try {
            userRepository.setAccountCredentials(user.getId(), username, email, passwordEncoder.encode(password));
        } catch (DuplicateKeyException ex) {
            throw new ConflictException("That username or email is already registered");
        }
        user.setDisplayName(name);
        user.setUsername(username);
        user.setEmail(email);
        return createSession(user);
    }

    @Transactional
    public AuthResult login(String identifierInput, String password) {
        String identifier = identifierInput == null ? "" : identifierInput.trim().toLowerCase(Locale.ROOT);
        if (identifier.isBlank() || identifier.length() > 254) throw new BadRequestException("Enter your username or email");
        User user = userRepository.findByLoginIdentifier(identifier)
                .orElseThrow(() -> new UnauthorizedException("Username/email or password is incorrect"));
        String passwordHash = userRepository.findPasswordHashByUserId(user.getId());
        if (password == null || passwordHash == null || !passwordEncoder.matches(password, passwordHash)) {
            throw new UnauthorizedException("Username/email or password is incorrect");
        }
        return createSession(user);
    }

    public User resolveSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        return userRepository.findBySessionTokenHash(hash(rawToken)).orElse(null);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) userRepository.deleteSession(hash(rawToken));
    }

    @Transactional
    public com.spendwise.dto.OtpRequestResult requestPasswordChangeOtp(User user) {
        userRepository.lockUserForPasswordOtp(user.getId());
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("This account has no registered email address");
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Timestamp windowStart = Timestamp.valueOf(LocalDateTime.now().minusMinutes(15));
        if (userRepository.countRecentPasswordChangeOtps(user.getId(), windowStart) >= OTP_MAX_REQUESTS_PER_WINDOW) {
            throw new RateLimitException("Too many OTP requests. Try again in 15 minutes.");
        }
        Timestamp latestCreatedAt = userRepository.latestPasswordChangeOtpCreatedAt(user.getId());
        if (latestCreatedAt != null) {
            long secondsSinceLastRequest = Duration.between(latestCreatedAt.toLocalDateTime(), now.toLocalDateTime()).getSeconds();
            if (secondsSinceLastRequest < OTP_RESEND_DELAY_SECONDS) {
                throw new RateLimitException("Please wait before requesting another code.");
            }
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Password-change email is not configured. Configure SMTP first.");
        String otp = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        userRepository.invalidatePasswordChangeOtps(user.getId(), now);
        userRepository.createPasswordChangeOtp(user.getId(), passwordEncoder.encode(otp), now,
                Timestamp.valueOf(now.toLocalDateTime().plusSeconds(OTP_TTL_SECONDS)));
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(Objects.requireNonNull(mailFrom));
            helper.setTo(Objects.requireNonNull(user.getEmail()));
            helper.setSubject("Your SpendWise password-change code");
            helper.setText("Your password-change verification code is " + otp +
                    ". It expires in 5 minutes. If you did not request this, secure your SpendWise account.");
            sender.send(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Password-change email could not be sent. Check SMTP configuration.");
        }
        return new com.spendwise.dto.OtpRequestResult(maskEmail(user.getEmail()), OTP_TTL_SECONDS, OTP_RESEND_DELAY_SECONDS);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public void verifyPasswordChangeOtp(User user, String otp) {
        userRepository.lockUserForPasswordOtp(user.getId());
        if (otp == null || !otp.matches("^[0-9]{6}$")) throw new BadRequestException("Enter the 6-digit code");
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        UserRepository.PasswordChangeOtp challenge = userRepository.findActivePasswordChangeOtp(user.getId(), now);
        if (challenge == null) throw new BadRequestException("Request a new verification code");
        if (challenge.verifiedAt() != null) return;
        if (!challenge.expiresAt().after(now)) throw new BadRequestException("This code has expired. Request a new one.");
        if (challenge.attempts() >= OTP_MAX_ATTEMPTS) throw new RateLimitException("Too many incorrect codes. Request a new code later.");
        if (!passwordEncoder.matches(otp, challenge.otpHash())) {
            userRepository.incrementPasswordChangeOtpAttempts(challenge.id());
            throw new BadRequestException("That verification code is incorrect");
        }
        userRepository.verifyPasswordChangeOtp(challenge.id(), now);
    }

    @Transactional
    public AuthResult completePasswordChange(User user, String newPassword) {
        userRepository.lockUserForPasswordOtp(user.getId());
        validatePassword(newPassword);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        UserRepository.PasswordChangeOtp challenge = userRepository.findActivePasswordChangeOtp(user.getId(), now);
        if (challenge == null || challenge.verifiedAt() == null || !challenge.expiresAt().after(now)) {
            throw new UnauthorizedException("Verify your email code before changing your password");
        }
        userRepository.consumePasswordChangeOtp(challenge.id(), now);
        userRepository.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
        userRepository.deleteSessionsForUser(user.getId());
        return createSession(user);
    }

    @Transactional
    public void requestPasswordReset(String emailInput) {
        String email = normalizeEmail(emailInput);
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Password reset email is not configured. Configure SMTP before requesting a reset.");
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return;

        String rawToken = randomToken();
        userRepository.setPasswordResetToken(user.getId(), hash(rawToken),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(30)));
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(Objects.requireNonNull(mailFrom));
            helper.setTo(Objects.requireNonNull(email));
            helper.setSubject("Reset your SpendWise password");
            helper.setText("Use this link within 30 minutes to reset your password:\n\n" +
                    publicUrl + "/#reset-password?token=" + rawToken + "\n\nIf you did not request this, you can ignore this email.");
            sender.send(message);
        } catch (Exception ex) {
            userRepository.setPasswordResetToken(user.getId(), null, null);
            throw new IllegalStateException("Password reset email could not be sent. Check SMTP configuration.");
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) throw new BadRequestException("Password reset link is invalid or expired");
        validatePassword(newPassword);
        User user = userRepository.findByValidResetToken(hash(rawToken))
                .orElseThrow(() -> new BadRequestException("Password reset link is invalid or expired"));
        userRepository.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
        userRepository.deleteSessionsForUser(user.getId());
    }

    private AuthResult createSession(User user) {
        String rawToken = randomToken();
        userRepository.saveSession(user.getId(), hash(rawToken), Timestamp.valueOf(LocalDateTime.now().plusDays(30)));
        return new AuthResult(rawToken, user);
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) throw new BadRequestException("Enter a valid email address");
        return email;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 72) {
            throw new BadRequestException("Password must be between 10 and 72 characters");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "*".repeat(Math.max(1, at)) + email.substring(at);
        return email.substring(0, 1) + "*".repeat(Math.min(6, at - 1)) + email.substring(at);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to securely process token", e);
        }
    }
}