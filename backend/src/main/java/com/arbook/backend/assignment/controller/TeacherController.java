package com.arbook.backend.assignment.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
@RequestMapping("/api/teacher")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TeacherController {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;
    private final AccessGuard accessGuard;

    public TeacherController(JdbcTemplate jdbc, SecurityFacade security, AccessGuard accessGuard) {
        this.jdbc = jdbc;
        this.security = security;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> classes() {
        if (security.hasRole("ADMIN")) {
            return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                    select c.id, c.name, c.school_year, g.name grade_name, s.name school_name
                    from classes c
                    join grades g on g.id = c.grade_id
                    left join schools s on s.id = c.school_id
                    order by c.name
                    """)));
        }
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select c.id, c.name, c.school_year, g.name grade_name, s.name school_name
                from teacher_classes tc
                join classes c on c.id = tc.class_id
                join grades g on g.id = c.grade_id
                left join schools s on s.id = c.school_id
                where tc.teacher_id = ?
                order by c.name
                """, security.currentUserIdOrNull())));
    }

    @GetMapping("/classes/{classId}/students")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> students(@PathVariable Long classId) {
        accessGuard.requireTeacherCanAccessClass(classId);
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select u.id, u.full_name, u.email, cs.joined_at
                from class_students cs
                join users u on u.id = cs.student_id
                where cs.class_id = ? and cs.is_active = true
                order by u.full_name
                """, classId)));
    }

    @PostMapping("/assignments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAssignment(@Valid @RequestBody AssignmentRequest request) {
        accessGuard.requireTeacherCanAccessClass(request.classId());
        Long assignmentId = jdbc.queryForObject("""
                insert into teacher_assignments(teacher_id, class_id, lesson_id, title, note, due_date, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now())
                returning id
                """, Long.class, security.currentUserIdOrNull(), request.classId(), request.lessonId(), request.title(), request.note(), request.dueDate());
        jdbc.update("""
                insert into assignment_students(assignment_id, student_id, status)
                select ?, student_id, 'NOT_STARTED' from class_students where class_id = ? and is_active = true
                on conflict do nothing
                """, assignmentId, request.classId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", assignmentId)));
    }

    @GetMapping("/assignments/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignment(@PathVariable Long id) {
        accessGuard.requireTeacherCanAccessAssignment(id);
        Map<String, Object> assignment = new java.util.LinkedHashMap<>(jdbc.queryForMap("""
                select ta.*, ta.status::text status, c.name class_name, l.title lesson_title
                from teacher_assignments ta
                join classes c on c.id = ta.class_id
                join lessons l on l.id = ta.lesson_id
                where ta.id = ?
                """, id));
        assignment.put("students", jdbc.queryForList("""
                select ast.student_id, u.full_name student_name, ast.status::text status, ast.submitted_at, ast.score, ast.teacher_feedback
                from assignment_students ast
                join users u on u.id = ast.student_id
                where ast.assignment_id = ?
                order by u.full_name
                """, id));
        return ResponseEntity.ok(ApiResponse.ok(assignment));
    }

    @GetMapping("/classes/{classId}/progress")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> classProgress(@PathVariable Long classId) {
        accessGuard.requireTeacherCanAccessClass(classId);
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select u.id student_id, u.full_name student_name,
                       count(lp.id) filter (where lp.status = 'COMPLETED') completed_lessons,
                       count(lp.id) filter (where lp.status = 'IN_PROGRESS') in_progress_lessons,
                       coalesce(avg(lp.progress_percent), 0) avg_progress,
                       coalesce(sum(lp.total_scan_count), 0) total_scans
                from class_students cs
                join users u on u.id = cs.student_id
                left join learning_progress lp on lp.student_id = u.id
                where cs.class_id = ? and cs.is_active = true
                group by u.id
                order by u.full_name
                """, classId)));
    }

    @PostMapping("/comments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> comment(@Valid @RequestBody CommentRequest request) {
        accessGuard.requireTeacherCanAccessStudent(request.studentId());
        Long id = jdbc.queryForObject("""
                insert into teacher_comments(teacher_id, student_id, lesson_id, assignment_id, comment_text, created_at)
                values (?, ?, ?, ?, ?, now())
                returning id
                """, Long.class, security.currentUserIdOrNull(), request.studentId(), request.lessonId(), request.assignmentId(), request.commentText());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping("/classes/{classId}/export-csv")
    public void exportClassCsv(@PathVariable Long classId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        accessGuard.requireTeacherCanAccessClass(classId);

        // Check if teacher is Premium
        Boolean isPremium = jdbc.queryForObject("select is_premium from users where id = ?", Boolean.class, security.currentUserIdOrNull());
        if (isPremium == null || !isPremium) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"PREMIUM_REQUIRED\",\"message\":\"Tính năng này chỉ dành cho tài khoản Giáo viên Premium. Vui lòng nâng cấp tài khoản.\"}");
            return;
        }

        // Query students & progress details
        List<Map<String, Object>> students = jdbc.queryForList("""
                select u.id student_id, u.full_name student_name, u.email,
                       count(lp.id) filter (where lp.status = 'COMPLETED') completed_lessons,
                       count(lp.id) filter (where lp.status = 'IN_PROGRESS') in_progress_lessons,
                       coalesce(avg(lp.progress_percent), 0) avg_progress,
                       coalesce(sum(lp.total_scan_count), 0) total_scans
                from class_students cs
                join users u on u.id = cs.student_id
                left join learning_progress lp on lp.student_id = u.id
                where cs.class_id = ? and cs.is_active = true
                group by u.id, u.full_name, u.email
                order by u.full_name
                """, classId);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"class_" + classId + "_progress.csv\"");

        // Write UTF-8 BOM to display Vietnamese characters correctly in Excel
        response.getWriter().write('\ufeff');

        // Write Header
        response.getWriter().write("Mã Học Sinh,Họ Và Tên,Email,Bài Học Hoàn Thành,Bài Học Đang Học,Tiến Độ Trung Bình (%),Số Lượt Quét AR\n");

        // Write Data
        for (Map<String, Object> row : students) {
            response.getWriter().write(String.format("%s,%s,%s,%s,%s,%s%%,%s\n",
                    row.get("student_id"),
                    escapeCsv(String.valueOf(row.get("student_name"))),
                    escapeCsv(String.valueOf(row.get("email") != null ? row.get("email") : "N/A")),
                    row.get("completed_lessons"),
                    row.get("in_progress_lessons"),
                    Math.round(Double.parseDouble(String.valueOf(row.get("avg_progress")))),
                    row.get("total_scans")
            ));
        }
    }

    private String escapeCsv(String val) {
        if (val == null || "null".equals(val)) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    public record AssignmentRequest(
            @NotNull Long classId,
            @NotNull Long lessonId,
            @NotBlank String title,
            String note,
            java.time.OffsetDateTime dueDate
    ) {
    }

    public record CommentRequest(@NotNull Long studentId, Long lessonId, Long assignmentId, @NotBlank String commentText) {
    }
}
