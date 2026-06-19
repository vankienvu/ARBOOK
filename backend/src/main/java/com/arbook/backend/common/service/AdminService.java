package com.arbook.backend.common.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;

    public AdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> getDashboardRows() {
        return jdbc.queryForList("""
                select email label, status::text value
                from users
                order by created_at desc
                limit 10
                """);
    }

    public List<Map<String, Object>> getPendingReviews() {
        return jdbc.queryForList("""
                select r.id as review_id, c.id as ar_content_id, c.title as content_title,
                       u.full_name as requested_by_name, r.requested_at
                from content_review_requests r
                join ar_contents c on c.id = r.ar_content_id
                join users u on u.id = r.requested_by
                where r.status = 'PENDING'
                order by r.requested_at asc
                """);
    }

    public void approveContent(Long reviewId, Long adminId) {
        jdbc.update("""
                update content_review_requests
                set status = 'APPROVED', reviewed_by = ?, reviewed_at = now()
                where id = ?
                """, adminId, reviewId);
        
        Long contentId = jdbc.queryForObject("""
                select ar_content_id from content_review_requests where id = ?
                """, Long.class, reviewId);

        jdbc.update("""
                update ar_contents
                set status = 'PUBLISHED', approved_by = ?, published_at = now(), updated_at = now()
                where id = ?
                """, adminId, contentId);
    }

    public void rejectContent(Long reviewId, Long adminId, String comment) {
        jdbc.update("""
                update content_review_requests
                set status = 'REJECTED', reviewed_by = ?, reviewed_at = now(), review_comment = ?
                where id = ?
                """, adminId, comment, reviewId);
        
        Long contentId = jdbc.queryForObject("""
                select ar_content_id from content_review_requests where id = ?
                """, Long.class, reviewId);

        jdbc.update("""
                update ar_contents
                set status = 'REJECTED', updated_at = now()
                where id = ?
                """, contentId);
    }
}
