package com.samhanair.logis.arologis.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.realtime.domain.ArologisAuditLog;
import com.samhanair.logis.arologis.realtime.repository.ArologisAuditLogRepository;
import com.samhanair.logis.arologis.realtime.service.ArologisAuditLogRecorder;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.shared.realtime.audit.ChangeEntry;
import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PR-H4b — ArologisAuditLogRecorder 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class ArologisAuditLogRecorderTest {

    @Mock
    private ArologisAuditLogRepository auditLogRepository;

    @Mock
    private RealtimeBroker broker;

    @InjectMocks
    private ArologisAuditLogRecorder recorder;

    private UUID entityId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        entityId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        lenient().when(auditLogRepository.save(any(ArologisAuditLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordOverlayPatch_publishesArologisEditEvent() {
        when(auditLogRepository.countByEntityId(entityId)).thenReturn(0L);
        recorder.recordOverlayPatch(entityId, actorId, "배차담당", null,
                "stops[0].status", "PENDING", "ARRIVED");
        verify(auditLogRepository, times(1)).save(any(ArologisAuditLog.class));
        verify(broker).publish(eq(entityId),
                eq(ArologisAuditLogRecorder.EVENT_AROLOGIS_EDIT), any());
    }

    @Test
    void recordBatch_multipleFields_shareSameRevisionNo() {
        when(auditLogRepository.countByEntityId(entityId)).thenReturn(4L);
        ArgumentCaptor<ArologisAuditLog> captor = ArgumentCaptor.forClass(ArologisAuditLog.class);

        recorder.recordBatch(entityId, actorId, "x", null, List.of(
                new ChangeEntry("dispatchType", "DAY", "NIGHT"),
                new ChangeEntry("vehicles[0].assignedDriverId", null, UUID.randomUUID().toString())));

        verify(auditLogRepository, times(2)).save(captor.capture());
        List<ArologisAuditLog> rows = captor.getAllValues();
        assertThat(rows).allMatch(r -> r.getRevisionNo() == 5);
        verify(broker, times(1)).publish(any(), anyString(), any());
    }

    @Test
    void recordBatch_emptyChanges_throws() {
        assertThatThrownBy(() -> recorder.recordBatch(entityId, actorId, "x", null, List.of()))
                .isInstanceOf(BusinessException.class);
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void listByEntity_delegates() {
        when(auditLogRepository.findByEntityIdOrderByRevisionNoDescChangedAtDesc(entityId))
                .thenReturn(List.of());
        assertThat(recorder.listByEntity(entityId)).isEmpty();
    }
}
