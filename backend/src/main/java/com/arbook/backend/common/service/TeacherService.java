package com.arbook.backend.common.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.arbook.backend.security.SecurityFacade;

@Service
public class TeacherService {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;

    public TeacherService(JdbcTemplate jdbc, SecurityFacade security) {
        this.jdbc = jdbc;
        this.security = security;
    }

    public List<Map<String, Object>> getDashboardRows() {
        Long teacherId = security.currentUserIdOrNull();
        if (teacherId == null) {
            return List.of();
        }
        return jdbc.queryForList("""
                select title label, status::text value
                from teacher_assignments
                where teacher_id = ?
                order by created_at desc
                limit 10
                """, teacherId);
    }

    public List<Map<String, Object>> getClassStudents(Long classId) {
        return jdbc.queryForList("""
                select u.full_name label, u.email value
                from class_students cs
                join users u on u.id = cs.student_id
                where cs.class_id = ? and cs.is_active = true
                order by u.full_name
                """, classId);
    }

    public Map<String, Object> getClassAnalytics(Long classId) {
        List<Map<String, Object>> quizDistribution = jdbc.queryForList("""
                select 
                    case 
                        when attempt.score >= 9.0 then '9-10'
                        when attempt.score >= 7.0 then '7-8.9'
                        when attempt.score >= 5.0 then '5-6.9'
                        else 'Dưới 5'
                    end as label,
                    count(distinct attempt.student_id) as value
                from quiz_attempts attempt
                join class_students cs on cs.student_id = attempt.student_id
                where cs.class_id = ? and cs.is_active = true and attempt.submitted_at is not null
                group by 
                    case 
                        when attempt.score >= 9.0 then '9-10'
                        when attempt.score >= 7.0 then '7-8.9'
                        when attempt.score >= 5.0 then '5-6.9'
                        else 'Dưới 5'
                    end
                """, classId);

        List<Map<String, Object>> assignmentProgress = jdbc.queryForList("""
                select ast.status::text as label, count(*) as value
                from assignment_students ast
                join teacher_assignments ta on ta.id = ast.assignment_id
                where ta.class_id = ?
                group by ast.status
                """, classId);

        return Map.of(
            "quizDistribution", quizDistribution,
            "assignmentProgress", assignmentProgress
        );
    }
}
