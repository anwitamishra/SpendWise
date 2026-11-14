package com.spendwise.dto;

public class AuthRequest {
    private String email;
    private String username;
    private String identifier;
    private String password;
    private String displayName;
    private String guestIdentifier;
    private String token;
    private String currentPassword;
    private String newPassword;
    private String otp;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getGuestIdentifier() { return guestIdentifier; }
    public void setGuestIdentifier(String guestIdentifier) { this.guestIdentifier = guestIdentifier; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}