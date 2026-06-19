package com.arbook.backend.feedback.controller;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.security.SecurityFacade;

@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;

    public FeedbackController(JdbcTemplate jdbc, SecurityFacade security) {
        this.jdbc = jdbc;
        this.security = security;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody FeedbackRequest request) {
        Long id = jdbc.queryForObject("""
                insert into feedbacks(user_id, ar_content_id, marker_id, lesson_id, type, rating, message, screenshot_url, status, created_at)
                values (?, ?, ?, ?, ?::feedback_type, ?, ?, ?, 'NEW', now())
                returning id
                """, Long.class, security.currentUserIdOrNull(), request.arContentId(), request.markerId(), request.lessonId(),
                request.type(), request.rating(), request.message(), request.screenshotUrl());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    public record FeedbackRequest(
            Long arContentId,
            Long markerId,
            Long lessonId,
            @NotBlank String type,
            Integer rating,
            @NotBlank String message,
            String screenshotUrl
    ) {
    }
}
