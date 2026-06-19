package com.arbook.backend.model3d.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;

import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.common.exception.BusinessException;
import com.arbook.backend.common.service.StorageService;
import com.arbook.backend.security.SecurityFacade;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final JdbcTemplate jdbc;
    private final SecurityFacade security;
    private final StorageService storageService;

    public ModelController(JdbcTemplate jdbc, SecurityFacade security, StorageService storageService) {
        this.jdbc = jdbc;
        this.security = security;
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "File tải lên không được để trống.", HttpStatus.BAD_REQUEST);
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".glb") && !fileName.endsWith(".gltf"))) {
            throw new BusinessException("INVALID_FORMAT", "Định dạng file không được hỗ trợ. Chỉ hỗ trợ .glb hoặc .gltf.", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType != null && (contentType.contains("html") || contentType.contains("javascript") || contentType.contains("jsp") || contentType.contains("php") || contentType.contains("exec"))) {
            throw new BusinessException("INVALID_MIME", "Kiểu nội dung tập tin không hợp lệ. Nghiêm cấm tải lên mã kịch bản hoặc tập tin thực thi.", HttpStatus.BAD_REQUEST);
        }
        
        String fileUrl = storageService.storeFile(file, "models");
        
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "fileName", file.getOriginalFilename(),
            "fileUrl", fileUrl,
            "sizeMb", BigDecimal.valueOf(file.getSize() / (1024.0 * 1024.0))
        )));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody ModelRequest request) {
        Long id = jdbc.queryForObject("""
                insert into three_d_models(name, description, file_url, thumbnail_url, format, size_mb, polygon_count,
                                           texture_count, default_scale, default_rotation_x, default_rotation_y, default_rotation_z,
                                           license, source_name, uploaded_by, status, created_at, updated_at)
                values (?, ?, ?, ?, ?::model_format, ?, ?, ?, coalesce(?, 1), coalesce(?, 0), coalesce(?, 0), coalesce(?, 0),
                        ?, ?, ?, coalesce(?::publish_status, 'DRAFT'), now(), now())
                returning id
                """, Long.class, request.name(), request.description(), request.fileUrl(), request.thumbnailUrl(),
                request.format(), request.sizeMb(), request.polygonCount(), request.textureCount(), request.defaultScale(),
                request.defaultRotationX(), request.defaultRotationY(), request.defaultRotationZ(), request.license(),
                request.sourceName(), security.currentUserIdOrNull(), request.status());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        if (security.hasRole("STUDENT")) {
            return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                    select id, name, description, file_url, thumbnail_url, format::text format, size_mb,
                           polygon_count, texture_count, default_scale, status::text status, created_at
                    from three_d_models
                    where status = 'PUBLISHED'
                    order by created_at desc
                    """)));
        }
        return ResponseEntity.ok(ApiResponse.ok(jdbc.queryForList("""
                select id, name, description, file_url, thumbnail_url, format::text format, size_mb,
                       polygon_count, texture_count, default_scale, status::text status, created_at
                from three_d_models
                order by created_at desc
                """)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select *, format::text format, status::text status
                from three_d_models
                where id = ?
                """, id);
        if (rows.isEmpty()) {
            throw new BusinessException("MODEL_NOT_FOUND", "Không tìm thấy model 3D.", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> model = rows.getFirst();
        if (security.hasRole("STUDENT") && !"PUBLISHED".equals(String.valueOf(model.get("status")))) {
            throw new BusinessException("MODEL_NOT_PUBLISHED", "Model 3D chưa được xuất bản.", HttpStatus.CONFLICT);
        }
        return ResponseEntity.ok(ApiResponse.ok(model));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTENT_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        jdbc.update("update three_d_models set status = 'ARCHIVED', updated_at = now() where id = ?", id);
        return ResponseEntity.ok(ApiResponse.<Void>ok("Đã lưu trữ model.", null));
    }

    public record ModelRequest(
            @NotBlank String name,
            String description,
            @NotBlank String fileUrl,
            String thumbnailUrl,
            @NotBlank String format,
            java.math.BigDecimal sizeMb,
            Integer polygonCount,
            Integer textureCount,
            java.math.BigDecimal defaultScale,
            java.math.BigDecimal defaultRotationX,
            java.math.BigDecimal defaultRotationY,
            java.math.BigDecimal defaultRotationZ,
            String license,
            String sourceName,
            String status
    ) {
    }
}
