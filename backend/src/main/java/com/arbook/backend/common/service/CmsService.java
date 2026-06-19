package com.arbook.backend.common.service;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CmsService {
    private final JdbcTemplate jdbc;

    public CmsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> getDashboardRows() {
        return jdbc.queryForList("""
                select marker_code label, marker_name value
                from ar_markers
                order by created_at desc
                limit 10
                """);
    }

    public List<Map<String, Object>> getArContentPreview(Long arContentId) {
        return jdbc.queryForList("""
                select 'Tiêu đề' label, title value from ar_contents where id = ?
                union all select 'Trạng thái', status::text from ar_contents where id = ?
                union all select 'Mô tả', description from ar_contents where id = ?
                """, arContentId, arContentId, arContentId);
    }

    public List<String> getArContentModelUrl(Long arContentId) {
        return jdbc.queryForList("""
                select model.file_url
                from ar_contents c
                join three_d_models model on model.id = c.default_model_id
                where c.id = ?
                """, String.class, arContentId);
    }
}
