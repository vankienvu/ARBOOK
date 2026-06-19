package com.arbook.backend.admin.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.security.SecurityFacade;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;
    private final PasswordEncoder passwordEncoder;

    public AdminController(JdbcTemplate jdbc, SecurityFacade security, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.security = security;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select u.id, u.full_name, u.email, u.phone, u.status::text status, u.last_login_at, u.created_at,
                       coalesce(string_agg(r.code::text, ',' order by r.code::text), '') roles
                from users u
                left join user_roles ur on ur.user_id = u.id
                left join roles r on r.id = ur.role_id
                group by u.id
                order by u.created_at desc
                """)));
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<ApiResponse<Void>> updateRoles(@PathVariable Long id, @RequestBody RolesRequest request) {
        jdbc.update("delete from user_roles where user_id = ?", id);
        for (String role : request.roles()) {
            jdbc.update("""
                    insert into user_roles(user_id, role_id, assigned_at, assigned_by)
                    select ?, id, now(), ? from roles where code = ?::role_code
                    on conflict do nothing
                    """, id, security.currentUserIdOrNull(), role);
        }
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã cập nhật role.", null));
    }

    @PostMapping("/users/{id}/lock")
    public ResponseEntity<ApiResponse<Void>> lockUser(@PathVariable Long id) {
        jdbc.update("update users set status = 'LOCKED', updated_at = now() where id = ?", id);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã khóa tài khoản.", null));
    }

    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable Long id) {
        jdbc.update("update users set status = 'ACTIVE', updated_at = now() where id = ?", id);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã mở khóa tài khoản.", null));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(@Valid @RequestBody CreateUserRequest request) {
        Long id = jdbc.queryForObject("""
                insert into users(full_name, email, password_hash, phone, status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', now(), now())
                returning id
                """, Long.class, request.fullName(), request.email(), passwordEncoder.encode(request.password()), request.phone());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @PostMapping("/classes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createClass(@Valid @RequestBody ClassRequest request) {
        Long id = jdbc.queryForObject("""
                insert into classes(school_id, grade_id, name, school_year, status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', now(), now())
                returning id
                """, Long.class, request.schoolId(), request.gradeId(), request.name(), request.schoolYear());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @PostMapping("/classes/{classId}/students")
    public ResponseEntity<ApiResponse<Void>> addStudent(@PathVariable Long classId, @RequestBody UserLinkRequest request) {
        jdbc.update("""
                insert into class_students(class_id, student_id, joined_at, is_active)
                values (?, ?, now(), true)
                on conflict (class_id, student_id) do update set is_active = true, left_at = null
                """, classId, request.userId());
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã gán học sinh vào lớp.", null));
    }

    @PostMapping("/classes/{classId}/teachers")
    public ResponseEntity<ApiResponse<Void>> addTeacher(@PathVariable Long classId, @RequestBody UserLinkRequest request) {
        jdbc.update("""
                insert into teacher_classes(teacher_id, class_id, assigned_at, is_main_teacher)
                values (?, ?, now(), false)
                on conflict do nothing
                """, request.userId(), classId);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã gán giáo viên vào lớp.", null));
    }

    @PostMapping("/classes/{classId}/textbooks")
    public ResponseEntity<ApiResponse<Void>> addTextbook(@PathVariable Long classId, @RequestBody TextbookLinkRequest request) {
        jdbc.update("""
                insert into class_textbooks(class_id, textbook_id, assigned_by, assigned_at)
                values (?, ?, ?, now())
                on conflict do nothing
                """, classId, request.textbookId(), security.currentUserIdOrNull());
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã gán sách cho lớp.", null));
    }

    @PostMapping("/ar-contents/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approveContent(@PathVariable Long id) {
        jdbc.update("""
                update ar_contents
                set status = 'PUBLISHED', approved_by = ?, published_at = now(), updated_at = now()
                where id = ?
                """, security.currentUserIdOrNull(), id);
        jdbc.update("""
                update content_review_requests
                set status = 'APPROVED', reviewed_by = ?, reviewed_at = now()
                where ar_content_id = ? and status = 'PENDING'
                """, security.currentUserIdOrNull(), id);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã duyệt và publish nội dung AR.", null));
    }

    @PostMapping("/ar-contents/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectContent(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request) {
        jdbc.update("update ar_contents set status = 'REJECTED', updated_at = now() where id = ?", id);
        jdbc.update("""
                update content_review_requests
                set status = 'REJECTED', reviewed_by = ?, review_comment = ?, reviewed_at = now()
                where ar_content_id = ? and status = 'PENDING'
                """, security.currentUserIdOrNull(), request == null ? null : request.comment(), id);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã từ chối nội dung AR.", null));
    }

    @GetMapping("/feedbacks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> feedbacks() {
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select f.*, f.type::text type, f.status::text status, u.full_name user_name, l.title lesson_title
                from feedbacks f
                join users u on u.id = f.user_id
                left join lessons l on l.id = f.lesson_id
                order by f.created_at desc
                """)));
    }

    @PutMapping("/feedbacks/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateFeedbackStatus(@PathVariable Long id, @RequestBody FeedbackStatusRequest request) {
        jdbc.update("""
                update feedbacks
                set status = ?::feedback_status, handled_note = ?, handled_by = ?, handled_at = now()
                where id = ?
                """, request.status(), request.note(), security.currentUserIdOrNull(), id);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã cập nhật feedback.", null));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analytics() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("totalUsers", jdbc.queryForObject("select count(*) from users where status <> 'DELETED'", Long.class));
        body.put("totalScans", jdbc.queryForObject("select count(*) from scan_history", Long.class));
        body.put("successfulScans", jdbc.queryForObject("select count(*) from scan_history where result = 'SUCCESS'", Long.class));
        body.put("newFeedbacks", jdbc.queryForObject("select count(*) from feedbacks where status = 'NEW'", Long.class));
        body.put("publishedContents", jdbc.queryForObject("select count(*) from ar_contents where status = 'PUBLISHED'", Long.class));
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    public record RolesRequest(List<String> roles) {
    }

    public record CreateUserRequest(String fullName, String email, String password, String phone) {
    }

    public record ClassRequest(@NotNull Long schoolId, @NotNull Long gradeId, String name, String schoolYear) {
    }

    public record UserLinkRequest(@NotNull Long userId) {
    }

    public record TextbookLinkRequest(@NotNull Long textbookId) {
    }

    public record ReviewRequest(String comment) {
    }

    public record FeedbackStatusRequest(String status, String note) {
    }
}
