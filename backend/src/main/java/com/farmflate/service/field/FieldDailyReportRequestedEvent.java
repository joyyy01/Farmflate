package com.farmflate.service.field;

import java.time.LocalDate;

/** Published after a field registration commits so report generation never extends that transaction. */
public record FieldDailyReportRequestedEvent(Long fieldId, LocalDate reportDate) {
}
