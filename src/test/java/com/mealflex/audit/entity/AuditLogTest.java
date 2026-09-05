package com.mealflex.audit.entity;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {
    @Test
    void auditLogCapturesCurrentCorrelationIdWhenPersisted() {
        MDC.put("correlationId", "request-12345678");
        try {
            AuditLog audit = AuditLog.builder().action("TEST").build();
            audit.attachCorrelationId();
            assertThat(audit.getCorrelationId()).isEqualTo("request-12345678");
        } finally { MDC.remove("correlationId"); }
    }
}
