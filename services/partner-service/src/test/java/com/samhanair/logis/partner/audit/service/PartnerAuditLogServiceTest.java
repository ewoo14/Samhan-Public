package com.samhanair.logis.partner.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.partner.audit.domain.PartnerAuditLog;
import com.samhanair.logis.partner.audit.repository.PartnerAuditLogRepository;
import com.samhanair.logis.shared.realtime.audit.ChangeEntry;
import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PR-H4b BE-A — PartnerAuditLogService 단위 테스트 (5 case).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerAuditLogServiceTest {

    @Mock private PartnerAuditLogRepository auditLogRepository;
    @Mock private RealtimeBroker broker;

    @InjectMocks private PartnerAuditLogService service;

    private UUID entityId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        when(auditLogRepository.findByEntityIdOrderByRevisionNoDescChangedAtDesc(entityId))
                .thenReturn(List.of());
    }

    @Test
    void recordOverlayPatch_inserts_andPublishes() {
        when(auditLogRepository.save(any(PartnerAuditLog.class))).thenAnswer(inv -> {
            PartnerAuditLog log = inv.getArgument(0);
            ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
            return log;
        });

        service.recordOverlayPatch(entityId, actorId, "이수민", null,
                "partner.name", "(주)구상호", "(주)신상호");

        verify(broker, times(1))
                .publish(eq(entityId), eq(PartnerAuditLogService.EVENT_PARTNER_EDIT), any());
    }

    @Test
    void recordOverlayPatch_rejects_null_entityId() {
        assertThatThrownBy(() -> service.recordOverlayPatch(null, actorId, "이수민", null,
                "field", "old", "new"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordBatch_sharesRevisionNo_andEmitsSingleEvent() {
        when(auditLogRepository.save(any(PartnerAuditLog.class))).thenAnswer(inv -> {
            PartnerAuditLog log = inv.getArgument(0);
            ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
            return log;
        });

        List<ChangeEntry> changes = List.of(
                new ChangeEntry("partner.name", "old1", "new1"),
                new ChangeEntry("partner.address", "old2", "new2"));
        List<PartnerAuditLog> saved = service.recordBatch(entityId, actorId, "이수민", null, changes);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRevisionNo()).isEqualTo(saved.get(1).getRevisionNo());
        verify(broker, times(1))
                .publish(eq(entityId), eq(PartnerAuditLogService.EVENT_PARTNER_EDIT), any());
    }

    @Test
    void recordBatch_rejectsEmptyChanges() {
        assertThatThrownBy(() -> service.recordBatch(entityId, actorId, "이수민", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listByEntity_delegatesToRepository() {
        service.listByEntity(entityId);
        verify(auditLogRepository, times(1))
                .findByEntityIdOrderByRevisionNoDescChangedAtDesc(entityId);
    }
}
