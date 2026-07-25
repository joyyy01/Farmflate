package com.example.aiworkspace.controller;

import com.example.aiworkspace.security.UserPrincipal;
import com.example.aiworkspace.service.assistant.AssistantService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantApiController {

    private final AssistantService assistantService;

    private String requireEmail(UserPrincipal principal) {
        if (principal == null || principal.getEmail() == null || principal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return principal.getEmail();
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody AssistantRequestDto request) {

        String email = requireEmail(userPrincipal);

        if (request.getMessage() == null || request.getMessage().isBlank() || request.getMessage().length() > 1200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "메시지는 1~1200자여야 합니다.");
        }

        try {
            Map<String, Object> response = assistantService.processMessage(email, request);
            return ResponseEntity.ok(response);
        } catch (AssistantService.AssistantException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of(
                    "code", e.getCode(),
                    "message", e.getMessage()
            ));
        }
    }

    @Data
    public static class AssistantRequestDto {
        private String message;
        private List<Map<String, String>> history;
        private AssistantContextDto context;
    }

    @Data
    public static class AssistantContextDto {
        private String regionAnalysisId;
        private String fieldId;
        private String route;
    }
}
