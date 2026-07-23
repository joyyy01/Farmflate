package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.user.User;
import com.example.aiworkspace.domain.user.UserRepository;
import com.example.aiworkspace.dto.region.*;
import com.example.aiworkspace.security.UserPrincipal;
import com.example.aiworkspace.service.analysis.RegionAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/region-analyses")
    public ResponseEntity<RegionAnalysisStatusDto> createAnalysis(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody RegionAnalysisRequestDto request) {
        String email = userPrincipal != null ? userPrincipal.getEmail() : "guest_" + System.currentTimeMillis();
        return ResponseEntity.ok(regionAnalysisService.createAnalysis(email, request));
    }

    @GetMapping("/region-analyses/{analysisId}/status")
    public ResponseEntity<RegionAnalysisStatusDto> getStatus(@PathVariable String analysisId) {
        return ResponseEntity.ok(regionAnalysisService.getStatus(analysisId));
    }

    @GetMapping("/region-analyses/{analysisId}")
    public ResponseEntity<RegionReportResponseDto> getReport(@PathVariable String analysisId) {
        return ResponseEntity.ok(regionAnalysisService.getReport(analysisId));
    }
}
