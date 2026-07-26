package com.example.aiworkspace.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FieldDailyReportSchedulerTest {

    @Test
    void runs_daily_field_refresh_at_six_am_in_korea() throws NoSuchMethodException {
        Method method = FieldDailyReportScheduler.class.getMethod("generateDailyReports");
        Scheduled schedule = method.getAnnotation(Scheduled.class);

        assertThat(schedule).isNotNull();
        assertThat(schedule.cron()).isEqualTo("0 0 6 * * *");
        assertThat(schedule.zone()).isEqualTo("Asia/Seoul");
    }
}
