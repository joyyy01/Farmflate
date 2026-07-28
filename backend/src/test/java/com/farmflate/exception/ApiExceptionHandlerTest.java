package com.farmflate.exception;

import com.farmflate.service.assistant.AssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void formats_assistant_domain_errors_with_the_shared_error_envelope() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<Map<String, Object>> response = handler.handleApiException(
                new AssistantService.AssistantException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "해당 분석을 찾을 수 없습니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsKey("error");
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error).containsEntry("code", "ANALYSIS_NOT_FOUND")
                .containsEntry("message", "해당 분석을 찾을 수 없습니다.")
                .containsEntry("details", null)
                .containsEntry("retryable", false);
    }
}
