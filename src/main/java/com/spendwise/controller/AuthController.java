package com.spendwise.controller;

import com.spendwise.dto.ApiResponse;
import com.spendwise.dto.AuthRequest;
import com.spendwise.dto.AuthResult;
import com.spendwise.dto.OtpRequestResult;
import com.spendwise.dto.RegistrationRequest;
import com.spendwise.model.User;
import com.spendwise.service.AuthService;
import com.spendwise.exception.UnauthorizedException;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResult>> register(@Valid @RequestBody RegistrationRequest request) {
        AuthResult result = authService.register(request.getUsername(), request.getEmail(), request.getPassword(),
                request.getDisplayName(), request.getGuestIdentifier());
        return ResponseEntity.status(201).body(ApiResponse.ok("Account created", result));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResult>> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Signed in", authService.login(request.getIdentifier(), request.getPassword())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authService.resolveSession(bearerToken(authorization));
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("Session expired"));
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(bearerToken(authorization));
        return ResponseEntity.ok(ApiResponse.ok("Signed out", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(@RequestBody AuthRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("If an account exists for that email, a reset link has been sent.", Map.of()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody AuthRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password updated. Sign in with your new password.", null));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthResult>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AuthRequest request) {
        User user = authenticatedUser(authorization);
        AuthResult result = authService.completePasswordChange(user, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password updated", result));
    }

    @PostMapping("/password-change/request")
    public ResponseEntity<ApiResponse<OtpRequestResult>> requestPasswordChangeOtp(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ResponseEntity.ok(ApiResponse.ok("Verification code sent", authService.requestPasswordChangeOtp(authenticatedUser(authorization))));
    }

    @PostMapping("/password-change/verify")
    public ResponseEntity<ApiResponse<Void>> verifyPasswordChangeOtp(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AuthRequest request) {
        authService.verifyPasswordChangeOtp(authenticatedUser(authorization), request.getOtp());
        return ResponseEntity.ok(ApiResponse.ok("Email verified", null));
    }

    @PostMapping("/password-change/complete")
    public ResponseEntity<ApiResponse<AuthResult>> completePasswordChange(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AuthRequest request) {
        AuthResult result = authService.completePasswordChange(authenticatedUser(authorization), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password updated successfully", result));
    }

    private User authenticatedUser(String authorization) {
        User user = authService.resolveSession(bearerToken(authorization));
        if (user == null) throw new UnauthorizedException("Please sign in again");
        return user;
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        return authorization.substring(7).trim();
    }
}