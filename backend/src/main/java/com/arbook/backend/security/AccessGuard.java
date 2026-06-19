package com.arbook.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.arbook.backend.common.exception.BusinessException;

@Component
public class AccessGuard {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;

    public AccessGuard(JdbcTemplate jdbc, SecurityFacade security) {
        this.jdbc = jdbc;
        this.security = security;
    }

    public void requireStudentCanAccessTextbook(Long textbookId) {
        if (!security.hasRole("STUDENT") || security.hasRole("ADMIN")) {
            return;
        }
        Long userId = requireAuthenticatedUserId();
        Integer count = jdbc.queryForObject("""
                select count(*)
                from class_students cs
                join class_textbooks ct on ct.class_id = cs.class_id
                join textbooks t on t.id = ct.textbook_id
                where cs.student_id = ?
                  and cs.is_active = true
                  and ct.textbook_id = ?
                  and t.status = 'PUBLISHED'
                """, Integer.class, userId, textbookId);
        if (count == null || count == 0) {
            throw forbidden("TEXTBOOK_ACCESS_DENIED", "Bạn chưa được cấp quyền học sách giáo khoa này.");
        }
    }

    public void requireStudentCanAccessLesson(Long lessonId) {
        if (!security.hasRole("STUDENT") || security.hasRole("ADMIN")) {
            return;
        }
        if (!currentStudentCanAccessLesson(lessonId)) {
            throw forbidden("LESSON_ACCESS_DENIED", "Bạn chưa được cấp quyền học bài học này.");
        }
    }

    public boolean currentStudentCanAccessLesson(Long lessonId) {
        if (!security.hasRole("STUDENT") || security.hasRole("ADMIN")) {
            return true;
        }
        Long userId = requireAuthenticatedUserId();
        Integer count = jdbc.queryForObject("""
                select count(*)
                from class_students cs
                join class_textbooks ct on ct.class_id = cs.class_id
                join textbooks t on t.id = ct.textbook_id
                join chapters ch on ch.textbook_id = t.id
                join lessons l on l.chapter_id = ch.id
                where cs.student_id = ?
                  and cs.is_active = true
                  and l.id = ?
                  and t.status = 'PUBLISHED'
                  and ch.status = 'PUBLISHED'
                  and l.status = 'PUBLISHED'
                """, Integer.class, userId, lessonId);
        return count != null && count > 0;
    }

    public void requireTeacherCanAccessClass(Long classId) {
        if (security.hasRole("ADMIN")) {
            return;
        }
        Long userId = requireAuthenticatedUserId();
        Integer count = jdbc.queryForObject("""
                select count(*)
                from teacher_classes
                where teacher_id = ? and class_id = ?
                """, Integer.class, userId, classId);
        if (count == null || count == 0) {
            throw forbidden("CLASS_ACCESS_DENIED", "Giáo viên chỉ được truy cập lớp được phân công.");
        }
    }

    public void requireTeacherCanAccessAssignment(Long assignmentId) {
        if (security.hasRole("ADMIN")) {
            return;
        }
        Long classId = jdbc.query("""
                select class_id
                from teacher_assignments
                where id = ?
                """, rs -> rs.next() ? rs.getLong("class_id") : null, assignmentId);
        if (classId == null) {
            throw notFound("ASSIGNMENT_NOT_FOUND", "Không tìm thấy bài giao.");
        }
        requireTeacherCanAccessClass(classId);
    }

    public void requireTeacherCanAccessStudent(Long studentId) {
        if (security.hasRole("ADMIN")) {
            return;
        }
        Long teacherId = requireAuthenticatedUserId();
        Integer count = jdbc.queryForObject("""
                select count(*)
                from teacher_classes tc
                join class_students cs on cs.class_id = tc.class_id
                where tc.teacher_id = ?
                  and cs.student_id = ?
                  and cs.is_active = true
                """, Integer.class, teacherId, studentId);
        if (count == null || count == 0) {
            throw forbidden("STUDENT_ACCESS_DENIED", "Giáo viên chỉ được nhận xét học sinh thuộc lớp được phân công.");
        }
    }

    private Long requireAuthenticatedUserId() {
        Long userId = security.currentUserIdOrNull();
        if (userId == null) {
            throw forbidden("AUTHENTICATION_REQUIRED", "Bạn cần đăng nhập để thực hiện thao tác này.");
        }
        return userId;
    }

    private BusinessException forbidden(String code, String message) {
        return new BusinessException(code, message, HttpStatus.FORBIDDEN);
    }

    private BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }
}
