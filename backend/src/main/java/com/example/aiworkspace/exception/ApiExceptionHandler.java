package com.example.aiworkspace.exception;

import com.example.aiworkspace.service.analysis.RegionAnalysisService;
import com.example.aiworkspace.service.farm.FieldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error the API returns is shaped {error:{code,message,details,retryable}}
 * so the frontend never has to special-case a raw Spring error body or a
 * plain 500 with no code.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException exception) {
        return body(exception.getStatus(), exception.getCode(), exception.getMessage(), null, exception.isRetryable());
    }

    @ExceptionHandler(FieldService.FieldException.class)
    public ResponseEntity<Map<String, Object>> handleFieldException(FieldService.FieldException exception) {
        return body(exception.getHttpStatus(), exception.getCode(), exception.getMessage(), null, false);
    }

    @ExceptionHandler(RegionAnalysisService.RegionAnalysisException.class)
    public ResponseEntity<Map<String, Object>> handleRegionAnalysisException(RegionAnalysisService.RegionAnalysisException exception) {
        return body(exception.getHttpStatus(), exception.getCode(), exception.getMessage(), null, exception.isRetryable());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(
                error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해 주세요.", fieldErrors, false);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception exception) {
        return body(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 정보가 올바르지 않습니다.", null, false);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException exception) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "COMMUNITY_ATTACHMENT_TOO_LARGE", "첨부 파일이 너무 큽니다.", null, false);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = status.name();
        String message = exception.getReason() != null ? exception.getReason() : "요청을 처리하지 못했습니다.";
        return body(status, code, message, null, status.is5xxServerError());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", null, true);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message, Object details, boolean retryable) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", details);
        error.put("retryable", retryable);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", error);
        return ResponseEntity.status(status).body(response);
    }
}
