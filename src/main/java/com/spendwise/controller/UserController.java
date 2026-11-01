package com.spendwise.controller;

import com.spendwise.dto.ApiResponse;
import com.spendwise.dto.GuestUserRequest;
import com.spendwise.model.User;
import com.spendwise.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<User>> getOrCreateGuest(@RequestBody(required = false) GuestUserRequest request) {
        if (request == null) {
            request = new GuestUserRequest();
        }
        User user = userService.getOrCreateGuestUser(request);
        return ResponseEntity.ok(ApiResponse.ok("User session initialized", user));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(@RequestHeader("X-Guest-Id") String guestIdentifier) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<User>> updateProfile(
            @RequestHeader("X-Guest-Id") String guestIdentifier,
            @RequestBody Map<String, String> body) {
        String displayName = body.get("displayName");
        User user = userService.updateDisplayName(guestIdentifier, displayName);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", user));
    }
}
