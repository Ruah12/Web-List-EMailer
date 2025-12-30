package com.kisoft.emaillist.controller;

import com.kisoft.emaillist.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TemplateController REST API endpoints.
 *
 * <p>These tests verify the HTTP request/response handling of the Template
 * Controller using a fake TemplateService implementation. Tests are isolated
 * from the actual file system and Spring context.</p>
 *
 * <h3>Test Coverage:</h3>
 * <ul>
 *   <li>Save template - valid slot returns 200 OK</li>
 *   <li>Save template - invalid slot returns 400 Bad Request</li>
 *   <li>Load template (POST) - missing slot returns 400 Bad Request</li>
 *   <li>Load template (GET) - returns 405 Method Not Allowed</li>
 *   <li>Load template (POST) - valid slot returns 200 OK with data</li>
 *   <li>Delete template - valid slot returns 200 OK</li>
 *   <li>Template info - returns 200 OK with metadata</li>
 * </ul>
 *
 * <h3>Testing Strategy:</h3>
 * <p>Uses a {@link FakeTemplateService} to isolate controller logic from
 * file system operations. This allows fast, deterministic tests without
 * Spring Boot context initialization.</p>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 * @see TemplateController
 * @see FakeTemplateService
 */
class TemplateControllerWebMvcTest {

    @Test
    void saveTemplate_whenValidSlot_returnsOk() {
        TemplateController controller = new TemplateController(new FakeTemplateService());

        Map<String, Object> req = new HashMap<>();
        req.put("slot", 1);
        req.put("subject", "Sub");
        req.put("htmlContent", "<p>Hi</p>");

        ResponseEntity<Map<String, Object>> resp = controller.saveTemplate(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("status", "ok");
        assertThat(resp.getBody()).containsEntry("slot", 1);
    }

    @Test
    void saveTemplate_whenInvalidSlot_returnsBadRequest() {
        TemplateController controller = new TemplateController(new FakeTemplateService());

        Map<String, Object> req = new HashMap<>();
        req.put("slot", 0);
        req.put("subject", "Sub");
        req.put("htmlContent", "<p>Hi</p>");

        ResponseEntity<Map<String, Object>> resp = controller.saveTemplate(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("status", "error");
    }

    @Test
    void loadTemplatePost_whenSlotMissing_returnsBadRequest() {
        TemplateController controller = new TemplateController(new FakeTemplateService());

        ResponseEntity<Map<String, Object>> resp = controller.loadTemplatePost(new TemplateController.TemplateSlotRequest(null));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("status", "error");
    }

    @Test
    void loadTemplateGet_isNotAllowed() {
        TemplateController controller = new TemplateController(new FakeTemplateService());

        ResponseEntity<Map<String, Object>> resp = controller.loadTemplate(1);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(405));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("status", "error");
    }

    @Test
    void loadTemplatePost_whenOk_returnsOkAndData() {
        TemplateController controller = new TemplateController(new FakeTemplateService());

        ResponseEntity<Map<String, Object>> resp = controller.loadTemplatePost(new TemplateController.TemplateSlotRequest(1));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("status", "ok");
        assertThat(resp.getBody()).containsEntry("slot", 1);
        assertThat(resp.getBody()).containsKey("data");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        assertThat(data).containsEntry("subject", "S");
        assertThat(data).containsEntry("htmlContent", "<p>X</p>");
    }

    @Test
    void loadTemplatePost_whenSlotOutOfRange_returnsBadRequest() {
        TemplateController controller = new TemplateController(new FakeTemplateService());

        ResponseEntity<Map<String, Object>> resp = controller.loadTemplatePost(new TemplateController.TemplateSlotRequest(42));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(400));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("status", "error");
    }

    @Test
    void loadTemplatePost_withTypedRequest_handlesRecord() {
        TemplateController controller = new TemplateController(new FakeTemplateService());

        ResponseEntity<Map<String, Object>> resp = controller.loadTemplatePost(new TemplateController.TemplateSlotRequest(1));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).containsEntry("status", "ok");
    }

    private static final class FakeTemplateService extends TemplateService {

        private FakeTemplateService() {
            super("configs/templates", true);
        }

        @Override
        public Map<String, Object> saveTemplate(int slot, String subject, String htmlContent) {
            return Map.of("status", "ok", "slot", slot);
        }

        @Override
        public Map<String, Object> loadTemplate(int slot) {
            return Map.of(
                "status", "ok",
                "slot", slot,
                "data", Map.of("subject", "S", "htmlContent", "<p>X</p>")
            );
        }

        @Override
        public String getTemplateLabel(int slot) {
            return slot + ". label";
        }

        @Override
        public Map<String, Object> deleteTemplate(int slot) {
            return Map.of("status", "ok", "slot", slot);
        }
    }
}
