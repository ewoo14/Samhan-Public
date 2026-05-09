package com.samhanair.logis.slip.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Slip 마감 lock 도메인 단위 테스트 — P1-8 (Stage 4) lock-by-period endpoint 의존.
 *
 * <p>시나리오 1종: lock_flag=true 인 슬립의 reject/cancel 호출 시 CONFLICT.
 */
class SlipLockTest {

    private static final UUID SOURCE_WH = UUID.randomUUID();
    private static final UUID PARTNER = UUID.randomUUID();

    @Test
    void lockedSlip_reject_throwsConflict() {
        Slip slip = createSentSlip();
        slip.lock();
        assertThat(slip.getLockFlag()).isTrue();

        assertThatThrownBy(() -> slip.reject("거래처 변심"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
    }

    @Test
    void lockedSlip_cancel_throwsConflict() {
        Slip slip = Slip.createOutbound("2026/05/09-001", LocalDate.of(2026, 5, 9), 1,
                SOURCE_WH, null, PARTNER, "삼한공조",
                null, null, "user-1");
        slip.lock();

        assertThatThrownBy(slip::cancel)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
    }

    @Test
    void unlock_restoresMutability() {
        Slip slip = createSentSlip();
        slip.lock();
        slip.unlock();

        // unlock 후에는 reject 정상 진행
        slip.reject("거래처 변심");
        assertThat(slip.getStatus()).isEqualTo(SlipStatus.REJECTED);
    }

    @Test
    void lock_idempotent() {
        Slip slip = createSentSlip();
        slip.lock();
        slip.lock(); // 재호출 무영향
        assertThat(slip.getLockFlag()).isTrue();
    }

    @Test
    void defaultLockFlag_isFalse() {
        Slip slip = Slip.createOutbound("2026/05/09-002", LocalDate.of(2026, 5, 9), 2,
                SOURCE_WH, null, PARTNER, "삼한공조",
                null, null, "user-1");
        assertThat(slip.getLockFlag()).isFalse();
    }

    private Slip createSentSlip() {
        Slip slip = Slip.createOutbound("2026/05/09-100", LocalDate.of(2026, 5, 9), 100,
                SOURCE_WH, null, PARTNER, "삼한공조",
                null, null, "user-1");
        slip.save();
        slip.send();
        return slip;
    }
}
