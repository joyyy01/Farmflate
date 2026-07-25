package com.example.aiworkspace.controller;

import com.example.aiworkspace.dto.field.CreateFieldRequestDto;
import com.example.aiworkspace.dto.field.FieldProfileResponseDto;
import com.example.aiworkspace.dto.field.FieldSuitabilityPreviewDto;
import com.example.aiworkspace.security.UserPrincipal;
import com.example.aiworkspace.service.farm.FieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Canonical My Farm API used by the front-end client. */
@RestController
@RequestMapping("/api/fields")
@RequiredArgsConstructor
public class FieldApiController {

    private final FieldService fieldService;

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

    @ExceptionHandler(FieldService.FieldException.class)
    public ResponseEntity<ApiErrorResponse> handleFieldException(FieldService.FieldException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    private record ApiErrorResponse(String code, String message) {
    }

    private String ownerEmail(UserPrincipal userPrincipal) {
        if (userPrincipal == null || userPrincipal.getEmail() == null || userPrincipal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return userPrincipal.getEmail();
    }
}
