package com.samhanair.logis.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** EmployeeSignatureAudit 정적 factory 단위 테스트 - C1a. */
class EmployeeSignatureAuditTest {

    @Test
    void record_factory는_RECORD_action과_핵심필드를_채운다() {
        UUID emp = UUID.randomUUID();
        EmployeeSignatureAudit audit = EmployeeSignatureAudit.record(
                emp, "a".repeat(64), SignatureChannel.UPLOAD, "actor-1");

        assertThat(audit.getEmployeeId()).isEqualTo(emp);
        assertThat(audit.getAction()).isEqualTo(SignatureAuditAction.RECORD);
        assertThat(audit.getSignatureHash()).isEqualTo("a".repeat(64));
        assertThat(audit.getSignatureChannel()).isEqualTo(SignatureChannel.UPLOAD);
        assertThat(audit.getActorUserId()).isEqualTo("actor-1");
        assertThat(audit.getReason()).isNull();
    }

    @Test
    void invalidate_factory는_INVALIDATE_action과_reason을_채운다() {
        UUID emp = UUID.randomUUID();
        EmployeeSignatureAudit audit = EmployeeSignatureAudit.invalidate(
                emp, "b".repeat(64), SignatureChannel.MOBILE_CANVAS, "오등록 정정", "master-9");

        assertThat(audit.getAction()).isEqualTo(SignatureAuditAction.INVALIDATE);
        assertThat(audit.getReason()).isEqualTo("오등록 정정");
        assertThat(audit.getActorUserId()).isEqualTo("master-9");
    }
}
