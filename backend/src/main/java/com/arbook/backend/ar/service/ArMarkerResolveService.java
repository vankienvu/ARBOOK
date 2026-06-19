package com.arbook.backend.ar.service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.arbook.backend.common.exception.BusinessException;

@Service
public class ArMarkerResolveService {
    private final JdbcTemplate jdbc;
    private final String backendBaseUrl;

    public ArMarkerResolveService(JdbcTemplate jdbc, @Value("${app.backend-base-url}") String backendBaseUrl) {
        this.jdbc = jdbc;
        this.backendBaseUrl = backendBaseUrl;
    }

    public Map<String, Object> resolve(String markerCode) {
        List<Map<String, Object>> rows = jdbc.query("""
                select m.id marker_id,
                       m.marker_code,
                       m.marker_name,
                       l.id lesson_id,
                       l.title lesson_title,
                       c.id ar_content_id,
                       c.title ar_content_title,
                       c.description,
                       c.status::text content_status,
                       model.id model_id,
                       model.name model_name,
                       model.file_url model_url,
                       array_remove(array_agg(distinct a.name), null) animation_names
                from ar_markers m
                join textbook_pages p on p.id = m.textbook_page_id
                join lessons l on l.id = p.lesson_id
                left join ar_contents c on c.marker_id = m.id and c.lesson_id = l.id
                left join three_d_models model on model.id = c.default_model_id
                left join animations a on a.ar_content_id = c.id
                where m.marker_code = ?
                  and m.status = 'ACTIVE'
                group by m.id, l.id, c.id, model.id
                """, this::mapResolveRow, markerCode);

        if (rows.isEmpty()) {
            throw new BusinessException("MARKER_NOT_FOUND", "Không tìm thấy marker trong hệ thống.", HttpStatus.NOT_FOUND);
        }

        Map<String, Object> row = rows.getFirst();
        if (row.get("arContentId") == null || !"PUBLISHED".equals(row.get("status"))) {
            throw new BusinessException("CONTENT_NOT_PUBLISHED", "Nội dung AR chưa được xuất bản.", HttpStatus.CONFLICT);
        }

        Long modelId = (Long) row.get("modelId");
        row.remove("modelId");
        row.put("labels", labels(modelId));
        return row;
    }

    private Map<String, Object> mapResolveRow(ResultSet rs, int rowNum) throws SQLException {
        String modelUrl = rs.getString("model_url");
        if (modelUrl != null && modelUrl.startsWith("/")) {
            modelUrl = backendBaseUrl + modelUrl;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("markerCode", rs.getString("marker_code"));
        body.put("markerName", rs.getString("marker_name"));
        body.put("lessonId", rs.getLong("lesson_id"));
        body.put("lessonTitle", rs.getString("lesson_title"));
        body.put("arContentId", nullableLong(rs, "ar_content_id"));
        body.put("arContentTitle", nullableString(rs, "ar_content_title"));
        body.put("description", nullableString(rs, "description"));
        body.put("modelId", nullableLong(rs, "model_id"));
        body.put("modelName", nullableString(rs, "model_name"));
        body.put("modelUrl", modelUrl);
        body.put("animationNames", toStringList(rs.getArray("animation_names")));
        body.put("status", nullableString(rs, "content_status"));
        return body;
    }

    private List<Map<String, Object>> labels(Long modelId) {
        if (modelId == null) {
            return List.of();
        }
        return jdbc.queryForList("""
                select label_name as name, description
                from model_labels
                where model_id = ?
                order by display_order, id
                """, modelId);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String nullableString(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    private List<String> toStringList(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object value = array.getArray();
        if (value instanceof String[] strings) {
            return List.of(strings);
        }
        Object[] objects = (Object[]) value;
        return java.util.Arrays.stream(objects).map(String::valueOf).toList();
    }
}
