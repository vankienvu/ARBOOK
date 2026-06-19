package com.arbook.backend.auth.dto;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        List<String> roles
) {
}
