package com.example.aiworkspace.domain.farm;

import com.example.aiworkspace.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

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
    public FarmEntity(String userEmail, String fieldName, String cropName, int daysPlanted, String stage, String statusBadge, String statusBadgeColor, String todayTask, String reportTime) {
        this.userEmail = userEmail;
        this.fieldName = fieldName;
        this.cropName = cropName;
        this.daysPlanted = daysPlanted;
        this.stage = stage != null ? stage : "생장 초기";
        this.statusBadge = statusBadge != null ? statusBadge : "물주기 필요";
        this.statusBadgeColor = statusBadgeColor != null ? statusBadgeColor : "yellow";
        this.todayTask = todayTask;
        this.reportTime = reportTime != null ? reportTime : "방금 전 자동 분석됨";
    }
}
