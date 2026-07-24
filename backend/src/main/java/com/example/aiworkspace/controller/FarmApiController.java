package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.farm.FarmEntity;
import com.example.aiworkspace.domain.farm.FarmRepository;
import com.example.aiworkspace.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmApiController {

    private final FarmRepository farmRepository;

    @GetMapping
    public ResponseEntity<List<FarmEntity>> getMyFarms(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String userEmail = userPrincipal != null ? userPrincipal.getEmail() : "user@farmflate.com";
        List<FarmEntity> farms = farmRepository.findByUserEmailOrderByCreatedAtDesc(userEmail);
        return ResponseEntity.ok(farms);
    }

    @PostMapping
    public ResponseEntity<FarmEntity> createFarm(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CreateFarmRequest request) {

        String userEmail = userPrincipal != null ? userPrincipal.getEmail() : "user@farmflate.com";

        FarmEntity farm = FarmEntity.builder()
                .userEmail(userEmail)
                .fieldName(request.getFieldName() != null ? request.getFieldName() : request.getCropName() + "밭")
                .cropName(request.getCropName())
                .daysPlanted(request.getDaysPlanted() > 0 ? request.getDaysPlanted() : 1)
                .stage(request.getStage() != null ? request.getStage() : "생장 초기")
                .statusBadge("물주기 필요")
                .statusBadgeColor("yellow")
                .todayTask(request.getCropName() + "밭 토양 수분 체크 및 물주기")
                .reportTime("방금 전 자동 분석됨")
                .build();

        FarmEntity saved = farmRepository.save(farm);
        return ResponseEntity.ok(saved);
    }

    @Data
    public static class CreateFarmRequest {
        private String fieldName;
        private String cropName;
        private int daysPlanted;
        private String stage;
    }
}
