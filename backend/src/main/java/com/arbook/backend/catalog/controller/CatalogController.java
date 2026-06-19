package com.arbook.backend.catalog.controller;

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
import org.springframework.web.bind.annotation.RequestParam;

import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.security.AccessGuard;
import com.arbook.backend.security.SecurityFacade;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;
    private final AccessGuard accessGuard;

    public CatalogController(JdbcTemplate jdbc, SecurityFacade security, AccessGuard accessGuard) {
        this.jdbc = jdbc;
        this.security = security;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/subjects")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> subjects() {
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select id, code, name, description, status::text status, created_at, updated_at
                from subjects
                where status = 'ACTIVE'
                order by name
                """)));
    }

    @PostMapping("/subjects")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSubject(@Valid @RequestBody SubjectRequest request) {
        Long id = jdbc.queryForObject("""
                insert into subjects(code, name, description, status, created_by, created_at, updated_at)
                values (?, ?, ?, coalesce(?::active_status, 'ACTIVE'), ?, now(), now())
                on conflict (code) do update set name = excluded.name, description = excluded.description, updated_at = now()
                returning id
                """, Long.class, request.code(), request.name(), request.description(), request.status(), security.currentUserIdOrNull());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping("/grades")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> grades() {
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("select id, name, level_number, description from grades order by level_number")));
    }

    @GetMapping("/textbooks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> textbooks() {
        Long userId = security.currentUserIdOrNull();
        if (security.hasRole("STUDENT")) {
            return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                    select distinct t.id, t.title, t.publisher, t.book_series, t.school_year, t.cover_image_url,
                           t.description, t.status::text status, s.name subject_name, g.name grade_name
                    from textbooks t
                    join subjects s on s.id = t.subject_id
                    join grades g on g.id = t.grade_id
                    join class_textbooks ct on ct.textbook_id = t.id
                    join class_students cs on cs.class_id = ct.class_id
                    where cs.student_id = ? and cs.is_active = true and t.status = 'PUBLISHED'
                    order by t.title
                    """, userId)));
        }
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select t.id, t.title, t.publisher, t.book_series, t.school_year, t.cover_image_url,
                       t.description, t.status::text status, s.name subject_name, g.name grade_name
                from textbooks t
                join subjects s on s.id = t.subject_id
                join grades g on g.id = t.grade_id
                order by t.created_at desc
                """)));
    }

    @PostMapping("/textbooks")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createTextbook(@Valid @RequestBody TextbookRequest request) {
        Long id = jdbc.queryForObject("""
                insert into textbooks(subject_id, grade_id, title, publisher, book_series, school_year, cover_image_url,
                                      description, status, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, coalesce(?::publish_status, 'DRAFT'), ?, now(), now())
                on conflict (subject_id, grade_id, title) do update
                set publisher = excluded.publisher, book_series = excluded.book_series, description = excluded.description, updated_at = now()
                returning id
                """, Long.class, request.subjectId(), request.gradeId(), request.title(), request.publisher(), request.bookSeries(),
                request.schoolYear(), request.coverImageUrl(), request.description(), request.status(), security.currentUserIdOrNull());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping("/textbooks/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> textbook(@PathVariable Long id) {
        accessGuard.requireStudentCanAccessTextbook(id);
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForMap("""
                select t.*, t.status::text status, s.name subject_name, g.name grade_name
                from textbooks t
                join subjects s on s.id = t.subject_id
                join grades g on g.id = t.grade_id
                where t.id = ?
                """, id)));
    }

    @GetMapping("/textbooks/{id}/chapters")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> chapters(@PathVariable Long id) {
        accessGuard.requireStudentCanAccessTextbook(id);
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select id, textbook_id, title, order_no, description, status::text status
                from chapters
                where textbook_id = ?
                order by order_no
                """, id)));
    }

    @PostMapping("/chapters")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createChapter(@Valid @RequestBody ChapterRequest request) {
        Long id = jdbc.queryForObject("""
                insert into chapters(textbook_id, title, order_no, description, status, created_at, updated_at)
                values (?, ?, ?, ?, coalesce(?::publish_status, 'DRAFT'), now(), now())
                on conflict (textbook_id, order_no) do update
                set title = excluded.title, description = excluded.description, updated_at = now()
                returning id
                """, Long.class, request.textbookId(), request.title(), request.orderNo(), request.description(), request.status());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping("/lessons/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> lesson(@PathVariable Long id) {
        accessGuard.requireStudentCanAccessLesson(id);
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForMap("""
                select l.*, l.status::text status, c.title chapter_title, t.title textbook_title
                from lessons l
                join chapters c on c.id = l.chapter_id
                join textbooks t on t.id = c.textbook_id
                where l.id = ?
                """, id)));
    }

    @GetMapping("/lessons")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listLessons(
            @RequestParam(value = "textbookId", required = false) Long textbookId,
            @RequestParam(value = "chapterId", required = false) Long chapterId) {
        if (security.hasRole("STUDENT")) {
            if (textbookId != null) {
                accessGuard.requireStudentCanAccessTextbook(textbookId);
            }
            if (chapterId != null) {
                Long tbId = jdbc.queryForObject("select textbook_id from chapters where id = ?", Long.class, chapterId);
                if (tbId != null) {
                    accessGuard.requireStudentCanAccessTextbook(tbId);
                }
            }
        }
        List<Map<String, Object>> lessons;
        if (chapterId != null) {
            lessons = jdbc.queryForList("""
                    select l.id, l.chapter_id, l.title, l.order_no, l.objectives, l.summary, l.estimated_minutes, l.status::text status
                    from lessons l
                    where l.chapter_id = ?
                    order by l.order_no
                    """, chapterId);
        } else if (textbookId != null) {
            lessons = jdbc.queryForList("""
                    select l.id, l.chapter_id, l.title, l.order_no, l.objectives, l.summary, l.estimated_minutes, l.status::text status
                    from lessons l
                    join chapters c on c.id = l.chapter_id
                    where c.textbook_id = ?
                    order by c.order_no, l.order_no
                    """, textbookId);
        } else {
            lessons = jdbc.queryForList("""
                    select l.id, l.chapter_id, l.title, l.order_no, l.objectives, l.summary, l.estimated_minutes, l.status::text status
                    from lessons l
                    order by l.title
                    """);
        }
        return ResponseEntity.ok(ApiResponse.ok(lessons));
    }

    @PostMapping("/lessons")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createLesson(@Valid @RequestBody LessonRequest request) {
        Long id = jdbc.queryForObject("""
                insert into lessons(chapter_id, title, order_no, objectives, summary, estimated_minutes, status, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, coalesce(?::publish_status, 'DRAFT'), ?, now(), now())
                on conflict (chapter_id, order_no) do update
                set title = excluded.title, objectives = excluded.objectives, summary = excluded.summary, updated_at = now()
                returning id
                """, Long.class, request.chapterId(), request.title(), request.orderNo(), request.objectives(), request.summary(),
                request.estimatedMinutes(), request.status(), security.currentUserIdOrNull());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    public record SubjectRequest(@NotBlank String code, @NotBlank String name, String description, String status) {
    }

    public record TextbookRequest(
            @NotNull Long subjectId,
            @NotNull Long gradeId,
            @NotBlank String title,
            String publisher,
            String bookSeries,
            String schoolYear,
            String coverImageUrl,
            String description,
            String status
    ) {
    }

    public record ChapterRequest(@NotNull Long textbookId, @NotBlank String title, @NotNull Integer orderNo, String description, String status) {
    }

    public record LessonRequest(
            @NotNull Long chapterId,
            @NotBlank String title,
            @NotNull Integer orderNo,
            String objectives,
            String summary,
            Integer estimatedMinutes,
            String status
    ) {
    }
}
