package com.example.aiworkspace.service.farm;

import com.example.aiworkspace.domain.farm.FieldActivityLogEntity;
import com.example.aiworkspace.dto.field.FieldAlertDto;
import com.example.aiworkspace.dto.field.FieldDailyStatus;
import com.example.aiworkspace.dto.field.FieldTaskBadge;
import com.example.aiworkspace.dto.field.FieldTaskDto;
import com.example.aiworkspace.dto.field.FieldWeatherDto;
import com.example.aiworkspace.dto.field.FieldWeatherStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides today's tasks/alerts/status purely from verified inputs (weather,
 * crop/stage, recent logs). No LLM call happens here — this is the source of
 * truth the narrator layer is validated against, so a rule change here is a
 * behavior change, not a copy change.
 */
@Component
public class FieldGuidanceRuleEngine {

    public static final String RULE_VERSION = "field-guidance-rules-v1";
    private static final int MAX_TASKS = 2;

    public record FieldGuidanceInput(
            String cropCode,
            String cropName,
            String stage,
            FieldWeatherDto weather,
            List<FieldActivityLogEntity> recentLogs) {
    }

    public record FieldGuidanceResult(
            FieldDailyStatus status,
            String headline,
            String headlineDescription,
            List<FieldTaskDto> tasks,
            List<FieldAlertDto> alerts,
            List<String> reasoningPoints) {
    }

    public FieldGuidanceResult evaluate(FieldGuidanceInput input) {
        FieldWeatherDto weather = input.weather();
        if (weather == null || weather.getStatus() != FieldWeatherStatus.AVAILABLE) {
            List<FieldTaskDto> tasks = List.of(task(
                    "CHECK_FIELD_DIRECTLY",
                    "밭 상태를 직접 확인하세요",
                    "날씨 데이터를 불러오지 못해 자동 안내가 제한돼요. 잎과 흙 상태를 직접 확인해 주세요.",
                    FieldTaskBadge.CHECK_ANYTIME));
            return new FieldGuidanceResult(
                    FieldDailyStatus.NEEDS_CHECK,
                    "오늘은 자동 안내가 제한돼요",
                    "날씨 정보를 확인할 수 없어 밭을 직접 살펴봐 주세요.",
                    tasks, List.of(), List.of("날씨 데이터를 불러오지 못했어요."));
        }

        List<FieldAlertDto> alerts = new ArrayList<>();
        List<FieldTaskDto> candidateTasks = new ArrayList<>();
        List<String> reasoning = new ArrayList<>();

        Double maxTemp = weather.getMaxTemperature();
        Double rainfall = weather.getRainfallMm();
        Double windSpeed = weather.getWindSpeed();

        if (maxTemp != null && maxTemp >= 30.0) {
            alerts.add(alert("HIGH_TEMPERATURE", "MEDIUM", "오후 고온 주의",
                    "오늘 최고기온이 " + format(maxTemp) + "℃까지 오를 전망이에요."));
            candidateTasks.add(task("CHECK_LEAF_CONDITION", "오후에 잎 처짐을 확인하세요",
                    "고온 시간대에 잎이 처질 수 있어요. 필요하면 차광을 준비하세요.", FieldTaskBadge.CHECK_ANYTIME));
            reasoning.add("오늘 예상 최고기온 " + format(maxTemp) + "℃");
        }

        if (rainfall != null && rainfall >= 30.0) {
            alerts.add(alert("HEAVY_RAIN", "HIGH", "강한 비 예보",
                    "오늘 예상 강수량이 " + format(rainfall) + "mm로 많아요."));
            candidateTasks.add(task("CHECK_DRAINAGE", "배수로를 확인하세요",
                    "강한 비가 예보돼 있어요. 물이 고이지 않도록 배수로를 미리 확인하세요.", FieldTaskBadge.MORNING_RECOMMENDED));
            reasoning.add("오늘 예상 강수량 " + format(rainfall) + "mm");
        }

        if (windSpeed != null && windSpeed >= 9.0) {
            alerts.add(alert("STRONG_WIND", "MEDIUM", "강풍 주의",
                    "오늘 풍속이 초속 " + format(windSpeed) + "m까지 강해질 수 있어요."));
            candidateTasks.add(task("CHECK_SUPPORT_STAKES", "지지대를 확인하세요",
                    "바람이 강해질 수 있어요. 어린 작물이나 지지대가 흔들리지 않는지 확인하세요.", FieldTaskBadge.CHECK_ANYTIME));
        }

        boolean recentWatering = hasRecentLog(input.recentLogs(), "WATERING");
        boolean isDry = rainfall != null && rainfall < 1.0;
        if (isDry && !recentWatering) {
            candidateTasks.add(task("CHECK_SOIL_MOISTURE", "흙의 마른 정도를 확인하세요",
                    "최근 비가 적어요. 흙을 직접 확인한 뒤 말랐을 때만 물을 주세요.", FieldTaskBadge.MORNING_RECOMMENDED));
            reasoning.add(recentWatering ? "최근 2일 물주기 기록 있음" : "최근 2일 물주기 기록 없음");
        }

        List<FieldTaskDto> tasks = dedupeAndLimit(candidateTasks);
        FieldDailyStatus status = alerts.isEmpty() ? FieldDailyStatus.STABLE : FieldDailyStatus.CAUTION;

        String headline = alerts.isEmpty()
                ? "오늘은 특별한 주의 없이 안정적이에요"
                : "오늘은 " + alerts.get(0).getTitle() + "가 필요해요";
        String headlineDescription = tasks.isEmpty()
                ? "오늘 예보 기준으로 추가 조치가 필요하지 않아요."
                : tasks.get(0).getDescription();

        if (reasoning.isEmpty()) {
            reasoning.add("오늘 예보 기준 특이 위험이 확인되지 않았어요.");
        }

        return new FieldGuidanceResult(status, headline, headlineDescription, tasks, alerts, reasoning);
    }

    private List<FieldTaskDto> dedupeAndLimit(List<FieldTaskDto> candidates) {
        List<FieldTaskDto> result = new ArrayList<>();
        for (FieldTaskDto candidate : candidates) {
            if (result.size() >= MAX_TASKS) break;
            boolean duplicate = result.stream().anyMatch(existing -> existing.getKey().equals(candidate.getKey()));
            if (!duplicate) result.add(candidate);
        }
        return result;
    }

    private boolean hasRecentLog(List<FieldActivityLogEntity> recentLogs, String category) {
        return recentLogs != null && recentLogs.stream()
                .anyMatch(log -> category.equalsIgnoreCase(log.getCategory()));
    }

    private FieldTaskDto task(String key, String title, String description, FieldTaskBadge badge) {
        return FieldTaskDto.builder().key(key).title(title).description(description).badge(badge).acknowledged(false).build();
    }

    private FieldAlertDto alert(String key, String severity, String title, String description) {
        return FieldAlertDto.builder().key(key).severity(severity).title(title).description(description).build();
    }

    private String format(Double value) {
        if (value == null) return "?";
        if (value == Math.floor(value)) return String.valueOf(value.intValue());
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }
}
