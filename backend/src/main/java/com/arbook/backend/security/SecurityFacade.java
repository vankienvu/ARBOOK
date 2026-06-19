package com.arbook.backend.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityFacade {
    public Optional<AppUserDetails> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public Long currentUserIdOrNull() {
        return currentUser().map(AppUserDetails::id).orElse(null);
    }

    public boolean hasRole(String role) {
        return currentUser()
                .map(user -> user.authorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role)))
                .orElse(false);
    }
}
