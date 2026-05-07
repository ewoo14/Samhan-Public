package com.samhanair.logis.arologis.matcher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.arologis.domain.MatchSource;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleTonnage;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InsungQuickDriverMatcher placeholder 검증 — Phase 10 W10-1.
 *
 * <p>본 PR (W10-1) 은 throw 만 검증. W10-2 시점 실 vendor 통합 시점에 별도 IT 추가 의무.
 */
class InsungQuickDriverMatcherTest {

    @Test
    @DisplayName("InsungQuick — match 호출 시 UnsupportedOperationException (W10-2 placeholder)")
    void match_throws_unsupported() {
        InsungQuickDriverMatcher matcher = new InsungQuickDriverMatcher();
        Vehicle vehicle = Vehicle.of(UUID.randomUUID(), 1, VehicleTonnage.TONNAGE_1, null);
        assertThatThrownBy(() -> matcher.match(vehicle, List.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("W10-2");
    }

    @Test
    @DisplayName("source enum = EXTERNAL_INSUNG_QUICK")
    void source_returns_insung_quick() {
        InsungQuickDriverMatcher matcher = new InsungQuickDriverMatcher();
        Assertions.assertThat(matcher.source()).isEqualTo(MatchSource.EXTERNAL_INSUNG_QUICK);
    }
}
