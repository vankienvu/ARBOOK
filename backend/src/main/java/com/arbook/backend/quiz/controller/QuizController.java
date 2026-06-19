package com.arbook.backend.quiz.controller;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.common.exception.BusinessException;
import com.arbook.backend.security.AccessGuard;
import com.arbook.backend.security.SecurityFacade;

@RestController
@RequestMapping("/api")
public class QuizController {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;
    private final AccessGuard accessGuard;

    public QuizController(JdbcTemplate jdbc, SecurityFacade security, AccessGuard accessGuard) {
        this.jdbc = jdbc;
        this.security = security;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/lessons/{lessonId}/quizzes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> quizzesByLesson(@PathVariable Long lessonId) {
        accessGuard.requireStudentCanAccessLesson(lessonId);
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select id, lesson_id, title, description, time_limit_minutes, max_attempts, passing_score, status::text status
                from quizzes
                where lesson_id = ? and status = 'PUBLISHED'
                order by id
                """, lessonId)));
    }

    @PostMapping("/quizzes")
    @PreAuthorize("hasAnyRole('TEACHER','CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createQuiz(@Valid @RequestBody QuizRequest request) {
        Long id = jdbc.queryForObject("""
                insert into quizzes(lesson_id, title, description, time_limit_minutes, max_attempts, passing_score, status, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, coalesce(?::publish_status, 'DRAFT'), ?, now(), now())
                returning id
                """, Long.class, request.lessonId(), request.title(), request.description(), request.timeLimitMinutes(),
                request.maxAttempts(), request.passingScore(), request.status(), security.currentUserIdOrNull());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQuiz(@PathVariable Long id) {
        Long lessonId = jdbc.queryForObject("select lesson_id from quizzes where id = ?", Long.class, id);
        accessGuard.requireStudentCanAccessLesson(lessonId);
        Map<String, Object> quiz = new java.util.LinkedHashMap<>(jdbc.queryForMap("""
                select id, lesson_id, title, description, time_limit_minutes, max_attempts, passing_score, status::text status
                from quizzes where id = ?
                """, id));
        List<Map<String, Object>> questions = jdbc.queryForList("""
                select id, quiz_id, question_text, question_type::text question_type, score, order_no, explanation, image_url
                from quiz_questions where quiz_id = ? order by order_no
                """, id);
        
        List<Map<String, Object>> allAnswers = jdbc.queryForList("""
                select id, question_id, answer_text, false as is_correct, order_no
                from quiz_answers
                where question_id in (select id from quiz_questions where quiz_id = ?)
                order by question_id, order_no
                """, id);

        Map<Long, List<Map<String, Object>>> answersByQuestionId = new java.util.HashMap<>();
        for (Map<String, Object> answer : allAnswers) {
            Long questionId = ((Number) answer.get("question_id")).longValue();
            answersByQuestionId.computeIfAbsent(questionId, k -> new java.util.ArrayList<>()).add(answer);
        }

        for (Map<String, Object> question : questions) {
            Long questionId = ((Number) question.get("id")).longValue();
            question.put("answers", answersByQuestionId.getOrDefault(questionId, java.util.List.of()));
        }
        quiz.put("questions", questions);
        return ResponseEntity.ok(ApiResponse.ok(quiz));
    }

    @PostMapping("/quizzes/{id}/submit")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@PathVariable Long id, @RequestBody SubmitQuizRequest request) {
        Long studentId = security.currentUserIdOrNull();
        if (studentId == null) {
            throw new BusinessException("AUTHENTICATION_REQUIRED", "Bạn cần đăng nhập để nộp bài.", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> quizInfo = jdbc.queryForMap("""
                select lesson_id, max_attempts
                from quizzes
                where id = ?
                """, id);
        accessGuard.requireStudentCanAccessLesson((Long) quizInfo.get("lesson_id"));
        Integer maxAttempts = (Integer) quizInfo.get("max_attempts");
        Integer usedAttempts = jdbc.queryForObject("""
                select count(*) from quiz_attempts where quiz_id = ? and student_id = ?
                """, Integer.class, id, studentId);
        if (maxAttempts != null && maxAttempts > 0 && usedAttempts != null && usedAttempts >= maxAttempts) {
            throw new BusinessException("QUIZ_ATTEMPT_LIMIT_REACHED", "Bạn đã dùng hết số lần làm quiz.", HttpStatus.CONFLICT);
        }
        Integer attemptNo = jdbc.queryForObject("""
                select coalesce(max(attempt_no), 0) + 1 from quiz_attempts where quiz_id = ? and student_id = ?
                """, Integer.class, id, studentId);
        BigDecimal totalScore = jdbc.queryForObject("select coalesce(sum(score), 0) from quiz_questions where quiz_id = ?", BigDecimal.class, id);
        Long attemptId = jdbc.queryForObject("""
                insert into quiz_attempts(quiz_id, student_id, attempt_no, total_score, started_at)
                values (?, ?, ?, ?, now())
                returning id
                """, Long.class, id, studentId, attemptNo, totalScore);

        // Load all questions of the quiz upfront to avoid N+1 query
        List<Map<String, Object>> questionsList = jdbc.queryForList(
                "select id, score from quiz_questions where quiz_id = ?", id);
        Map<Long, BigDecimal> questionScores = new java.util.HashMap<>();
        for (Map<String, Object> q : questionsList) {
            questionScores.put(((Number) q.get("id")).longValue(), (BigDecimal) q.get("score"));
        }

        // Load all correct answers for these questions upfront
        List<Map<String, Object>> correctAnswersList = jdbc.queryForList("""
                select a.id, a.question_id
                from quiz_answers a
                join quiz_questions q on q.id = a.question_id
                where q.quiz_id = ? and a.is_correct = true
                """, id);
        Map<Long, Set<Long>> correctAnswersMap = new java.util.HashMap<>();
        for (Map<String, Object> ca : correctAnswersList) {
            Long qId = ((Number) ca.get("question_id")).longValue();
            Long ansId = ((Number) ca.get("id")).longValue();
            correctAnswersMap.computeIfAbsent(qId, k -> new java.util.HashSet<>()).add(ansId);
        }

        BigDecimal score = BigDecimal.ZERO;
        for (AnswerSubmission answer : request.answers()) {
            Long qId = answer.questionId();
            if (!questionScores.containsKey(qId)) {
                throw new BusinessException("INVALID_QUESTION", "Câu hỏi không thuộc đề thi này hoặc không tồn tại.", HttpStatus.BAD_REQUEST);
            }
            BigDecimal questionScore = questionScores.get(qId);
            Set<Long> correctIds = correctAnswersMap.getOrDefault(qId, Set.of());
            Set<Long> submittedIds = answer.answerIds() == null ? Set.of() : new HashSet<>(answer.answerIds());
            boolean correct = !correctIds.isEmpty() && correctIds.equals(submittedIds);
            BigDecimal earned = correct ? questionScore : BigDecimal.ZERO;
            score = score.add(earned);
            if (submittedIds.isEmpty()) {
                jdbc.update("""
                        insert into quiz_attempt_answers(attempt_id, question_id, answer_text, is_correct, score_earned)
                        values (?, ?, ?, ?, ?)
                        """, attemptId, qId, answer.answerText(), correct, earned);
            } else {
                for (Long answerId : submittedIds) {
                    jdbc.update("""
                            insert into quiz_attempt_answers(attempt_id, question_id, answer_id, is_correct, score_earned)
                            values (?, ?, ?, ?, ?)
                            """, attemptId, qId, answerId, correct, earned);
                }
            }
        }
        BigDecimal passingScore = jdbc.queryForObject("select passing_score from quizzes where id = ?", BigDecimal.class, id);
        boolean passed = totalScore.signum() == 0 || score.multiply(BigDecimal.valueOf(100)).divide(totalScore, 2, java.math.RoundingMode.HALF_UP).compareTo(passingScore) >= 0;
        jdbc.update("""
                update quiz_attempts set score = ?, submitted_at = now(), is_passed = ? where id = ?
                """, score, passed, attemptId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("attemptId", attemptId, "score", score, "totalScore", totalScore, "passed", passed)));
    }

    @GetMapping("/quizzes/{id}/results")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> results(@PathVariable Long id) {
        if (!security.hasRole("ADMIN")) {
            return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                    select distinct qa.id, u.full_name student_name, qa.attempt_no, qa.score, qa.total_score, qa.is_passed, qa.submitted_at
                    from quiz_attempts qa
                    join users u on u.id = qa.student_id
                    join class_students cs on cs.student_id = u.id and cs.is_active = true
                    join teacher_classes tc on tc.class_id = cs.class_id
                    where qa.quiz_id = ? and tc.teacher_id = ?
                    order by qa.submitted_at desc
                    """, id, security.currentUserIdOrNull())));
        }
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select qa.id, u.full_name student_name, qa.attempt_no, qa.score, qa.total_score, qa.is_passed, qa.submitted_at
                from quiz_attempts qa
                join users u on u.id = qa.student_id
                where qa.quiz_id = ?
                order by qa.submitted_at desc
                """, id)));
    }

    public record QuizRequest(
            @NotNull Long lessonId,
            @NotBlank String title,
            String description,
            Integer timeLimitMinutes,
            Integer maxAttempts,
            BigDecimal passingScore,
            String status
    ) {
    }

    public record SubmitQuizRequest(List<AnswerSubmission> answers) {
    }

    public record AnswerSubmission(Long questionId, List<Long> answerIds, String answerText) {
    }
}
