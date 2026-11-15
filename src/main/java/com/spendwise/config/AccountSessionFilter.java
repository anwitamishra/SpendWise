package com.spendwise.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendwise.dto.ApiResponse;
import com.spendwise.model.User;
import com.spendwise.repository.UserRepository;
import com.spendwise.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Enforces bearer sessions for requests targeting registered accounts while keeping guest mode available. */
@Component
public class AccountSessionFilter extends OncePerRequestFilter {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AccountSessionFilter(AuthService authService, UserRepository userRepository, ObjectMapper objectMapper) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/") ||
                request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String guestId = request.getHeader("X-Guest-Id");
        String authorization = request.getHeader("Authorization");
        User sessionUser = null;
        if (authorization != null) {
            if (!authorization.startsWith("Bearer ")) {
                reject(response, "Sign-in session is invalid");
                return;
            }
            sessionUser = authService.resolveSession(authorization.substring(7).trim());
            if (sessionUser == null || guestId == null || !guestId.equals(sessionUser.getGuestIdentifier())) {
                reject(response, "Sign-in session is invalid");
                return;
            }
        } else if (guestId != null && userRepository.hasAccount(guestId)) {
            reject(response, "Please sign in to access this account");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(message));
    }
}