package com.spendwise.service;

import com.spendwise.dto.GuestUserRequest;
import com.spendwise.exception.BadRequestException;
import com.spendwise.exception.ResourceNotFoundException;
import com.spendwise.model.User;
import com.spendwise.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User getOrCreateGuestUser(GuestUserRequest request) {
        String guestId = request.getGuestIdentifier();
        if (guestId == null || guestId.trim().isEmpty()) {
            guestId = UUID.randomUUID().toString();
        }

        String displayName = request.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "Guest " + guestId.substring(0, Math.min(6, guestId.length()));
        }

        final String finalGuestId = guestId.trim();
        final String finalDisplayName = displayName.trim();

        return userRepository.findByGuestIdentifier(finalGuestId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setGuestIdentifier(finalGuestId);
                    newUser.setDisplayName(finalDisplayName);
                    return userRepository.save(newUser);
                });
    }

    public User getUserByGuestIdentifier(String guestIdentifier) {
        if (guestIdentifier == null || guestIdentifier.trim().isEmpty()) {
            throw new BadRequestException("Guest identifier is required");
        }
        return userRepository.findByGuestIdentifier(guestIdentifier.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for identifier: " + guestIdentifier));
    }

    @Transactional
    public User updateDisplayName(String guestIdentifier, String newDisplayName) {
        if (newDisplayName == null || newDisplayName.trim().isEmpty()) {
            throw new BadRequestException("Display name cannot be empty");
        }
        User user = getUserByGuestIdentifier(guestIdentifier);
        userRepository.updateDisplayName(user.getId(), newDisplayName.trim());
        user.setDisplayName(newDisplayName.trim());
        return user;
    }
}
