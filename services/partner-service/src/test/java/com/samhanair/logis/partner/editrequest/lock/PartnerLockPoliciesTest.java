package com.samhanair.logis.partner.editrequest.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.partner.domain.PartnerStatus;
import com.samhanair.logis.shared.realtime.lock.DefaultEditLockGuard;
import com.samhanair.logis.shared.realtime.lock.EditLockGuard;
import com.samhanair.logis.shared.realtime.lock.LockedException;
import org.junit.jupiter.api.Test;

/**
 * PR-H4b BE-A — PartnerLockPolicies × DefaultEditLockGuard 단위 테스트.
 */
class PartnerLockPoliciesTest {

    private final EditLockGuard guard = new DefaultEditLockGuard();

    @Test
    void partner_ACTIVE_isFree() {
        guard.guardCanEdit(PartnerStatus.ACTIVE, PartnerLockPolicies.PARTNER, false);
        // no throw
    }

    @Test
    void partner_SUSPENDED_isFree() {
        guard.guardCanEdit(PartnerStatus.SUSPENDED, PartnerLockPolicies.PARTNER, false);
        // no throw
    }

    @Test
    void partner_TERMINATED_isTerminal_throws() {
        assertThatThrownBy(() -> guard.guardCanEdit(PartnerStatus.TERMINATED,
                PartnerLockPolicies.PARTNER, true))
                .isInstanceOf(LockedException.class);
    }

    @Test
    void partner_TERMINATED_delete_alsoBlocked() {
        assertThatThrownBy(() -> guard.guardCanDelete(PartnerStatus.TERMINATED,
                PartnerLockPolicies.PARTNER, true))
                .isInstanceOf(LockedException.class);
    }

    @Test
    void partnerPolicy_categoriesAreCorrect() {
        assertThat(PartnerLockPolicies.PARTNER.isFree(PartnerStatus.ACTIVE)).isTrue();
        assertThat(PartnerLockPolicies.PARTNER.isFree(PartnerStatus.SUSPENDED)).isTrue();
        assertThat(PartnerLockPolicies.PARTNER.isTerminal(PartnerStatus.TERMINATED)).isTrue();
        assertThat(PartnerLockPolicies.PARTNER.isLockedRequiresApproval(PartnerStatus.ACTIVE))
                .isFalse();
    }
}
