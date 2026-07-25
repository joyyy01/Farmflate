package com.example.aiworkspace.service.assistant;

import com.example.aiworkspace.controller.AssistantApiController.AssistantRequestDto;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final RegionAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.python-server.url:http://localhost:8000}")
    private String pythonServerUrl;

    @Value("${app.python-server.internal-api-key:farmflate-local-internal-key}")
    private String internalApiKey;

    @Value("${app.python-server.request-timeout-ms:15000}")
    private int requestTimeoutMs;

    @SuppressWarnings("unchecked")
    public Map<String, Object> processMessage(String email, AssistantRequestDto request) {
        String analysisId = request.getContext() != null ? request.getContext().getRegionAnalysisId() : null;

        RegionAnalysisEntity entity = null;
        Map<String, Object> payload = Map.of();
        if (analysisId != null && !analysisId.isBlank()) {
            entity = analysisRepository.findByIdAndUserEmail(analysisId, email)
                    .orElseThrow(() -> new AssistantException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "해당 분석을 찾을 수 없습니다."));
            if (entity.getPayloadJson() != null && !entity.getPayloadJson().isBlank()) {
                try {
                    payload = objectMapper.readValue(entity.getPayloadJson(), Map.class);
                } catch (Exception e) {
                    log.warn("Failed to parse payload for analysis {}", analysisId, e);
                }
            }
        }

        Map<String, Object> factPackage = buildFactPackage(email, request, payload);

        try {
            Map<String, Object> pythonResponse = callPythonAgent(factPackage);
            return validateAndReshape(pythonResponse, factPackage);
        } catch (Exception e) {
            log.warn("Python AI call failed, returning fallback", e);
            return buildFallbackResponse(factPackage);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateAndReshape(Map<String, Object> response, Map<String, Object> factPackage) {
        if (response == null || !response.containsKey("answer")) {
            return buildFallbackResponse(factPackage);
        }
        Object answerObj = response.get("answer");
        if (answerObj instanceof String) {
            Map<String, Object> reshaped = new LinkedHashMap<>(response);
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("answer", answerObj);
            structured.put("basisType", "CURRENT_REPORT");
            structured.put("usedFactIds", List.of());
            structured.put("usedSourceIds", List.of());
            structured.put("mentionedNumbers", List.of());
            structured.put("mentionedCrops", List.of());
            structured.put("mentionedRisks", List.of());
            structured.put("safetyNotice", null);
            reshaped.put("answer", structured);
            reshaped.putIfAbsent("requestId", factPackage.get("requestId"));
            reshaped.putIfAbsent("sources", factPackage.getOrDefault("sources", List.of()));
            return reshaped;
        }
        if (answerObj instanceof Map) {
            Map<String, Object> answer = (Map<String, Object>) answerObj;
            if (!answer.containsKey("answer") || answer.get("answer") == null) {
                return buildFallbackResponse(factPackage);
            }
            response.putIfAbsent("requestId", factPackage.get("requestId"));
            response.putIfAbsent("sources", factPackage.getOrDefault("sources", List.of()));
            if (!"completed".equals(response.get("status"))) {
                response.put("status", "completed");
            }
            return response;
        }
        return buildFallbackResponse(factPackage);
    }

    private Map<String, Object> buildFactPackage(String email, AssistantRequestDto request, Map<String, Object> payload) {
        Map<String, Object> facts = new LinkedHashMap<>();
        List<Map<String, Object>> sources = new ArrayList<>();

        if (payload.containsKey("region")) {
            Object region = payload.get("region");
            if (region instanceof Map) {
                Map<String, Object> regionMap = (Map<String, Object>) region;
                String sidoName = String.valueOf(regionMap.getOrDefault("sidoName", ""));
                String sigunguName = String.valueOf(regionMap.getOrDefault("sigunguName", ""));
                facts.put("region.name", (sidoName + " " + sigunguName).trim());
            }
        }
        if (payload.containsKey("regionScore")) facts.put("region.score", payload.get("regionScore"));
        if (payload.containsKey("grade")) facts.put("region.grade", payload.get("grade"));

        Object crops = payload.get("recommendedCrops");
        if (crops instanceof List) {
            List<Map<String, Object>> cropList = (List<Map<String, Object>>) crops;
            for (int i = 0; i < cropList.size(); i++) {
                Map<String, Object> crop = cropList.get(i);
                facts.put("crop." + (i + 1) + ".name", crop.getOrDefault("cropName", ""));
                facts.put("crop." + (i + 1) + ".score", crop.getOrDefault("score", null));
            }
        }

        Object risks = payload.get("topRisks");
        if (risks instanceof List) {
            List<Map<String, Object>> riskList = (List<Map<String, Object>>) risks;
            for (int i = 0; i < riskList.size(); i++) {
                Map<String, Object> risk = riskList.get(i);
                facts.put("risk." + (i + 1) + ".code", risk.getOrDefault("riskCode", ""));
                facts.put("risk." + (i + 1) + ".title", risk.getOrDefault("title", ""));
                Object actions = risk.get("actions");
                if (actions instanceof List && !((List<?>) actions).isEmpty()) {
                    facts.put("risk." + (i + 1) + ".action.1", ((List<?>) actions).get(0));
                }
            }
        }

        Object srcList = payload.get("sources");
        if (srcList instanceof List) {
            for (Object src : (List<?>) srcList) {
                if (src instanceof Map) {
                    Map<String, Object> srcMap = (Map<String, Object>) src;
                    Map<String, Object> source = new LinkedHashMap<>();
                    String provider = String.valueOf(srcMap.getOrDefault("provider", ""));
                    String service = String.valueOf(srcMap.getOrDefault("service", ""));
                    source.put("sourceId", "source." + provider);
                    source.put("title", provider + " " + service);
                    source.put("detail", service);
                    source.put("provider", provider);
                    source.put("service", service);
                    source.put("observedAt", srcMap.getOrDefault("dataDate", ""));
                    source.put("sourceUrl", srcMap.getOrDefault("sourceUrl", ""));
                    sources.add(source);
                }
            }
        }

        Map<String, Object> factPackage = new LinkedHashMap<>();
        factPackage.put("requestId", UUID.randomUUID().toString());
        factPackage.put("userScope", Map.of("userId", email));
        factPackage.put("question", request.getMessage());
        factPackage.put("history", request.getHistory() != null ? request.getHistory() : List.of());
        factPackage.put("context", Map.of(
                "route", request.getContext() != null && request.getContext().getRoute() != null ? request.getContext().getRoute() : "ai_chat",
                "regionAnalysisId", request.getContext() != null && request.getContext().getRegionAnalysisId() != null ? request.getContext().getRegionAnalysisId() : "",
                "fieldId", request.getContext() != null && request.getContext().getFieldId() != null ? request.getContext().getFieldId() : ""
        ));
        factPackage.put("facts", facts);
        factPackage.put("sources", sources);

        return factPackage;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callPythonAgent(Map<String, Object> factPackage) {
        String url = pythonServerUrl + "/api/v1/agent/run";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Farmflate-Internal-Key", internalApiKey);
        headers.set("X-Request-Id", String.valueOf(factPackage.get("requestId")));

        Map<String, Object> body = Map.of("fact_package", factPackage);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        }
        throw new RuntimeException("Python AI returned " + response.getStatusCode());
    }

    private Map<String, Object> buildFallbackResponse(Map<String, Object> factPackage) {
        Map<String, Object> facts = (Map<String, Object>) factPackage.getOrDefault("facts", Map.of());
        StringBuilder sb = new StringBuilder();
        List<String> usedFacts = new ArrayList<>();

        Object score = facts.get("region.score");
        Object grade = facts.get("region.grade");
        if (score != null) {
            sb.append("현재 리포트의 지역 점수는 ").append(score).append("점");
            if (grade != null) sb.append("(").append(grade).append(")");
            sb.append("입니다.\n");
            usedFacts.add("region.score");
        }

        Object crop1Name = facts.get("crop.1.name");
        Object crop1Score = facts.get("crop.1.score");
        if (crop1Name != null) {
            sb.append("추천 작물 1순위는 ").append(crop1Name);
            if (crop1Score != null) sb.append("(").append(crop1Score).append("점)");
            sb.append("입니다.\n");
            usedFacts.add("crop.1.name");
        }
        Object crop2Name = facts.get("crop.2.name");
        Object crop2Score = facts.get("crop.2.score");
        if (crop2Name != null) {
            sb.append("2순위는 ").append(crop2Name);
            if (crop2Score != null) sb.append("(").append(crop2Score).append("점)");
            sb.append(", ");
            Object crop3Name = facts.get("crop.3.name");
            Object crop3Score = facts.get("crop.3.score");
            if (crop3Name != null) {
                sb.append("3순위는 ").append(crop3Name);
                if (crop3Score != null) sb.append("(").append(crop3Score).append("점)");
            }
            sb.append("입니다.\n");
            usedFacts.add("crop.2.name");
        }

        Object riskTitle = facts.get("risk.1.title");
        if (riskTitle != null) {
            sb.append("가장 큰 위험은 ").append(riskTitle).append("입니다.\n");
            usedFacts.add("risk.1.title");
            Object action = facts.get("risk.1.action.1");
            if (action != null) {
                sb.append("권장 행동은 ").append(action).append("입니다.\n");
                usedFacts.add("risk.1.action.1");
            }
        } else {
            sb.append("현재 기상 예보에서 확인된 주요 위험은 없습니다.\n");
        }

        if (score == null && crop1Name == null) {
            sb.setLength(0);
            sb.append("현재 분석 데이터가 부족합니다. 지역 분석을 먼저 완료해주세요.");
        }

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("answer", sb.toString().trim());
        answer.put("basisType", "CURRENT_REPORT");
        answer.put("usedFactIds", usedFacts);
        List<Map<String, Object>> pkgSources = (List<Map<String, Object>>) factPackage.getOrDefault("sources", List.of());
        List<String> sourceIds = pkgSources.stream()
                .map(s -> String.valueOf(s.getOrDefault("sourceId", "")))
                .filter(id -> !id.isEmpty())
                .toList();

        answer.put("usedSourceIds", sourceIds);
        answer.put("mentionedNumbers", List.of());
        answer.put("mentionedCrops", List.of());
        answer.put("mentionedRisks", riskTitle != null ? List.of(riskTitle) : List.of());
        answer.put("safetyNotice", null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", factPackage.get("requestId"));
        result.put("status", "completed");
        result.put("answer", answer);
        result.put("sources", pkgSources);
        return result;
    }

    public static class AssistantException extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        public AssistantException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public HttpStatus getStatus() { return status; }
        public String getCode() { return code; }
    }
}
