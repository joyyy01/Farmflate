package com.farmflate.controller;

import com.farmflate.dto.region.*;
import com.farmflate.security.UserPrincipal;
import com.farmflate.service.analysis.RegionAnalysisService;
import com.farmflate.service.user.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RegionApiController {

    private final RegionAnalysisService regionAnalysisService;
    private final UserProfileService userProfileService;

    private String requireEmail(UserPrincipal principal) {
        if (principal == null || principal.getEmail() == null || principal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return principal.getEmail();
    }

    @GetMapping("/home")
    public ResponseEntity<HomeResponseDto> getHome(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String email = requireEmail(userPrincipal);
        String displayName = userProfileService.getDisplayName(email, "Farmflate 사용자");
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
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody RegionAnalysisRequestDto request) {
        String email = requireEmail(userPrincipal);
        return ResponseEntity.ok(regionAnalysisService.create(email, request));
    }

    @GetMapping("/regions/analysis/{analysisId}/status")
    public ResponseEntity<RegionAnalysisStatusDto> getStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID analysisId) {
        String email = requireEmail(userPrincipal);
        return ResponseEntity.ok(regionAnalysisService.getStatus(email, analysisId));
    }

    @GetMapping("/regions/reports/{analysisId}")
    public ResponseEntity<RegionReportResponseDto> getReport(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID analysisId) {
        String email = requireEmail(userPrincipal);
        return ResponseEntity.ok(regionAnalysisService.getReport(email, analysisId));
    }
}
