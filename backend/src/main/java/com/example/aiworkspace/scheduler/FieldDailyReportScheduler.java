package com.example.aiworkspace.scheduler;

import com.example.aiworkspace.service.farm.FieldDailyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Single-instance-safe by construction: getOrCreate() is idempotent per
 * farm/date/DAILY_0630, so a second app instance (or a scheduler retry after
 * a restart) racing this job just reuses the same row instead of duplicating
 * it. A true multi-instance deployment should still add a distributed lock
 * (e.g. ShedLock) purely to avoid redundant provider calls, not for
 * correctness.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldDailyReportScheduler {

    private final FieldDailyReportService reportService;
    private final Clock clock;

    @Scheduled(cron = "0 30 6 * * *", zone = "Asia/Seoul")
    public void generateDailyReports() {
        LocalDate today = LocalDate.now(clock);
        log.info("field_daily_report scheduled run starting for {}", today);
        reportService.generateForAllActiveFields(today);
    }
}
