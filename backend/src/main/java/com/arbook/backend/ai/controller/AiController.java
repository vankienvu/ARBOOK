package com.arbook.backend.ai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.arbook.backend.common.ApiResponse;
import com.arbook.backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiController {
    private final JdbcTemplate jdbc;
    private final RestTemplate restTemplate;
    
    @Value("${app.openai.enabled:false}")
    private boolean enabled;
    
    @Value("${app.openai.api-key:}")
    private String apiKey;

    public AiController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, String>>> chat(@RequestBody ChatRequest request) {
        if (request.lessonId() == null) {
            throw new BusinessException("LESSON_REQUIRED", "Không tìm thấy thông tin bài học để cung cấp ngữ cảnh.", HttpStatus.BAD_REQUEST);
        }
        if (request.message() == null || request.message().trim().isEmpty()) {
            throw new BusinessException("PROMPT_REQUIRED", "Nội dung câu hỏi không được để trống.", HttpStatus.BAD_REQUEST);
        }
        if (request.message().length() > 500) {
            throw new BusinessException("PROMPT_TOO_LONG", "Câu hỏi quá dài. Vui lòng nhập dưới 500 ký tự.", HttpStatus.BAD_REQUEST);
        }

        // Fetch lesson details to feed into System context
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select l.title, l.description, s.name subject_name, g.name grade_name
                from lessons l
                join chapters c on c.id = l.chapter_id
                join textbooks t on t.id = c.textbook_id
                join subjects s on s.id = t.subject_id
                join grades g on g.id = t.grade_id
                where l.id = ?
                """, request.lessonId());

        String lessonContext = "";
        if (!rows.isEmpty()) {
            Map<String, Object> l = rows.getFirst();
            lessonContext = String.format(
                "Môn học: %s, Khối lớp: %s, Bài học: %s, Mô tả bài học: %s",
                l.get("subject_name"), l.get("grade_name"), l.get("title"), l.get("description")
            );
        }

        // System instructions / prompt framing
        String systemInstruction = "Bạn là Trợ lý Học tập AI xuất sắc của ứng dụng ARBook (Sách giáo khoa Thực tế tăng cường).\n" +
            "Hãy trả lời câu hỏi của học sinh một cách thân thiện, ngắn gọn, dễ hiểu, mang tính giáo khoa khoa học và truyền cảm hứng.\n" +
            "Sử dụng thông tin ngữ cảnh bài học sau đây nếu có để trả lời:\n" +
            "--- Ngữ cảnh bài học ---\n" +
            (lessonContext.isEmpty() ? "Không có thông tin cụ thể." : lessonContext) + "\n" +
            "------------------------\n" +
            "Lưu ý: Chỉ trả lời bằng Tiếng Việt. Tránh câu trả lời quá dài dòng dông dài (khoảng dưới 200 từ). Hỗ trợ định dạng Markdown nhẹ để dễ đọc.";

        String responseText = "";

        if (enabled && org.springframework.util.StringUtils.hasText(apiKey)) {
            try {
                String url = "https://api.openai.com/v1/chat/completions";
                
                // Construct payload
                Map<String, Object> sysMsg = Map.of("role", "system", "content", systemInstruction);
                Map<String, Object> userMsg = Map.of("role", "user", "content", request.message());
                
                Map<String, Object> payload = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(sysMsg, userMsg)
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                
                ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
                Map body = response.getBody();
                
                if (body != null && body.containsKey("choices")) {
                    List choices = (List) body.get("choices");
                    if (!choices.isEmpty()) {
                        Map choice = (Map) choices.getFirst();
                        if (choice.containsKey("message")) {
                            Map messageObj = (Map) choice.get("message");
                            responseText = String.valueOf(messageObj.get("content"));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("OpenAI API Error: ", e);
                responseText = "Xin lỗi, hiện tại trợ lý học tập AI đang gặp sự cố kết nối. Vui lòng thử lại sau.";
            }
        } else {
            // Mock Response for Demonstration
            responseText = "✨ [Mô phỏng AI Tutor] Bạn đang hỏi về bài học: *" + (rows.isEmpty() ? "Chưa rõ" : rows.getFirst().get("title")) + "*.\n\n" +
                "Hiện tại tính năng AI OpenAI đang chạy ở chế độ mô phỏng (do chưa bật `app.openai.enabled` hoặc thiếu API Key trong cấu hình).\n\n" +
                "Câu hỏi của bạn: *\"" + request.message() + "\u201d*\n\n" +
                "**Gợi ý trả lời:** Bạn hãy cấu hình `APP_OPENAI_API_KEY` của bạn trong file `.env` hoặc `application.properties` để nhận câu trả lời thực tế, trực quan từ mô hình AI OpenAI GPT-4o-mini!";
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of("reply", responseText)));
    }

    public record ChatRequest(Long lessonId, String message) {
    }
}
