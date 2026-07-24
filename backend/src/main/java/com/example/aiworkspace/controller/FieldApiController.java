package com.example.aiworkspace.controller;

import com.example.aiworkspace.dto.field.CreateFieldRequestDto;
import com.example.aiworkspace.dto.field.FieldProfileResponseDto;
import com.example.aiworkspace.service.farm.FieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/** Canonical My Farm API used by the front-end client. */
@RestController
@RequestMapping("/api/fields")
@RequiredArgsConstructor
public class FieldApiController {

    private final FieldService fieldService;

    @GetMapping
    public ResponseEntity<List<FieldProfileResponseDto>> getFields(Principal principal) {
        return ResponseEntity.ok(fieldService.getFields(principal.getName()));
    }

    @PostMapping
    public ResponseEntity<FieldProfileResponseDto> createField(Principal principal,
                                                                @RequestBody CreateFieldRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fieldService.create(principal.getName(), request));
    }

    @ExceptionHandler(FieldService.FieldException.class)
    public ResponseEntity<ApiErrorResponse> handleFieldException(FieldService.FieldException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    private record ApiErrorResponse(String code, String message) {
    }
}
