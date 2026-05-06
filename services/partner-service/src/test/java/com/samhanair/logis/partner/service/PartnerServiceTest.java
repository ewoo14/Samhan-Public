package com.samhanair.logis.partner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Partner 도메인 단위 테스트 — JPA / Spring 부팅 없음. JDK 17 한글 path 환경에서도 PASS.
 *
 * <p>커버:
 * <ol>
 *   <li>register 정상 흐름 + 필수값 가드</li>
 *   <li>changeCreditLimit delta 계산</li>
 *   <li>increaseBalance / decreaseBalance 잔액 일관성</li>
 *   <li>canIssueSlip 한도 초과 / 비활성 상태 거부</li>
 *   <li>상태 전이 (suspend / activate / terminate)</li>
 * </ol>
 */
class PartnerServiceTest {

    @Test
    void register_with_required_fields_initialises_active_status_and_zero_balance() {
        Partner p = Partner.register("P-2026-0001", "123-45-67890", "(주)테스트",
                "서울 강남구", "02-0000-0000", new BigDecimal("1000000"));

        assertThat(p.getPartnerCode()).isEqualTo("P-2026-0001");
        assertThat(p.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
        assertThat(p.getOutstandingBalance()).isEqualByComparingTo("0");
        assertThat(p.getCreditLimit()).isEqualByComparingTo("1000000");
    }

    @Test
    void register_rejects_blank_required_fields() {
        assertThatThrownBy(() -> Partner.register("", "123", "name", null, null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Partner.register("code", "", "name", null, null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Partner.register("code", "123", " ", null, null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeCreditLimit_returns_delta_and_updates_state() {
        Partner p = Partner.register("P-1", "123", "name", null, null, new BigDecimal("500000"));

        BigDecimal delta = p.changeCreditLimit(new BigDecimal("2000000"));

        assertThat(delta).isEqualByComparingTo("1500000");
        assertThat(p.getCreditLimit()).isEqualByComparingTo("2000000");
    }

    @Test
    void changeCreditLimit_rejects_negative_value() {
        Partner p = Partner.register("P-1", "123", "name", null, null, BigDecimal.ZERO);
        assertThatThrownBy(() -> p.changeCreditLimit(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void increaseBalance_then_decreaseBalance_round_trip_is_consistent() {
        Partner p = Partner.register("P-1", "123", "name", null, null, new BigDecimal("1000000"));

        p.increaseBalance(new BigDecimal("300000"));
        assertThat(p.getOutstandingBalance()).isEqualByComparingTo("300000");

        p.decreaseBalance(new BigDecimal("100000"));
        assertThat(p.getOutstandingBalance()).isEqualByComparingTo("200000");
    }

    @Test
    void decreaseBalance_rejects_overpay() {
        Partner p = Partner.register("P-1", "123", "name", null, null, new BigDecimal("1000000"));
        p.increaseBalance(new BigDecimal("100"));

        assertThatThrownBy(() -> p.decreaseBalance(new BigDecimal("200")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canIssueSlip_blocks_when_limit_exceeded() {
        Partner p = Partner.register("P-1", "123", "name", null, null, new BigDecimal("1000000"));
        p.increaseBalance(new BigDecimal("900000"));

        assertThat(p.canIssueSlip(new BigDecimal("100000"))).isTrue(); // 정확히 한도
        assertThat(p.canIssueSlip(new BigDecimal("100001"))).isFalse(); // 초과
    }

    @Test
    void canIssueSlip_blocks_when_not_active() {
        Partner p = Partner.register("P-1", "123", "name", null, null, new BigDecimal("1000000"));

        p.suspend();
        assertThat(p.canIssueSlip(new BigDecimal("1"))).isFalse();

        p.activate();
        assertThat(p.canIssueSlip(new BigDecimal("1"))).isTrue();

        p.terminate();
        assertThat(p.canIssueSlip(new BigDecimal("1"))).isFalse();
    }
}
