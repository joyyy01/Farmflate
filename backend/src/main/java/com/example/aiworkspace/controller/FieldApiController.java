package com.example.aiworkspace.controller;

import com.example.aiworkspace.dto.field.CreateFieldLogRequestDto;
import com.example.aiworkspace.dto.field.CreateFieldRequestDto;
import com.example.aiworkspace.dto.field.FieldActivityLogDto;
import com.example.aiworkspace.dto.field.FieldDashboardResponseDto;
import com.example.aiworkspace.dto.field.FieldProfileResponseDto;
import com.example.aiworkspace.dto.field.FieldSuitabilityPreviewDto;
import com.example.aiworkspace.dto.field.TaskAcknowledgementResponseDto;
import com.example.aiworkspace.security.UserPrincipal;
import com.example.aiworkspace.service.farm.FieldDashboardService;
import com.example.aiworkspace.service.farm.FieldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

/** Canonical My Farm API used by the front-end client. */
@RestController
@RequestMapping("/api/fields")
@RequiredArgsConstructor
public class FieldApiController {

    private final FieldService fieldService;
    private final FieldDashboardService fieldDashboardService;

    @GetMapping
    public ResponseEntity<List<FieldProfileResponseDto>> getFields(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(fieldService.getFields(ownerEmail(userPrincipal)));
    }

    @PostMapping
    public ResponseEntity<FieldProfileResponseDto> createField(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                 @RequestBody CreateFieldRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fieldService.create(ownerEmail(userPrincipal), request));
    }

    @PostMapping("/preview")
    public ResponseEntity<FieldSuitabilityPreviewDto> previewField(@AuthenticationPrincipal UserPrincipal userPrincipal,
                                                                  @RequestBody CreateFieldRequestDto request) {
        return ResponseEntity.ok(fieldService.preview(ownerEmail(userPrincipal), request));
    }

    @GetMapping("/{fieldId}/dashboard")
    public ResponseEntity<FieldDashboardResponseDto> getDashboard(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long fieldId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(fieldDashboardService.getDashboard(ownerEmail(userPrincipal), fieldId, date));
    }

    @PutMapping("/{fieldId}/daily-reports/{reportDate}/tasks/{taskKey}/acknowledgement")
    public ResponseEntity<TaskAcknowledgementResponseDto> acknowledgeTask(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long fieldId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @PathVariable String taskKey) {
        return ResponseEntity.ok(fieldDashboardService.acknowledgeTask(ownerEmail(userPrincipal), fieldId, reportDate, taskKey));
    }

    @PostMapping("/{fieldId}/logs")
    public ResponseEntity<FieldActivityLogDto> createLog(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long fieldId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateFieldLogRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fieldDashboardService.createLog(ownerEmail(userPrincipal), fieldId, idempotencyKey, request));
    }

    private String ownerEmail(UserPrincipal userPrincipal) {
        if (userPrincipal == null || userPrincipal.getEmail() == null || userPrincipal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return userPrincipal.getEmail();
    }
}
