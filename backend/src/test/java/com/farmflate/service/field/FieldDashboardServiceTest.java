package com.farmflate.service.field;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FieldDashboardServiceTest {

    @Test
    void dashboard_suspends_an_inherited_transaction_before_daily_generation() throws NoSuchMethodException {
        Transactional transaction = FieldDashboardService.class
                .getMethod("getDashboard", String.class, Long.class, LocalDate.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
