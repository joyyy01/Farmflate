package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.user.User;
import com.example.aiworkspace.domain.user.UserRepository;
import com.example.aiworkspace.dto.region.*;
import com.example.aiworkspace.security.UserPrincipal;
import com.example.aiworkspace.service.analysis.RegionAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegionApiController {

    private final RegionAnalysisService regionAnalysisService;
    private final UserRepository userRepository;

    @GetMapping("/home")
    public ResponseEntity<HomeResponseDto> getHome(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.ok(HomeResponseDto.builder()
                    .user(HomeResponseDto.UserDto.builder().displayName("Farmflate 사용자").build())
                    .weather(HomeResponseDto.WeatherDto.builder().status("UNAVAILABLE").build())
                    .todayAction(null)
                    .latestRegionAnalysis(null)
                    .farms(List.of())
                    .build());
        }

        String email = userPrincipal.getEmail();
        User user = userRepository.findByEmail(email).orElse(null);
        String displayName = (user != null && user.getNickname() != null && !user.getNickname().isBlank())
                ? user.getNickname()
                : "Farmflate 사용자";

        return ResponseEntity.ok(regionAnalysisService.getHome(email, displayName));
    }

    @GetMapping("/regions/sidos")
    public ResponseEntity<List<RegionDto>> getSidos() {
        return ResponseEntity.ok(regionAnalysisService.getSidos());
    }

    @GetMapping("/regions/sidos/{sidoCode}/sigungus")
    public ResponseEntity<List<RegionDto>> getSigungus(@PathVariable String sidoCode) {
        return ResponseEntity.ok(regionAnalysisService.getSigungus(sidoCode));
    }

    @PostMapping("/regions/analysis")
    public ResponseEntity<RegionAnalysisStatusDto> create(
            Principal principal,
            @RequestBody RegionAnalysisRequestDto request) {
        validateRequest(request);
        return ResponseEntity.ok(regionAnalysisService.create(principal.getName(), request));
    }

    @GetMapping("/regions/analysis/{analysisId}/status")
    public ResponseEntity<RegionAnalysisStatusDto> getStatus(
            Principal principal,
            @PathVariable UUID analysisId) {
        return ResponseEntity.ok(regionAnalysisService.getStatus(principal.getName(), analysisId));
    }

    @GetMapping("/regions/reports/{analysisId}")
    public ResponseEntity<RegionReportResponseDto> getReport(
            Principal principal,
            @PathVariable UUID analysisId) {
        return ResponseEntity.ok(regionAnalysisService.getReport(principal.getName(), analysisId));
    }

    @ExceptionHandler(RegionAnalysisService.RegionAnalysisException.class)
    public ResponseEntity<ApiErrorResponse> handleRegionAnalysisException(
            RegionAnalysisService.RegionAnalysisException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(new ApiErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRegionRequest(Exception exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_REGION_REQUEST", "요청한 지역 분석 정보가 올바르지 않습니다."));
    }

    private record ApiErrorResponse(String code, String message) {
    }

    private void validateRequest(RegionAnalysisRequestDto request) {
        if (request == null
                || !hasTextWithin(request.getSidoCode(), 20)
                || !hasTextWithin(request.getSidoName(), 100)
                || !hasTextWithin(request.getSigunguCode(), 20)
                || !hasTextWithin(request.getSigunguName(), 100)
                || !hasTextWithin(request.getIdempotencyKey(), 128)
                || request.getForceRefresh() == null) {
            throw RegionAnalysisService.RegionAnalysisException.invalidRequest();
        }
    }

    private boolean hasTextWithin(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }
}
