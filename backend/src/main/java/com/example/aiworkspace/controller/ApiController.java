package com.example.aiworkspace.controller;

import com.example.aiworkspace.dto.ApiResponseDto;
import com.example.aiworkspace.service.ApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ApiService apiService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponseDto<Map<String, Object>>> getHealthStatus() {
        return ResponseEntity.ok(
                ApiResponseDto.success("Spring Boot Service Healthy", apiService.getSystemStatus())
        );
    }
}
