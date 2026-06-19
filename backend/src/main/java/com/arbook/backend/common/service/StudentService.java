package com.arbook.backend.common.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.arbook.backend.security.SecurityFacade;

@Service
public class StudentService {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;

    public StudentService(JdbcTemplate jdbc, SecurityFacade security) {
        this.jdbc = jdbc;
        this.security = security;
    }

    public List<Map<String, Object>> getDashboardRows() {
        Long userId = security.currentUserIdOrNull();
        if (userId == null) {
            return List.of();
        }
        return jdbc.queryForList("""
                select l.title label, coalesce(lp.status::text, 'NOT_STARTED') value
                from lessons l
                left join learning_progress lp on lp.lesson_id = l.id and lp.student_id = ?
                order by l.order_no
                limit 10
                """, userId);
    }

    public List<Map<String, Object>> getLessonDetails(Long lessonId) {
        return jdbc.queryForList("""
                select 'Bài học' label, l.title value from lessons l where l.id = ?
                union all
                select 'AR content', c.title from ar_contents c where c.lesson_id = ?
                union all
                select 'Quiz', q.title from quizzes q where q.lesson_id = ?
                """, lessonId, lessonId, lessonId);
    }

    public List<String> getModelUrls(Long lessonId) {
        return jdbc.queryForList("""
                select model.file_url
                from ar_contents c
                join three_d_models model on model.id = c.default_model_id
                where c.lesson_id = ? and c.status = 'PUBLISHED'
                """, String.class, lessonId);
    }

    public List<Map<String, Object>> getQuizQuestions(Long quizId) {
        return jdbc.queryForList("""
                select concat('Câu ', order_no) label, question_text value
                from quiz_questions
                where quiz_id = ?
                order by order_no
                """, quizId);
    }
}
