package com.arbook.backend.ar.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.arbook.backend.ar.service.ArMarkerResolveService;
import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.common.exception.BusinessException;
import com.arbook.backend.security.AccessGuard;
import com.arbook.backend.security.SecurityFacade;

@RestController
@RequestMapping("/api")
public class ArController {
    private final JdbcTemplate jdbc;
    private final ArMarkerResolveService resolveService;
    private final SecurityFacade security;
    private final AccessGuard accessGuard;

    @Value("${app.unity.client-key:UnitySecretKey123}")
    private String unityClientKey;

    public ArController(JdbcTemplate jdbc, ArMarkerResolveService resolveService, SecurityFacade security, AccessGuard accessGuard) {
        this.jdbc = jdbc;
        this.resolveService = resolveService;
        this.security = security;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/ar-markers/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @RequestParam("code") String code,
            HttpServletRequest request) {
        String apiKey = request.getHeader("X-Unity-API-Key");
        boolean isClientAuthorized = unityClientKey.equals(apiKey);
        boolean isUserAuthenticated = security.currentUserIdOrNull() != null;

        if (!isClientAuthorized && !isUserAuthenticated) {
            throw new BusinessException("UNAUTHORIZED_CLIENT", "Yêu cầu không hợp lệ. Vui lòng cung cấp mã ứng dụng hợp lệ.", HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(resolveService.resolve(code));
    }

    @PostMapping("/ar-markers")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMarker(@Valid @RequestBody MarkerRequest request) {
        Long id = jdbc.queryForObject("""
                insert into ar_markers(textbook_page_id, marker_code, marker_type, marker_name, image_url, target_file_url,
                                       qr_payload, quality_score, width_cm, height_cm, status, created_by, created_at, updated_at)
                values (?, ?, ?::marker_type, ?, ?, ?, ?, ?, ?, ?, coalesce(?::marker_status, 'ACTIVE'), ?, now(), now())
                returning id
                """, Long.class,
                request.textbookPageId(), request.markerCode(), request.markerType(), request.markerName(),
                request.imageUrl(), request.targetFileUrl(), request.qrPayload(), request.qualityScore(),
                request.widthCm(), request.heightCm(), request.status(), security.currentUserIdOrNull());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping("/ar-contents/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getContent(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select c.*, l.title lesson_title, m.marker_code, model.name model_name, model.file_url model_url
                from ar_contents c
                join lessons l on l.id = c.lesson_id
                join ar_markers m on m.id = c.marker_id
                left join three_d_models model on model.id = c.default_model_id
                where c.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException("AR_CONTENT_NOT_FOUND", "Không tìm thấy nội dung AR.", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> content = rows.getFirst();
        if (security.hasRole("STUDENT")) {
            if (!"PUBLISHED".equals(String.valueOf(content.get("status")))) {
                throw new BusinessException("CONTENT_NOT_PUBLISHED", "Nội dung AR chưa được xuất bản.", HttpStatus.CONFLICT);
            }
            accessGuard.requireStudentCanAccessLesson((Long) content.get("lesson_id"));
        }
        return ResponseEntity.ok(ApiResponse.ok(content));
    }

    @PostMapping("/ar-contents")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createContent(@Valid @RequestBody ArContentRequest request) {
        Long id = jdbc.queryForObject("""
                insert into ar_contents(lesson_id, marker_id, default_model_id, title, description, content_type,
                                        instruction_text, learning_goal, position_x, position_y, position_z,
                                        rotation_x, rotation_y, rotation_z, scale_x, scale_y, scale_z,
                                        status, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, coalesce(?::ar_content_type, 'MODEL_3D'), ?, ?,
                        0, 0, 0, 0, 0, 0, 1, 1, 1, 'DRAFT', ?, now(), now())
                returning id
                """, Long.class,
                request.lessonId(), request.markerId(), request.defaultModelId(), request.title(), request.description(),
                request.contentType(), request.instructionText(), request.learningGoal(), security.currentUserIdOrNull());
        if (request.defaultModelId() != null) {
            jdbc.update("""
                    insert into ar_content_models(ar_content_id, model_id, display_order, is_default)
                    values (?, ?, 1, true)
                    on conflict (ar_content_id, model_id) do update set is_default = true
                    """, id, request.defaultModelId());
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @PutMapping("/ar-contents/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateContent(@PathVariable Long id, @Valid @RequestBody ArContentRequest request) {
        jdbc.update("""
                update ar_contents
                set lesson_id = ?, marker_id = ?, default_model_id = ?, title = ?, description = ?,
                    instruction_text = ?, learning_goal = ?, updated_at = now()
                where id = ?
                """, request.lessonId(), request.markerId(), request.defaultModelId(), request.title(), request.description(),
                request.instructionText(), request.learningGoal(), id);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã cập nhật nội dung AR.", null));
    }

    @PostMapping("/ar-contents/{id}/submit-review")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> submitReview(@PathVariable Long id) {
        jdbc.update("update ar_contents set status = 'PENDING_REVIEW', updated_at = now() where id = ?", id);
        jdbc.update("""
                insert into content_review_requests(ar_content_id, requested_by, status, requested_at)
                values (?, ?, 'PENDING', now())
                """, id, security.currentUserIdOrNull());
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã gửi nội dung chờ duyệt.", null));
    }

    public record MarkerRequest(
            @NotNull Long textbookPageId,
            @NotBlank String markerCode,
            @NotBlank String markerType,
            @NotBlank String markerName,
            String imageUrl,
            String targetFileUrl,
            String qrPayload,
            java.math.BigDecimal qualityScore,
            java.math.BigDecimal widthCm,
            java.math.BigDecimal heightCm,
            String status
    ) {
    }

    public record ArContentRequest(
            @NotNull Long lessonId,
            @NotNull Long markerId,
            Long defaultModelId,
            @NotBlank String title,
            String description,
            String contentType,
            String instructionText,
            String learningGoal
    ) {
    }
}
