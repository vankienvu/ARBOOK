package com.arbook.backend.config;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedRoles();

        long adminId = upsertUser("Admin Demo", "admin@artextbook.com", "ADMIN");
        long contentId = upsertUser("Content Manager Demo", "content@artextbook.com", "CONTENT_MANAGER");
        long teacherId = upsertUser("Teacher Demo", "teacher@artextbook.com", "TEACHER");
        long studentId = upsertUser("Student Demo", "student@artextbook.com", "STUDENT");

        long schoolId = upsertSchool();
        long gradeId = upsertGrade();
        long classId = upsertClass(schoolId, gradeId);
        linkClassStudent(classId, studentId);
        linkTeacherClass(teacherId, classId);

        long subjectId = upsertSubject(contentId);
        long textbookId = upsertTextbook(subjectId, gradeId, contentId);
        linkClassTextbook(classId, textbookId, adminId);

        long chapterId = upsertChapter(textbookId);
        long cellLessonId = upsertLesson(chapterId, 1, "Cấu tạo tế bào", "Quan sát các thành phần chính của tế bào.", contentId);
        long solarLessonId = upsertLesson(chapterId, 2, "Hệ Mặt Trời", "Quan sát chuyển động mô phỏng của các hành tinh.", contentId);
        long heartLessonId = upsertLesson(chapterId, 3, "Cấu tạo trái tim", "Quan sát các buồng tim và nhịp đập mô phỏng.", contentId);

        long cellPageId = upsertPage(textbookId, cellLessonId, 12, "Trang 12 - Cấu tạo tế bào");
        long solarPageId = upsertPage(textbookId, solarLessonId, 13, "Trang 13 - Hệ Mặt Trời");
        long heartPageId = upsertPage(textbookId, heartLessonId, 14, "Trang 14 - Cấu tạo trái tim");

        long cellMarkerId = upsertMarker(cellPageId, "BIO_CELL_001", "Marker cấu tạo tế bào", contentId);
        long solarMarkerId = upsertMarker(solarPageId, "SOLAR_SYSTEM_001", "Marker hệ Mặt Trời", contentId);
        long heartMarkerId = upsertMarker(heartPageId, "HEART_001", "Marker trái tim", contentId);

        long cellModelId = upsertModel("Cell 3D Model", "Mô hình placeholder cấu tạo tế bào.", "cell-placeholder.glb", contentId);
        long solarModelId = upsertModel("Solar System 3D Model", "Mô hình placeholder hệ Mặt Trời.", "solar-system-placeholder.glb", contentId);
        long heartModelId = upsertModel("Heart 3D Model", "Mô hình placeholder trái tim.", "heart-placeholder.glb", contentId);

        long cellContentId = upsertArContent(cellLessonId, cellMarkerId, cellModelId, "Mô hình tế bào 3D",
                "Mô hình mô phỏng các thành phần chính của tế bào.", contentId, adminId);
        long solarContentId = upsertArContent(solarLessonId, solarMarkerId, solarModelId, "Mô hình hệ Mặt Trời 3D",
                "Mô hình mô phỏng quỹ đạo cơ bản của các hành tinh.", contentId, adminId);
        long heartContentId = upsertArContent(heartLessonId, heartMarkerId, heartModelId, "Mô hình trái tim 3D",
                "Mô hình mô phỏng cấu tạo và nhịp đập của trái tim.", contentId, adminId);

        linkContentModel(cellContentId, cellModelId, "Default Cell");
        linkContentModel(solarContentId, solarModelId, "Default Solar System");
        linkContentModel(heartContentId, heartModelId, "Default Heart");

        seedLabels(cellModelId, solarModelId, heartModelId);
        seedAnimations(cellContentId, solarContentId, heartContentId);
        seedQuiz(cellLessonId, contentId);
        seedAssignment(teacherId, classId, cellLessonId, studentId);
        seedSettings(adminId);
    }

    private void seedRoles() {
        upsertRole("STUDENT", "Student", "Học sinh");
        upsertRole("TEACHER", "Teacher", "Giáo viên");
        upsertRole("CONTENT_MANAGER", "Content Manager", "Quản lý nội dung");
        upsertRole("ADMIN", "Admin", "Quản trị viên");
    }

    private void upsertRole(String code, String name, String description) {
        jdbc.update("""
                insert into roles(code, name, description)
                values (?::role_code, ?, ?)
                on conflict (code) do update set name = excluded.name, description = excluded.description
                """, code, name, description);
    }

    private long upsertUser(String fullName, String email, String roleCode) {
        Long userId = jdbc.queryForObject("""
                insert into users(full_name, email, password_hash, status, created_at, updated_at)
                values (?, ?, ?, 'ACTIVE', now(), now())
                on conflict (email) do update
                set full_name = excluded.full_name,
                    password_hash = excluded.password_hash,
                    updated_at = now()
                returning id
                """, Long.class, fullName, email, passwordEncoder.encode("123456"));
        Long roleId = jdbc.queryForObject("select id from roles where code = ?::role_code", Long.class, roleCode);
        jdbc.update("""
                insert into user_roles(user_id, role_id, assigned_at)
                values (?, ?, now())
                on conflict do nothing
                """, userId, roleId);
        return userId;
    }

    private long upsertSchool() {
        return upsertByCode("""
                insert into schools(name, code, address, phone, email, status, created_at, updated_at)
                values ('PTIT Demo School', 'PTIT_DEMO', 'Hà Nội', '0240000000', 'demo@ptit.edu.vn', 'ACTIVE', now(), now())
                on conflict (code) do update set name = excluded.name, updated_at = now()
                returning id
                """);
    }

    private long upsertGrade() {
        Long existing = queryLong("select id from grades where level_number = 10");
        if (existing != null) {
            return existing;
        }
        return jdbc.queryForObject("""
                insert into grades(name, level_number, description)
                values ('Lớp 10', 10, 'Khối lớp 10')
                returning id
                """, Long.class);
    }

    private long upsertClass(long schoolId, long gradeId) {
        Long existing = queryLong("select id from classes where school_id = ? and grade_id = ? and name = '10A1' and school_year = '2025-2026'", schoolId, gradeId);
        if (existing != null) {
            return existing;
        }
        return jdbc.queryForObject("""
                insert into classes(school_id, grade_id, name, school_year, status, created_at, updated_at)
                values (?, ?, '10A1', '2025-2026', 'ACTIVE', now(), now())
                returning id
                """, Long.class, schoolId, gradeId);
    }

    private void linkClassStudent(long classId, long studentId) {
        jdbc.update("""
                insert into class_students(class_id, student_id, joined_at, is_active)
                values (?, ?, now(), true)
                on conflict do nothing
                """, classId, studentId);
    }

    private void linkTeacherClass(long teacherId, long classId) {
        jdbc.update("""
                insert into teacher_classes(teacher_id, class_id, assigned_at, is_main_teacher)
                values (?, ?, now(), true)
                on conflict do nothing
                """, teacherId, classId);
    }

    private long upsertSubject(long createdBy) {
        return jdbc.queryForObject("""
                insert into subjects(code, name, description, status, created_by, created_at, updated_at)
                values ('BIO10', 'Sinh học', 'Môn Sinh học demo cho AR sách giáo khoa.', 'ACTIVE', ?, now(), now())
                on conflict (code) do update set name = excluded.name, updated_at = now()
                returning id
                """, Long.class, createdBy);
    }

    private long upsertTextbook(long subjectId, long gradeId, long createdBy) {
        return jdbc.queryForObject("""
                insert into textbooks(subject_id, grade_id, title, publisher, book_series, school_year, description, status, created_by, created_at, updated_at)
                values (?, ?, 'Sinh học 10 - Demo AR', 'NXB Giáo dục Việt Nam', 'Demo AR', '2025-2026',
                        'Sách demo dùng cho ứng dụng AR hiển thị nội dung 3D.', 'PUBLISHED', ?, now(), now())
                on conflict (subject_id, grade_id, title) do update
                set status = 'PUBLISHED', description = excluded.description, updated_at = now()
                returning id
                """, Long.class, subjectId, gradeId, createdBy);
    }

    private void linkClassTextbook(long classId, long textbookId, long assignedBy) {
        jdbc.update("""
                insert into class_textbooks(class_id, textbook_id, assigned_by, assigned_at)
                values (?, ?, ?, now())
                on conflict do nothing
                """, classId, textbookId, assignedBy);
    }

    private long upsertChapter(long textbookId) {
        return jdbc.queryForObject("""
                insert into chapters(textbook_id, title, order_no, description, status, created_at, updated_at)
                values (?, 'Sinh học tế bào', 1, 'Chương demo cho nội dung AR.', 'PUBLISHED', now(), now())
                on conflict (textbook_id, order_no) do update
                set title = excluded.title, status = 'PUBLISHED', updated_at = now()
                returning id
                """, Long.class, textbookId);
    }

    private long upsertLesson(long chapterId, int orderNo, String title, String summary, long createdBy) {
        return jdbc.queryForObject("""
                insert into lessons(chapter_id, title, order_no, objectives, summary, estimated_minutes, status, created_by, created_at, updated_at)
                values (?, ?, ?, 'Quan sát mô hình 3D và trả lời quiz ngắn.', ?, 15, 'PUBLISHED', ?, now(), now())
                on conflict (chapter_id, order_no) do update
                set title = excluded.title, summary = excluded.summary, status = 'PUBLISHED', updated_at = now()
                returning id
                """, Long.class, chapterId, title, orderNo, summary, createdBy);
    }

    private long upsertPage(long textbookId, long lessonId, int pageNumber, String title) {
        return jdbc.queryForObject("""
                insert into textbook_pages(textbook_id, lesson_id, page_number, page_title, description, created_at, updated_at)
                values (?, ?, ?, ?, 'Trang sách demo có image target.', now(), now())
                on conflict (textbook_id, page_number) do update
                set lesson_id = excluded.lesson_id, page_title = excluded.page_title, updated_at = now()
                returning id
                """, Long.class, textbookId, lessonId, pageNumber, title);
    }

    private long upsertMarker(long pageId, String code, String name, long createdBy) {
        return jdbc.queryForObject("""
                insert into ar_markers(textbook_page_id, marker_code, marker_type, marker_name, image_url, target_file_url, quality_score, width_cm, height_cm, status, created_by, created_at, updated_at)
                values (?, ?, 'IMAGE_TARGET', ?, concat('/markers/', ?, '.svg'), concat('/vuforia/', ?, '.dat'), 90.00, 12.00, 8.00, 'ACTIVE', ?, now(), now())
                on conflict (marker_code) do update
                set textbook_page_id = excluded.textbook_page_id, marker_name = excluded.marker_name, status = 'ACTIVE', updated_at = now()
                returning id
                """, Long.class, pageId, code, name, code, code, createdBy);
    }

    private long upsertModel(String name, String description, String fileName, long uploadedBy) {
        Long existing = queryLong("select id from three_d_models where name = ?", name);
        if (existing != null) {
            jdbc.update("""
                    update three_d_models
                    set description = ?, file_url = concat('/uploads/models/', ?), status = 'PUBLISHED', updated_at = now()
                    where id = ?
                    """, description, fileName, existing);
            return existing;
        }
        return jdbc.queryForObject("""
                insert into three_d_models(name, description, file_url, thumbnail_url, format, size_mb, polygon_count, texture_count,
                                           default_scale, license, source_name, uploaded_by, status, created_at, updated_at)
                values (?, ?, concat('/uploads/models/', ?), concat('/uploads/models/thumb-', ?), 'GLB', 1.50, 1200, 1,
                        1.0000, 'Demo placeholder asset', 'Generated primitive placeholder', ?, 'PUBLISHED', now(), now())
                returning id
                """, Long.class, name, description, fileName, fileName, uploadedBy);
    }

    private long upsertArContent(long lessonId, long markerId, long modelId, String title, String description, long createdBy, long approvedBy) {
        Long existing = queryLong("select id from ar_contents where marker_id = ?", markerId);
        if (existing != null) {
            jdbc.update("""
                    update ar_contents
                    set lesson_id = ?, default_model_id = ?, title = ?, description = ?, status = 'PUBLISHED',
                        approved_by = ?, published_at = coalesce(published_at, now()), updated_at = now()
                    where id = ?
                    """, lessonId, modelId, title, description, approvedBy, existing);
            return existing;
        }
        return jdbc.queryForObject("""
                insert into ar_contents(lesson_id, marker_id, default_model_id, title, description, content_type,
                                        instruction_text, learning_goal, status, version_no, created_by, approved_by, published_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'MODEL_3D',
                        'Đưa marker trước webcam, quan sát mô hình và bấm animation để xem chuyển động.',
                        'Nhận biết cấu trúc chính qua mô hình 3D.', 'PUBLISHED', 1, ?, ?, now(), now(), now())
                returning id
                """, Long.class, lessonId, markerId, modelId, title, description, createdBy, approvedBy);
    }

    private void linkContentModel(long contentId, long modelId, String label) {
        jdbc.update("""
                insert into ar_content_models(ar_content_id, model_id, display_order, is_default, label)
                values (?, ?, 1, true, ?)
                on conflict (ar_content_id, model_id) do update set is_default = true, label = excluded.label
                """, contentId, modelId, label);
    }

    private void seedLabels(long cellModelId, long solarModelId, long heartModelId) {
        insertLabel(cellModelId, "Nhân tế bào", "Điều khiển hoạt động của tế bào.", 0, 0.5, 0, 1);
        insertLabel(cellModelId, "Ty thể", "Cung cấp năng lượng cho tế bào.", 0.4, 0.1, 0, 2);
        insertLabel(solarModelId, "Mặt Trời", "Nguồn sáng và năng lượng trung tâm.", 0, 0, 0, 1);
        insertLabel(solarModelId, "Quỹ đạo", "Đường chuyển động mô phỏng của hành tinh.", 0.8, 0, 0, 2);
        insertLabel(heartModelId, "Tâm thất", "Buồng tim bơm máu đi nuôi cơ thể.", 0.2, 0.2, 0, 1);
        insertLabel(heartModelId, "Tâm nhĩ", "Buồng tim nhận máu trở về.", -0.2, 0.45, 0, 2);
    }

    private void insertLabel(long modelId, String name, String description, double x, double y, double z, int order) {
        Long existing = queryLong("select id from model_labels where model_id = ? and label_name = ?", modelId, name);
        if (existing == null) {
            jdbc.update("""
                    insert into model_labels(model_id, label_name, description, position_x, position_y, position_z, display_order)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, modelId, name, description, BigDecimal.valueOf(x), BigDecimal.valueOf(y), BigDecimal.valueOf(z), order);
        }
    }

    private void seedAnimations(long cellContentId, long solarContentId, long heartContentId) {
        insertAnimation(cellContentId, "RotateCell", "Xoay mô hình tế bào.");
        insertAnimation(cellContentId, "ShowParts", "Lần lượt làm nổi bật các bộ phận tế bào.");
        insertAnimation(solarContentId, "OrbitSimulation", "Mô phỏng chuyển động quỹ đạo.");
        insertAnimation(heartContentId, "BeatHeart", "Mô phỏng nhịp đập trái tim.");
    }

    private void insertAnimation(long contentId, String name, String description) {
        Long existing = queryLong("select id from animations where ar_content_id = ? and name = ?", contentId, name);
        if (existing == null) {
            jdbc.update("""
                    insert into animations(ar_content_id, name, description, animation_clip_name, duration_seconds, order_no, auto_play, created_at)
                    values (?, ?, ?, ?, 8, 1, false, now())
                    """, contentId, name, description, name);
        }
    }

    private void seedQuiz(long lessonId, long createdBy) {
        Long quizId = queryLong("select id from quizzes where lesson_id = ? and title = 'Quiz cấu tạo tế bào'", lessonId);
        if (quizId == null) {
            quizId = jdbc.queryForObject("""
                    insert into quizzes(lesson_id, title, description, time_limit_minutes, max_attempts, passing_score, status, created_by, created_at, updated_at)
                    values (?, 'Quiz cấu tạo tế bào', '5 câu hỏi kiểm tra nhanh sau khi xem mô hình tế bào.', 10, 3, 60, 'PUBLISHED', ?, now(), now())
                    returning id
                    """, Long.class, lessonId, createdBy);
            addQuestion(quizId, "Bào quan nào được ví như nhà máy năng lượng của tế bào?", "SINGLE_CHOICE", 1,
                    List.of(answer("Ty thể", true), answer("Nhân tế bào", false), answer("Màng sinh chất", false), answer("Không bào", false)));
            addQuestion(quizId, "Nhân tế bào có vai trò chính là gì?", "SINGLE_CHOICE", 2,
                    List.of(answer("Điều khiển hoạt động của tế bào", true), answer("Tạo thành vách tế bào", false), answer("Dự trữ nước", false), answer("Tiêu hóa thức ăn", false)));
            addQuestion(quizId, "Các thành phần nào có thể quan sát trong mô hình tế bào demo?", "MULTIPLE_CHOICE", 3,
                    List.of(answer("Nhân tế bào", true), answer("Ty thể", true), answer("Mặt Trời", false), answer("Tâm thất", false)));
            addQuestion(quizId, "Màng sinh chất giúp bao bọc và trao đổi chất với môi trường.", "TRUE_FALSE", 4,
                    List.of(answer("Đúng", true), answer("Sai", false)));
            addQuestion(quizId, "Khi xem AR, học sinh nên làm gì để học hiệu quả?", "SINGLE_CHOICE", 5,
                    List.of(answer("Quan sát model, đọc label và làm quiz", true), answer("Chỉ bấm reset liên tục", false), answer("Che marker khi camera quét", false), answer("Tắt ánh sáng phòng", false)));
        }
    }

    private AnswerSeed answer(String text, boolean correct) {
        return new AnswerSeed(text, correct);
    }

    private void addQuestion(long quizId, String text, String type, int orderNo, List<AnswerSeed> answers) {
        Long questionId = jdbc.queryForObject("""
                insert into quiz_questions(quiz_id, question_text, question_type, score, order_no, explanation)
                values (?, ?, ?::quiz_question_type, 1, ?, 'Xem lại mô hình 3D và nhãn chú thích để củng cố kiến thức.')
                returning id
                """, Long.class, quizId, text, type, orderNo);
        int answerOrder = 1;
        for (AnswerSeed answer : answers) {
            jdbc.update("""
                    insert into quiz_answers(question_id, answer_text, is_correct, order_no)
                    values (?, ?, ?, ?)
                    """, questionId, answer.text(), answer.correct(), answerOrder++);
        }
    }

    private void seedAssignment(long teacherId, long classId, long lessonId, long studentId) {
        Long assignmentId = queryLong("select id from teacher_assignments where teacher_id = ? and class_id = ? and lesson_id = ?", teacherId, classId, lessonId);
        if (assignmentId == null) {
            assignmentId = jdbc.queryForObject("""
                    insert into teacher_assignments(teacher_id, class_id, lesson_id, title, note, due_date, status, created_at, updated_at)
                    values (?, ?, ?, 'Học AR bài Cấu tạo tế bào', 'Xem mô hình tế bào và hoàn thành quiz.', now() + interval '7 days', 'ACTIVE', now(), now())
                    returning id
                    """, Long.class, teacherId, classId, lessonId);
        }
        jdbc.update("""
                insert into assignment_students(assignment_id, student_id, status)
                values (?, ?, 'NOT_STARTED')
                on conflict do nothing
                """, assignmentId, studentId);
    }

    private void seedSettings(long adminId) {
        jdbc.update("""
                insert into system_settings(setting_key, setting_value, description, updated_by, updated_at)
                values ('allowed_model_formats', 'GLB,GLTF,FBX,OBJ,USDZ', 'Các định dạng model 3D cho phép upload.', ?, now())
                on conflict (setting_key) do update set setting_value = excluded.setting_value, updated_at = now()
                """, adminId);
    }

    private long upsertByCode(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private Long queryLong(String sql, Object... args) {
        List<Long> ids = jdbc.query(sql, this::mapLong, args);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Long mapLong(ResultSet rs, int rowNum) throws SQLException {
        return rs.getLong(1);
    }

    private record AnswerSeed(String text, boolean correct) {
    }
}
