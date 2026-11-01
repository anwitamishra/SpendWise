package com.spendwise.dto;

public class GuestUserRequest {
    private String guestIdentifier;
    private String displayName;

    public String getGuestIdentifier() {
        return guestIdentifier;
    }

    public void setGuestIdentifier(String guestIdentifier) {
        this.guestIdentifier = guestIdentifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
