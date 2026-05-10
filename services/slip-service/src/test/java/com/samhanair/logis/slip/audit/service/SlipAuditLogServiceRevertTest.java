package com.samhanair.logis.slip.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.audit.domain.SlipAuditLog;
import com.samhanair.logis.slip.audit.repository.SlipAuditLogRepository;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.realtime.SlipRealtimeBroker;
import com.samhanair.logis.slip.repository.SlipRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PR-H2 BE — revert 라이프사이클 4 case 단위 테스트.
 *
 * <ol>
 *   <li>revertToRevision — invalid revisionNo (&lt;1) 거부</li>
 *   <li>revertToRevision — slip 미존재 NOT_FOUND</li>
 *   <li>revertToRevision — 해당 revision audit row 미존재 NOT_FOUND</li>
 *   <li>revertToRevision — 다중 필드 revert 시 모두 같은 신규 revisionNo 공유</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SlipAuditLogServiceRevertTest {

    @Mock private SlipAuditLogRepository auditLogRepository;
    @Mock private SlipRepository slipRepository;
    @Mock private SlipRealtimeBroker broker;

    @InjectMocks private SlipAuditLogService service;

    private UUID slipId;
    private UUID actorId;
    private Slip slip;

    @BeforeEach
    void setUp() {
        slipId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        slip = Slip.createOutbound("2026/05/10-001", LocalDate.now(), 1,
                UUID.randomUUID(), null, null, "거래처A", null, "원본", "user-1");
    }

    @Test
    void revertToRevision_invalidRevisionNo_throwsInvalidInput() {
        assertThatThrownBy(() -> service.revertToRevision(slipId, 0, actorId, "관리자", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(broker, never()).publish(any(), any(), any());
    }

    @Test
    void revertToRevision_slipMissing_throwsNotFound() {
        when(slipRepository.findById(slipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertToRevision(slipId, 1, actorId, "관리자", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(broker, never()).publish(any(), any(), any());
    }

    @Test
    void revertToRevision_revisionMissing_throwsNotFound() {
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));
        when(auditLogRepository.findBySlipIdAndRevisionNo(slipId, 5)).thenReturn(List.of());

        assertThatThrownBy(() -> service.revertToRevision(slipId, 5, actorId, "관리자", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(broker, never()).publish(any(), any(), any());
    }

    @Test
    void revertToRevision_multipleFields_shareSameNewRevisionNo() {
        // 사전 mutation 시뮬: memo + shippingAddress 변경됨, revisionCount=2
        slip.applyOverlayPatch("memo", "수정된 메모");
        slip.applyOverlayPatch("shippingAddress", "수정된 주소");
        slip.incrementRevision();
        slip.incrementRevision(); // revisionCount=2

        SlipAuditLog row1 = SlipAuditLog.record(slipId, 2, actorId, "홍길동", null,
                "memo", "원본", "수정된 메모");
        SlipAuditLog row2 = SlipAuditLog.record(slipId, 2, actorId, "홍길동", null,
                "shippingAddress", null, "수정된 주소");
        ReflectionTestUtils.setField(row1, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(row2, "id", UUID.randomUUID());

        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));
        when(auditLogRepository.findBySlipIdAndRevisionNo(slipId, 2))
                .thenReturn(List.of(row1, row2));
        lenient().when(auditLogRepository.save(any(SlipAuditLog.class))).thenAnswer(inv -> {
            SlipAuditLog log = inv.getArgument(0);
            ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
            return log;
        });

        List<SlipAuditLog> saved = service.revertToRevision(slipId, 2,
                UUID.randomUUID(), "관리자", null);

        assertThat(saved).hasSize(2);
        // revisionCount=2 → +1 = 3, 두 row 모두 revisionNo=3
        assertThat(saved).allMatch(s -> s.getRevisionNo() == 3);
        assertThat(slip.getMemo()).isEqualTo("원본"); // 복원됨
        verify(broker, times(1))
                .publish(eq(slipId), eq(SlipAuditLogService.EVENT_SLIP_REVERTED), any());
    }
}
