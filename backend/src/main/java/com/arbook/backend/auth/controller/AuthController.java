package com.arbook.backend.auth.controller;

import java.time.OffsetDateTime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arbook.backend.auth.dto.CurrentUserResponse;
import com.arbook.backend.auth.dto.LoginRequest;
import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.security.AppUserDetails;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JdbcTemplate jdbcTemplate;

    public AuthController(AuthenticationManager authenticationManager, JdbcTemplate jdbcTemplate) {
        this.authenticationManager = authenticationManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        AppUserDetails user = (AppUserDetails) authentication.getPrincipal();
        jdbcTemplate.update("update users set last_login_at = ?, updated_at = now() where id = ?",
                OffsetDateTime.now(), user.id());

        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công.", toResponse(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest servletRequest) {
        SecurityContextHolder.clearContext();
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đăng xuất thành công.", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    private CurrentUserResponse toResponse(AppUserDetails user) {
        return new CurrentUserResponse(
                user.id(),
                user.fullName(),
                user.email(),
                user.authorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).toList()
        );
    }
}
