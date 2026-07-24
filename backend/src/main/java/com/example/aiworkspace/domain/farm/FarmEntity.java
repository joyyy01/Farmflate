package com.example.aiworkspace.domain.farm;

import com.example.aiworkspace.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "farms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "crop_name", nullable = false, length = 50)
    private String cropName;

    @Column(name = "crop_code", length = 50)
    private String cropCode;

    @Column(name = "region_analysis_id", length = 36)
    private String regionAnalysisId;

    @Column(name = "location_json", columnDefinition = "TEXT")
    private String locationJson;

    @Column(name = "cultivation_method", length = 30)
    private String cultivationMethod;

    @Column(name = "cultivation_start_date")
    private LocalDate cultivationStartDate;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "days_planted")
    private int daysPlanted = 1;

    @Column(length = 50)
    private String stage;

    @Column(name = "status_badge", length = 50)
    private String statusBadge;

    @Column(name = "status_badge_color", length = 20)
    private String statusBadgeColor;

    @Column(name = "today_task", columnDefinition = "TEXT")
    private String todayTask;

    @Column(name = "report_time", length = 100)
    private String reportTime;

    @Builder
    public FarmEntity(Long id, String userEmail, String fieldName, String cropName, String cropCode,
                      String regionAnalysisId, String locationJson, String cultivationMethod,
                      LocalDate cultivationStartDate, Boolean active, int daysPlanted, String stage,
                      String statusBadge, String statusBadgeColor, String todayTask, String reportTime) {
        this.id = id;
        this.userEmail = userEmail;
        this.fieldName = fieldName;
        this.cropName = cropName;
        this.cropCode = cropCode;
        this.regionAnalysisId = regionAnalysisId;
        this.locationJson = locationJson;
        this.cultivationMethod = cultivationMethod;
        this.cultivationStartDate = cultivationStartDate;
        this.active = active == null || active;
        this.daysPlanted = daysPlanted;
        this.stage = stage != null ? stage : "생장 초기";
        this.statusBadge = statusBadge != null ? statusBadge : "물주기 필요";
        this.statusBadgeColor = statusBadgeColor != null ? statusBadgeColor : "yellow";
        this.todayTask = todayTask;
        this.reportTime = reportTime != null ? reportTime : "방금 전 자동 분석됨";
    }
}
