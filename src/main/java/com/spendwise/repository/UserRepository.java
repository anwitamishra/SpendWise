package com.spendwise.repository;

import com.spendwise.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final @NonNull RowMapper<User> userRowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setDisplayName(rs.getString("display_name"));
        user.setGuestIdentifier(rs.getString("guest_identifier"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            user.setCreatedAt(ts.toLocalDateTime());
        }
        return user;
    };

    public User save(User user) {
        String sql = "INSERT INTO users (display_name, guest_identifier) VALUES (?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getDisplayName());
            ps.setString(2, user.getGuestIdentifier());
            return ps;
        }, keyHolder);

        Long generatedId = KeyHolderUtil.extractId(keyHolder);
        if (generatedId != null) {
            user.setId(generatedId);
            findById(generatedId).ifPresent(saved -> user.setCreatedAt(saved.getCreatedAt()));
        }
        return user;
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT id, display_name, guest_identifier, username, email, created_at FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findByGuestIdentifier(String guestIdentifier) {
        String sql = "SELECT id, display_name, guest_identifier, username, email, created_at FROM users WHERE guest_identifier = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, guestIdentifier);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int updateDisplayName(Long id, String displayName) {
        String sql = "UPDATE users SET display_name = ? WHERE id = ?";
        return jdbcTemplate.update(sql, displayName, id);
    }

    public Optional<User> findByEmail(String email) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, display_name, guest_identifier, username, email, created_at FROM users WHERE email = ?",
                    userRowMapper, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean hasAccount(String guestIdentifier) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE guest_identifier = ? AND password_hash IS NOT NULL",
                Integer.class, guestIdentifier);
        return count != null && count > 0;
    }

    public Optional<User> findByLoginIdentifier(String identifier) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, display_name, guest_identifier, username, email, created_at FROM users " +
                            "WHERE LOWER(email) = ? OR LOWER(username) = ?",
                    userRowMapper, identifier, identifier));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public String findPasswordHashByUserId(Long userId) {
        try {
            return jdbcTemplate.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean usernameExists(String username) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE LOWER(username) = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public void setAccountCredentials(Long userId, String username, String email, String passwordHash) {
        jdbcTemplate.update("UPDATE users SET username = ?, email = ?, password_hash = ? WHERE id = ?",
                username, email, passwordHash, userId);
    }

    public void updatePassword(Long userId, String passwordHash) {
        jdbcTemplate.update("UPDATE users SET password_hash = ?, reset_token_hash = NULL, reset_token_expires_at = NULL WHERE id = ?",
                passwordHash, userId);
    }

    public void setPasswordResetToken(Long userId, String tokenHash, java.sql.Timestamp expiresAt) {
        jdbcTemplate.update("UPDATE users SET reset_token_hash = ?, reset_token_expires_at = ? WHERE id = ?",
                tokenHash, expiresAt, userId);
    }

    public Optional<User> findByValidResetToken(String tokenHash) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, display_name, guest_identifier, username, email, created_at FROM users " +
                            "WHERE reset_token_hash = ? AND reset_token_expires_at > CURRENT_TIMESTAMP",
                    userRowMapper, tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void saveSession(Long userId, String tokenHash, java.sql.Timestamp expiresAt) {
        jdbcTemplate.update("INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId, tokenHash, expiresAt);
    }

    public Optional<User> findBySessionTokenHash(String tokenHash) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT u.id, u.display_name, u.guest_identifier, u.username, u.email, u.created_at " +
                            "FROM users u JOIN auth_sessions s ON s.user_id = u.id " +
                            "WHERE s.token_hash = ? AND s.expires_at > CURRENT_TIMESTAMP",
                    userRowMapper, tokenHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void deleteSession(String tokenHash) {
        jdbcTemplate.update("DELETE FROM auth_sessions WHERE token_hash = ?", tokenHash);
    }

    public void deleteSessionsForUser(Long userId) {
        jdbcTemplate.update("DELETE FROM auth_sessions WHERE user_id = ?", userId);
    }

    public int countRecentPasswordChangeOtps(Long userId, Timestamp since) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_change_otps WHERE user_id = ? AND purpose = 'PASSWORD_CHANGE' AND created_at >= ?",
                Integer.class, userId, since);
        return count == null ? 0 : count;
    }

    public void lockUserForPasswordOtp(Long userId) {
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
    }

    public Timestamp latestPasswordChangeOtpCreatedAt(Long userId) {
        return jdbcTemplate.query("SELECT MAX(created_at) AS created_at FROM password_change_otps " +
                        "WHERE user_id = ? AND purpose = 'PASSWORD_CHANGE'",
                rs -> rs.next() ? rs.getTimestamp("created_at") : null, userId);
    }

    public void invalidatePasswordChangeOtps(Long userId, Timestamp now) {
        jdbcTemplate.update("UPDATE password_change_otps SET consumed_at = ? WHERE user_id = ? " +
                        "AND purpose = 'PASSWORD_CHANGE' AND consumed_at IS NULL", now, userId);
    }

    public long createPasswordChangeOtp(Long userId, String otpHash, Timestamp createdAt, Timestamp expiresAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO password_change_otps (user_id, purpose, otp_hash, created_at, expires_at) " +
                            "VALUES (?, 'PASSWORD_CHANGE', ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, otpHash);
            statement.setTimestamp(3, createdAt);
            statement.setTimestamp(4, expiresAt);
            return statement;
        }, keyHolder);
        Long id = KeyHolderUtil.extractId(keyHolder);
        if (id == null) throw new IllegalStateException("OTP challenge could not be saved");
        return id;
    }

    public PasswordChangeOtp findActivePasswordChangeOtp(Long userId, Timestamp now) {
        try {
            return jdbcTemplate.queryForObject("SELECT id, otp_hash, expires_at, attempts, verified_at " +
                            "FROM password_change_otps WHERE user_id = ? AND purpose = 'PASSWORD_CHANGE' " +
                            "AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1",
                    (rs, rowNum) -> new PasswordChangeOtp(rs.getLong("id"), rs.getString("otp_hash"),
                            rs.getTimestamp("expires_at"), rs.getInt("attempts"), rs.getTimestamp("verified_at")),
                    userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void incrementPasswordChangeOtpAttempts(long challengeId) {
        jdbcTemplate.update("UPDATE password_change_otps SET attempts = attempts + 1 WHERE id = ?", challengeId);
    }

    public void verifyPasswordChangeOtp(long challengeId, Timestamp verifiedAt) {
        jdbcTemplate.update("UPDATE password_change_otps SET verified_at = ? WHERE id = ?", verifiedAt, challengeId);
    }

    public void consumePasswordChangeOtp(long challengeId, Timestamp consumedAt) {
        jdbcTemplate.update("UPDATE password_change_otps SET consumed_at = ? WHERE id = ?", consumedAt, challengeId);
    }

    public record PasswordChangeOtp(long id, String otpHash, Timestamp expiresAt, int attempts, Timestamp verifiedAt) {}
}
