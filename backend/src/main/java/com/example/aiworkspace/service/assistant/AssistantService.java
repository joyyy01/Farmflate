package com.example.aiworkspace.service.assistant;

import com.example.aiworkspace.controller.AssistantApiController.AssistantRequestDto;
import com.example.aiworkspace.domain.farm.FarmEntity;
import com.example.aiworkspace.domain.farm.FarmRepository;
import com.example.aiworkspace.domain.farm.FieldDailyReportRepository;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.dto.field.FieldAlertDto;
import com.example.aiworkspace.dto.field.FieldDailyReportDto;
import com.example.aiworkspace.dto.field.FieldTaskDto;
import com.example.aiworkspace.service.farm.FieldDailyReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {
    private static final Set<String> ALLOWED_HISTORY_ROLES = Set.of("user", "assistant");
    private static final int MAX_HISTORY_MESSAGES = 8;
    private static final int MAX_HISTORY_CONTENT_LENGTH = 1_200;

    private final RegionAnalysisRepository analysisRepository;
    private final FarmRepository farmRepository;
    private final FieldDailyReportRepository fieldDailyReportRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private RestTemplate restTemplate;

    @Value("${app.python-server.url:http://localhost:8000}")
    private String pythonServerUrl;

    @Value("${app.python-server.internal-api-key:farmflate-local-internal-key}")
    private String internalApiKey;

    @Value("${app.python-server.request-timeout-ms:15000}")
    private int requestTimeoutMs;

    /* @Value fields aren't populated yet during field initializers, so the
       timeout-configured RestTemplate has to be built after injection --
       previously this value was read but never actually applied, leaving the
       Python agent call with no timeout at all. */
    @PostConstruct
    private void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(requestTimeoutMs);
        factory.setReadTimeout(requestTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

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
            // Pass the agent's real status through instead of forcing
            // "completed" -- "fallback" means the LLM answer failed grounding
            // validation and a rule-based answer was substituted; masking
            // that made a degraded response indistinguishable from a normal one.
            response.putIfAbsent("status", "completed");
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
        putIfPresent(facts, "region.summary", payload.get("summary"));
        addReportComponentFacts(facts, payload);
        addDataConfidenceFacts(facts, payload);

        Object crops = payload.get("recommendedCrops");
        if (crops instanceof List) {
            List<Map<String, Object>> cropList = (List<Map<String, Object>>) crops;
            for (int i = 0; i < cropList.size(); i++) {
                Map<String, Object> crop = cropList.get(i);
                facts.put("crop." + (i + 1) + ".name", crop.getOrDefault("cropName", ""));
                facts.put("crop." + (i + 1) + ".score", crop.getOrDefault("score", null));
                putIfPresent(facts, "crop." + (i + 1) + ".baseFitness", crop.get("baseFitness"));
                putIfPresent(facts, "crop." + (i + 1) + ".seasonReadiness", crop.get("seasonReadiness"));
                putIfPresent(facts, "crop." + (i + 1) + ".caution", crop.get("cautionReason"));
                putFirstListValue(facts, "crop." + (i + 1) + ".reason.1", crop.get("positiveReasons"));
            }
        }

        Object risks = payload.get("topRisks");
        if (risks instanceof List) {
            List<Map<String, Object>> riskList = (List<Map<String, Object>>) risks;
            for (int i = 0; i < riskList.size(); i++) {
                Map<String, Object> risk = riskList.get(i);
                facts.put("risk." + (i + 1) + ".code", risk.getOrDefault("riskCode", ""));
                facts.put("risk." + (i + 1) + ".title", risk.getOrDefault("title", ""));
                putIfPresent(facts, "risk." + (i + 1) + ".severity", risk.get("severity"));
                putIfPresent(facts, "risk." + (i + 1) + ".description", risk.get("description"));
                putFirstListValue(facts, "risk." + (i + 1) + ".cause.1", risk.get("causalChain"));
                Object actions = risk.get("actions");
                putFirstListValue(facts, "risk." + (i + 1) + ".action.1", actions);
            }
        }

        injectFieldFacts(facts, email, request);

        Object srcList = payload.get("sources");
        if (srcList instanceof List) {
            int sourceIndex = 0;
            for (Object src : (List<?>) srcList) {
                if (src instanceof Map) {
                    Map<String, Object> srcMap = (Map<String, Object>) src;
                    Map<String, Object> source = new LinkedHashMap<>();
                    String provider = String.valueOf(srcMap.getOrDefault("provider", ""));
                    String service = String.valueOf(srcMap.getOrDefault("service", ""));
                    source.put("sourceId", "source." + (++sourceIndex));
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
        factPackage.put("history", sanitizeHistory(request.getHistory()));
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
    private void addReportComponentFacts(Map<String, Object> facts, Map<String, Object> payload) {
        Object componentsValue = payload.get("components");
        if (!(componentsValue instanceof Map<?, ?> components)) return;
        addComponentFacts(facts, "component.climate", (Map<String, Object>) components.get("climate"));
        addComponentFacts(facts, "component.soil", (Map<String, Object>) components.get("soil"));
        addComponentFacts(facts, "component.hazard", (Map<String, Object>) components.get("hazard"));
        addComponentFacts(facts, "component.cultivation", (Map<String, Object>) components.get("cultivation"));
    }

    private void addComponentFacts(Map<String, Object> facts, String prefix, Map<String, Object> component) {
        if (component == null) return;
        putIfPresent(facts, prefix + ".score", component.get("score"));
        putIfPresent(facts, prefix + ".safetyScore", component.get("safetyScore"));
        putIfPresent(facts, prefix + ".grade", component.get("grade"));
        putIfPresent(facts, prefix + ".description", component.get("description"));
        putIfPresent(facts, prefix + ".soilPh", component.get("soilPh"));
        putIfPresent(facts, prefix + ".soilEc", component.get("soilEc"));
    }

    @SuppressWarnings("unchecked")
    private void addDataConfidenceFacts(Map<String, Object> facts, Map<String, Object> payload) {
        Object confidenceValue = payload.get("dataConfidence");
        if (confidenceValue instanceof Map<?, ?> confidence) {
            putIfPresent(facts, "data.confidence.level", ((Map<String, Object>) confidence).get("level"));
            putIfPresent(facts, "data.confidence.score", ((Map<String, Object>) confidence).get("score"));
            putIfPresent(facts, "data.confidence.message", ((Map<String, Object>) confidence).get("message"));
        }
        Object missingValue = payload.get("missingMetrics");
        if (missingValue instanceof List<?> missing && !missing.isEmpty()) {
            facts.put("data.missing.1", String.valueOf(missing.get(0)));
            facts.put("data.missing.count", missing.size());
        }
    }

    private void putIfPresent(Map<String, Object> facts, String key, Object value) {
        if (value != null && !(value instanceof String valueString && valueString.isBlank())) facts.put(key, value);
    }

    private void putFirstListValue(Map<String, Object> facts, String key, Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) putIfPresent(facts, key, list.get(0));
    }

    static List<Map<String, String>> sanitizeHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return List.of();
        List<Map<String, String>> sanitized = new ArrayList<>();
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (Map<String, String> message : history.subList(start, history.size())) {
            if (message == null) continue;
            String role = message.get("role");
            String content = message.get("content");
            if (!ALLOWED_HISTORY_ROLES.contains(role) || content == null || content.isBlank()) continue;
            String normalized = content.trim();
            if (normalized.length() > MAX_HISTORY_CONTENT_LENGTH) normalized = normalized.substring(0, MAX_HISTORY_CONTENT_LENGTH);
            sanitized.add(Map.of("role", role, "content", normalized));
        }
        return List.copyOf(sanitized);
    }

    /**
     * Only ever reads an already-generated DAILY_0630 snapshot the caller
     * owns — never triggers a new report generation and never trusts a
     * fieldId/date the requesting user doesn't own.
     */
    private void injectFieldFacts(Map<String, Object> facts, String email, AssistantRequestDto request) {
        if (request.getContext() == null || request.getContext().getFieldId() == null
                || request.getContext().getFieldId().isBlank()) {
            return;
        }
        Long fieldId;
        try {
            fieldId = Long.valueOf(request.getContext().getFieldId());
        } catch (NumberFormatException exception) {
            return;
        }
        FarmEntity field = farmRepository.findByIdAndUserEmail(fieldId, email).orElse(null);
        if (field == null) return;

        LocalDate reportDate = LocalDate.now(clock);
        String requestedDate = request.getContext().getReportDate();
        if (requestedDate != null && !requestedDate.isBlank()) {
            try {
                LocalDate parsed = LocalDate.parse(requestedDate);
                if (!parsed.isAfter(LocalDate.now(clock))) reportDate = parsed;
            } catch (Exception ignored) {
                // Keep today's date rather than trusting an unparsable client value.
            }
        }

        fieldDailyReportRepository
                .findFirstByFarmIdAndOwnerEmailAndReportDateAndGenerationReasonOrderByGeneratedAtDesc(
                        fieldId, email, reportDate, FieldDailyReportService.GENERATION_REASON)
                .ifPresent(entity -> {
                    try {
                        FieldDailyReportDto report = objectMapper.readValue(entity.getPayloadJson(), FieldDailyReportDto.class);
                        facts.put("field.name", field.getFieldName());
                        facts.put("field.crop.name", field.getCropName());
                        facts.put("field.report.date", report.getReportDate());
                        putIfPresent(facts, "field.status", report.getStatus());
                        putIfPresent(facts, "field.score", report.getSuitabilityScore());
                        putIfPresent(facts, "field.headline", report.getHeadline());
                        putIfPresent(facts, "field.headlineDescription", report.getHeadlineDescription());
                        if (report.getWeather() != null) {
                            facts.put("field.weather.minTemperature", report.getWeather().getMinTemperature());
                            facts.put("field.weather.maxTemperature", report.getWeather().getMaxTemperature());
                            facts.put("field.weather.humidity", report.getWeather().getHumidity());
                            facts.put("field.weather.rainfall", report.getWeather().getRainfallMm());
                        }
                        List<FieldTaskDto> tasks = report.getTasks();
                        if (tasks != null && !tasks.isEmpty()) {
                            facts.put("field.task.1.title", tasks.get(0).getTitle());
                            putIfPresent(facts, "field.task.1.description", tasks.get(0).getDescription());
                        }
                        List<FieldAlertDto> alerts = report.getAlerts();
                        if (alerts != null && !alerts.isEmpty()) {
                            facts.put("field.alert.1.title", alerts.get(0).getTitle());
                            putIfPresent(facts, "field.alert.1.severity", alerts.get(0).getSeverity());
                            putIfPresent(facts, "field.alert.1.description", alerts.get(0).getDescription());
                        }
                        if (report.getReasoning() != null && report.getReasoning().getPoints() != null
                                && !report.getReasoning().getPoints().isEmpty()) {
                            facts.put("field.reasoning.1", report.getReasoning().getPoints().get(0));
                        }
                    } catch (Exception exception) {
                        log.warn("Unable to parse field daily report for AI fact injection: {}", entity.getId());
                    }
                });
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
