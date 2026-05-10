package com.samhanair.logis.shared.realtime.lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * PR-H4a — EditLockPolicy builder + 카테고리 분류 단위 (4 case).
 *
 * <ol>
 *   <li>builder — 빈 builder → 모든 set 빈 상태</li>
 *   <li>builder — freeStatuses + 분기 동작 isFree</li>
 *   <li>builder — lockedRequiresApproval + 분기 동작</li>
 *   <li>builder — fullyLocked + terminalStatuses 분기 동작</li>
 * </ol>
 */
class EditLockPolicyTest {

    enum S { A, B, C, D, E }

    @Test
    void builder_empty_returnsEmptyPolicy() {
        EditLockPolicy<S> p = EditLockPolicy.<S>builder().build();

        assertThat(p.freeStatuses()).isEmpty();
        assertThat(p.lockedRequiresApproval()).isEmpty();
        assertThat(p.fullyLocked()).isEmpty();
        assertThat(p.terminalStatuses()).isEmpty();
        assertThat(p.isFree(S.A)).isFalse();
    }

    @Test
    void builder_freeStatuses_routesIsFree() {
        EditLockPolicy<S> p = EditLockPolicy.<S>builder()
                .freeStatuses(S.A, S.B)
                .build();

        assertThat(p.isFree(S.A)).isTrue();
        assertThat(p.isFree(S.C)).isFalse();
    }

    @Test
    void builder_lockedRequiresApproval_routesCorrectly() {
        EditLockPolicy<S> p = EditLockPolicy.<S>builder()
                .lockedRequiresApproval(S.C)
                .build();

        assertThat(p.isLockedRequiresApproval(S.C)).isTrue();
        assertThat(p.isLockedRequiresApproval(S.A)).isFalse();
    }

    @Test
    void builder_fullyLockedAndTerminal_segmented() {
        EditLockPolicy<S> p = EditLockPolicy.<S>builder()
                .fullyLocked(S.D)
                .terminalStatuses(S.E)
                .build();

        assertThat(p.isFullyLocked(S.D)).isTrue();
        assertThat(p.isFullyLocked(S.E)).isFalse();
        assertThat(p.isTerminal(S.E)).isTrue();
        assertThat(p.isTerminal(S.D)).isFalse();
    }
}
