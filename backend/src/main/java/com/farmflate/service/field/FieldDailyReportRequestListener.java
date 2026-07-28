package com.farmflate.service.field;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FieldDailyReportRequestListener {

    private final FieldDailyReportService fieldDailyReportService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generate(FieldDailyReportRequestedEvent event) {
        try {
            fieldDailyReportService.getOrCreate(event.fieldId(), event.reportDate());
        } catch (RuntimeException exception) {
            log.warn("field_daily_report.registration_generation_failed fieldId={} error={}",
                    event.fieldId(), exception.getMessage());
        }
    }
}
