package com.farmflate.service.assistant;

import com.farmflate.controller.AssistantApiController.AssistantRequestDto;
import com.farmflate.controller.AssistantApiController.VisibleDataRefDto;
import com.farmflate.exception.ApiException;
import com.farmflate.domain.farm.FarmEntity;
import com.farmflate.domain.farm.FarmRepository;
import com.farmflate.domain.farm.FieldDailyReportRepository;
import com.farmflate.domain.region.RegionAnalysisEntity;
import com.farmflate.domain.region.RegionAnalysisRepository;
import com.farmflate.dto.field.FieldAlertDto;
import com.farmflate.dto.field.FieldDailyReportDto;
import com.farmflate.dto.field.FieldTaskDto;
import com.farmflate.service.field.FieldDailyReportService;
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
    private static final int MAX_VISIBLE_DATA_REFS = 12;
    private static final int MAX_VISIBLE_LABEL_LENGTH = 80;
    private static final String AGENT_UNAVAILABLE_MESSAGE = "현재 검증 가능한 답변을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    private static final Set<String> ALLOWED_VISIBLE_SECTIONS = Set.of(
            "summary", "climate", "soil", "hazard", "crop", "field");
    private static final List<String> REGION_FACT_KEY_PREFIXES = List.of(
            "region.", "component.", "crop.", "risk.", "data.", "field.soil.");
    private static final List<String> DAILY_FIELD_FACT_KEY_PREFIXES = List.of(
            "field.name", "field.crop.", "field.report.", "field.status", "field.score",
            "field.headline", "field.reasoning.", "field.weather.", "field.task.", "field.alert.");

    private final RegionAnalysisRepository analysisRepository;
    private final FarmRepository farmRepository;
    private final FieldDailyReportRepository fieldDailyReportRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private RestTemplate restTemplate;

    @Value("${app.python-server.url:http://localhost:8000}")
    private String pythonServerUrl;

    @Value("${app.python-server.internal-api-key:}")
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
            log.warn("python_agent_unavailable requestId={} errorType={}",
                    factPackage.get("requestId"), e.getClass().getSimpleName());
            return buildAgentUnavailableResponse(factPackage);
        }
    }

    private Map<String, Object> validateAndReshape(Map<String, Object> response, Map<String, Object> factPackage) {
        if (response == null) return buildAgentUnavailableResponse(factPackage);

        Map<String, Object> answer = objectMap(response.get("answer"));
        Object rawStatus = response.get("status");
        String status = rawStatus instanceof String value ? value : "";
        if (!Set.of("completed", "needs_context").contains(status)
                || !(answer.get("answer") instanceof String text) || text.isBlank()) {
            return buildAgentUnavailableResponse(factPackage);
        }
        if ("completed".equals(status)
                && (!hasEntries(response.get("sources")) || !hasNonBlankStrings(answer.get("usedSourceIds")))) {
            return buildAgentUnavailableResponse(factPackage);
        }

        Map<String, Object> reshaped = new LinkedHashMap<>(response);
        reshaped.put("requestId", factPackage.get("requestId"));
        reshaped.put("answer", answer);
        if ("needs_context".equals(status)) {
            reshaped.put("sources", List.of());
            answer.put("usedSourceIds", List.of());
        }
        return reshaped;
    }

    private boolean hasEntries(Object value) {
        return value instanceof List<?> entries && !entries.isEmpty();
    }

    private boolean hasNonBlankStrings(Object value) {
        return value instanceof List<?> values
                && values.stream().anyMatch(entry -> entry instanceof String text && !text.isBlank());
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
        if (hasFactPrefix(facts, REGION_FACT_KEY_PREFIXES)) {
            sources.add(factSource("region.report", "저장된 지역 분석", REGION_FACT_KEY_PREFIXES));
        }
        if (hasFactPrefix(facts, DAILY_FIELD_FACT_KEY_PREFIXES)) {
            sources.add(factSource("field.snapshot", "필지 일일 리포트", DAILY_FIELD_FACT_KEY_PREFIXES));
        }

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
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("route", request.getContext() != null && request.getContext().getRoute() != null ? request.getContext().getRoute() : "ai_chat");
        context.put("regionAnalysisId", request.getContext() != null && request.getContext().getRegionAnalysisId() != null ? request.getContext().getRegionAnalysisId() : "");
        context.put("fieldId", request.getContext() != null && request.getContext().getFieldId() != null ? request.getContext().getFieldId() : "");
        context.put("visibleData", sanitizeVisibleData(request.getContext() != null ? request.getContext().getVisibleData() : null));
        factPackage.put("context", context);
        factPackage.put("facts", facts);
        factPackage.put("sources", sources);

        return factPackage;
    }

    private boolean hasFactPrefix(Map<String, Object> facts, List<String> prefixes) {
        return facts.keySet().stream().anyMatch(key -> prefixes.stream().anyMatch(key::startsWith));
    }

    private Map<String, Object> factSource(String sourceId, String title, List<String> factKeyPrefixes) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceId", sourceId);
        source.put("title", title);
        source.put("factKeyPrefixes", factKeyPrefixes);
        return source;
    }

    private void addReportComponentFacts(Map<String, Object> facts, Map<String, Object> payload) {
        Map<String, Object> components = objectMap(payload.get("components"));
        if (components.isEmpty()) return;
        addComponentFacts(facts, "component.climate", objectMap(components.get("climate")));
        addComponentFacts(facts, "component.soil", objectMap(components.get("soil")));
        addComponentFacts(facts, "component.hazard", objectMap(components.get("hazard")));
        addComponentFacts(facts, "component.cultivation", objectMap(components.get("cultivation")));
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, entry) -> {
            if (key instanceof String name) normalized.put(name, entry);
        });
        return normalized;
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

    static List<Map<String, String>> sanitizeVisibleData(List<VisibleDataRefDto> visibleData) {
        if (visibleData == null || visibleData.isEmpty()) return List.of();
        List<Map<String, String>> sanitized = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (VisibleDataRefDto ref : visibleData) {
            if (ref == null || sanitized.size() >= MAX_VISIBLE_DATA_REFS) continue;
            String key = ref.getKey() == null ? "" : ref.getKey().trim();
            String label = ref.getLabel() == null ? "" : ref.getLabel().trim();
            String section = ref.getSection() == null ? "" : ref.getSection().trim();
            if (!isAllowedVisibleFactKey(key) || label.isBlank() || label.length() > MAX_VISIBLE_LABEL_LENGTH
                    || !ALLOWED_VISIBLE_SECTIONS.contains(section) || !seenKeys.add(key)) {
                continue;
            }
            sanitized.add(Map.of("key", key, "label", label, "section", section));
        }
        return List.copyOf(sanitized);
    }

    private static boolean isAllowedVisibleFactKey(String key) {
        return key.matches("region\\.(score|grade|summary)")
                || key.matches("component\\.(climate|soil|hazard|cultivation)\\.(score|safetyScore|grade|description|soilPh|soilEc)")
                || key.matches("crop\\.[1-5](\\.(name|score|baseFitness|seasonReadiness|caution|reason\\.1))?")
                || key.matches("risk\\.[1-3](\\.(code|title|action\\.1))?")
                || key.matches("field\\.(score|status|headline|headlineDescription|reasoning\\.1|alert\\.1|task\\.1|soil\\.(ph|ec)|weather\\.(minTemperature|maxTemperature|humidity|rainfall))");
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
        injectFieldSoilFacts(facts, email, field);

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
    private void injectFieldSoilFacts(Map<String, Object> facts, String email, FarmEntity field) {
        if (field.getRegionAnalysisId() == null || field.getRegionAnalysisId().isBlank()) return;
        RegionAnalysisEntity analysis = analysisRepository.findByIdAndUserEmail(field.getRegionAnalysisId(), email).orElse(null);
        if (analysis == null || analysis.getPayloadJson() == null || analysis.getPayloadJson().isBlank()) return;
        try {
            Map<String, Object> payload = objectMapper.readValue(analysis.getPayloadJson(), Map.class);
            Map<String, Object> components = objectMap(payload.get("components"));
            Map<String, Object> soil = objectMap(components.get("soil"));
            putIfPresent(facts, "field.soil.ph", soil.get("soilPh"));
            putIfPresent(facts, "field.soil.ec", soil.get("soilEc"));
        } catch (Exception exception) {
            log.warn("Unable to read field soil facts for AI context: {}", field.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callPythonAgent(Map<String, Object> factPackage) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new IllegalStateException("Python internal API key is not configured");
        }
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

    private Map<String, Object> buildAgentUnavailableResponse(Map<String, Object> factPackage) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("answer", AGENT_UNAVAILABLE_MESSAGE);
        answer.put("basisType", "INSUFFICIENT_EVIDENCE");
        answer.put("usedFactIds", List.of());
        answer.put("usedSourceIds", List.of());
        answer.put("mentionedNumbers", List.of());
        answer.put("mentionedCrops", List.of());
        answer.put("mentionedRisks", List.of());
        answer.put("safetyNotice", null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", factPackage.get("requestId"));
        result.put("status", "needs_context");
        result.put("answer", answer);
        result.put("sources", List.of());
        result.put("trace", List.of("현재 AI 응답을 완료하지 못했습니다."));
        return result;
    }

    public static class AssistantException extends ApiException {
        public AssistantException(HttpStatus status, String code, String message) {
            super(status, code, message);
        }
    }
}
