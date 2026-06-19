package com.arbook.backend.progress.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.security.AccessGuard;
import com.arbook.backend.security.SecurityFacade;

@RestController
@RequestMapping("/api")
public class ScanProgressController {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;
    private final AccessGuard accessGuard;

    public ScanProgressController(JdbcTemplate jdbc, SecurityFacade security, AccessGuard accessGuard) {
        this.jdbc = jdbc;
        this.security = security;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/scans")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordScan(@RequestBody ScanRequest request) {
        Long studentId = security.currentUserIdOrNull();
        Map<String, Object> match = jdbc.query("""
                select m.id marker_id, c.id ar_content_id, l.id lesson_id, c.status::text content_status
                from ar_markers m
                join textbook_pages p on p.id = m.textbook_page_id
                join lessons l on l.id = p.lesson_id
                left join ar_contents c on c.marker_id = m.id and c.lesson_id = l.id
                where m.marker_code = ?
                order by case when c.status = 'PUBLISHED' then 0 else 1 end, c.id
                limit 1
                """, rs -> {
                    if (!rs.next()) {
                        return Map.of();
                    }
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("markerId", rs.getLong("marker_id"));
                    long contentId = rs.getLong("ar_content_id");
                    row.put("arContentId", rs.wasNull() ? null : contentId);
                    row.put("lessonId", rs.getLong("lesson_id"));
                    row.put("contentStatus", rs.getString("content_status"));
                    return row;
                }, request.markerCode());
        String result = scanResult(match);
        Long id = jdbc.queryForObject("""
                insert into scan_history(student_id, marker_id, ar_content_id, lesson_id, result, detected_code, scanned_at)
                values (?, ?, ?, ?, ?::scan_result, ?, now())
                returning id
                """, Long.class, studentId, match.get("markerId"), match.get("arContentId"), match.get("lessonId"), result, request.markerCode());
        if ("SUCCESS".equals(result) && studentId != null) {
            jdbc.update("""
                    insert into learning_progress(student_id, lesson_id, status, progress_percent, total_scan_count, total_view_seconds, last_accessed_at)
                    values (?, ?, 'IN_PROGRESS', 25, 1, 0, now())
                    on conflict (student_id, lesson_id) do update
                    set status = case when learning_progress.status = 'COMPLETED' then 'COMPLETED' else 'IN_PROGRESS' end,
                        progress_percent = greatest(learning_progress.progress_percent, 25),
                        total_scan_count = learning_progress.total_scan_count + 1,
                        last_accessed_at = now()
                    """, studentId, match.get("lessonId"));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id, "result", result)));
    }

    @PostMapping("/scans/failed")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> failedScan(@RequestBody ScanFailedRequest request) {
        Long id = jdbc.queryForObject("""
                insert into scan_history(student_id, result, detected_code, error_message, scanned_at)
                values (?, coalesce(?::scan_result, 'FAILED'), ?, ?, now())
                returning id
                """, Long.class, security.currentUserIdOrNull(), request.result(), request.detectedCode(), request.errorMessage());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping("/scans/me")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myScans() {
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select sh.id, sh.detected_code, sh.result::text result, sh.error_message, sh.scanned_at,
                       l.title lesson_title, c.title ar_content_title
                from scan_history sh
                left join lessons l on l.id = sh.lesson_id
                left join ar_contents c on c.id = sh.ar_content_id
                where sh.student_id = ?
                order by sh.scanned_at desc
                """, security.currentUserIdOrNull())));
    }

    @PostMapping("/progress/events")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> progressEvent(@RequestBody ProgressEventRequest request) {
        if (request.progressPercent() == null || request.progressPercent().compareTo(java.math.BigDecimal.ZERO) < 0 || request.progressPercent().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new com.arbook.backend.common.exception.BusinessException("INVALID_PROGRESS", "Tiến độ hoàn thành phải nằm trong khoảng từ 0% đến 100%.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (request.viewSeconds() == null || request.viewSeconds() < 0 || request.viewSeconds() > 100000) {
            throw new com.arbook.backend.common.exception.BusinessException("INVALID_VIEW_SECONDS", "Thời gian xem không hợp lệ.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        Long studentId = security.currentUserIdOrNull();
        accessGuard.requireStudentCanAccessLesson(request.lessonId());
        jdbc.update("""
                insert into learning_progress(student_id, lesson_id, status, progress_percent, total_scan_count, total_view_seconds,
                                              last_accessed_at, completed_at)
                values (?, ?, ?::progress_status, ?, 0, coalesce(?, 0), now(), case when ? = 'COMPLETED' then now() else null end)
                on conflict (student_id, lesson_id) do update
                set status = excluded.status,
                    progress_percent = greatest(learning_progress.progress_percent, excluded.progress_percent),
                    total_view_seconds = learning_progress.total_view_seconds + excluded.total_view_seconds,
                    last_accessed_at = now(),
                    completed_at = case when excluded.status = 'COMPLETED' then now() else learning_progress.completed_at end
                """, studentId, request.lessonId(), request.status(), request.progressPercent(), request.viewSeconds(), request.status());
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã ghi tiến độ.", null));
    }

    @GetMapping("/progress/me")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myProgress() {
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select lp.*, lp.status::text status, l.title lesson_title
                from learning_progress lp
                join lessons l on l.id = lp.lesson_id
                where lp.student_id = ?
                order by lp.last_accessed_at desc nulls last
                """, security.currentUserIdOrNull())));
    }

    @GetMapping("/progress/classes/{classId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> classProgress(@PathVariable Long classId) {
        accessGuard.requireTeacherCanAccessClass(classId);
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select u.id student_id, u.full_name student_name, l.id lesson_id, l.title lesson_title,
                       coalesce(lp.status::text, 'NOT_STARTED') status, coalesce(lp.progress_percent, 0) progress_percent,
                       coalesce(lp.total_scan_count, 0) total_scan_count, lp.last_accessed_at
                from class_students cs
                join users u on u.id = cs.student_id
                cross join lessons l
                left join learning_progress lp on lp.student_id = u.id and lp.lesson_id = l.id
                where cs.class_id = ? and cs.is_active = true
                order by u.full_name, l.order_no
                """, classId)));
    }

    private String scanResult(Map<String, Object> match) {
        if (match.isEmpty()) {
            return "UNKNOWN_MARKER";
        }
        if (match.get("arContentId") == null || !"PUBLISHED".equals(match.get("contentStatus"))) {
            return "CONTENT_NOT_PUBLISHED";
        }
        Long lessonId = (Long) match.get("lessonId");
        if (!accessGuard.currentStudentCanAccessLesson(lessonId)) {
            return "ACCESS_DENIED";
        }
        return "SUCCESS";
    }

    public record ScanRequest(String markerCode) {
    }

    public record ScanFailedRequest(String detectedCode, String result, String errorMessage) {
    }

    public record ProgressEventRequest(Long lessonId, String status, java.math.BigDecimal progressPercent, Integer viewSeconds) {
    }
}
