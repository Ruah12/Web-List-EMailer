package com.kisoft.emaillist.controller;

import com.kisoft.emaillist.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
