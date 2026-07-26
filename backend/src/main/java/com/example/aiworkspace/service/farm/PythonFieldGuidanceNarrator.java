package com.example.aiworkspace.service.farm;

import com.example.aiworkspace.dto.field.FieldTaskDto;
import com.example.aiworkspace.dto.field.FieldWeatherDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the existing Python agent's generic /agent/task endpoint asking it to
 * rephrase the rule engine's already-decided facts into beginner-friendly
 * Korean. The response is only ever used after every field is validated
 * against the rule engine's own candidates; any mismatch throws so the
 * caller falls back to the rule engine's plain text instead of persisting an
 * unverified LLM claim.
 */
@Slf4j
@Component
public class PythonFieldGuidanceNarrator implements FieldGuidanceNarrator {

    private static final int MAX_HEADLINE = 80;
    private static final int MAX_DESCRIPTION = 300;
    private static final int MAX_SUMMARY = 500;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("${app.python-server.url:http://localhost:8000}")
    private String pythonServerUrl;

    @Value("${app.python-server.internal-api-key:farmflate-local-internal-key}")
    private String internalApiKey;

    public PythonFieldGuidanceNarrator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NarratedGuidance narrate(String cropCode, String cropName, String stage, LocalDate reportDate,
                                     FieldWeatherDto weather, FieldGuidanceRuleEngine.FieldGuidanceResult validated) {
        if (validated.tasks().isEmpty() && validated.alerts().isEmpty()) {
            // Nothing for the narrator to add beyond the rule engine's own stable copy.
            throw new NarrationException("NO_CANDIDATES");
        }
        Map<String, Object> factPackage = buildFactPackage(cropCode, cropName, stage, reportDate, weather, validated);
        JsonNode result = callAgent(factPackage);
        return validate(result, validated);
    }

    private Map<String, Object> buildFactPackage(String cropCode, String cropName, String stage, LocalDate reportDate,
                                                  FieldWeatherDto weather, FieldGuidanceRuleEngine.FieldGuidanceResult validated) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("cropCode", cropCode);
        facts.put("cropName", cropName);
        facts.put("stage", stage);
        facts.put("reportDate", reportDate == null ? null : reportDate.toString());
        facts.put("maxTemperature", weather == null ? null : weather.getMaxTemperature());
        facts.put("minTemperature", weather == null ? null : weather.getMinTemperature());
        facts.put("rainfallMm", weather == null ? null : weather.getRainfallMm());
        facts.put("humidity", weather == null ? null : weather.getHumidity());
        facts.put("windSpeed", weather == null ? null : weather.getWindSpeed());
        facts.put("candidateTasks", validated.tasks().stream()
                .map(task -> Map.of("key", task.getKey(), "title", task.getTitle(), "description", task.getDescription()))
                .toList());
        facts.put("candidateAlerts", validated.alerts().stream()
                .map(alert -> Map.of("key", alert.getKey(), "title", alert.getTitle()))
                .toList());
        return facts;
    }

    private JsonNode callAgent(Map<String, Object> factPackage) {
        String url = pythonServerUrl + "/api/v1/agent/field-guidance";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Farmflate-Internal-Key", internalApiKey);

        Map<String, Object> body = Map.of("facts", factPackage);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new NarrationException("AGENT_CALL_FAILED");
        }
        try {
            return objectMapper.valueToTree(response.getBody());
        } catch (Exception exception) {
            throw new NarrationException("AGENT_RESULT_NOT_JSON");
        }
    }

    private NarratedGuidance validate(JsonNode result, FieldGuidanceRuleEngine.FieldGuidanceResult validated) {
        String headline = textOrNull(result, "headline");
        String headlineDescription = textOrNull(result, "headlineDescription");
        String reasoningSummary = textOrNull(result, "reasoningSummary");

        if (headline == null || headline.length() > MAX_HEADLINE
                || headlineDescription == null || headlineDescription.length() > MAX_DESCRIPTION
                || reasoningSummary == null || reasoningSummary.length() > MAX_SUMMARY) {
            throw new NarrationException("AGENT_RESULT_OUT_OF_BOUNDS");
        }

        Map<String, FieldTaskDto> byKey = new LinkedHashMap<>();
        for (FieldTaskDto candidate : validated.tasks()) byKey.put(candidate.getKey(), candidate);

        List<FieldTaskDto> narratedTasks = new ArrayList<>();
        JsonNode tasksNode = result.path("tasks");
        if (tasksNode.isArray()) {
            for (JsonNode taskNode : tasksNode) {
                String key = textOrNull(taskNode, "key");
                FieldTaskDto candidate = key == null ? null : byKey.get(key);
                if (candidate == null) continue; // never accept a task key the rule engine didn't propose
                String title = textOrNull(taskNode, "title");
                String description = textOrNull(taskNode, "description");
                if (title == null || title.length() > MAX_HEADLINE || description == null || description.length() > MAX_DESCRIPTION) {
                    continue;
                }
                narratedTasks.add(FieldTaskDto.builder()
                        .key(candidate.getKey()).title(title).description(description)
                        .badge(candidate.getBadge()).acknowledged(candidate.isAcknowledged()).build());
            }
        }
        if (narratedTasks.size() != validated.tasks().size()) {
            throw new NarrationException("AGENT_TASK_SET_MISMATCH");
        }

        return new NarratedGuidance(headline, headlineDescription, narratedTasks, reasoningSummary);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    public static class NarrationException extends RuntimeException {
        public NarrationException(String code) {
            super(code);
        }
    }
}
