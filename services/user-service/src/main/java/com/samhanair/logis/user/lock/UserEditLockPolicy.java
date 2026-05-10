package com.samhanair.logis.user.lock;

import com.samhanair.logis.shared.realtime.lock.EditLockPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-service 잠금 정책 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>사용자 task 명시:
 * <ul>
 *   <li>{@link UserStatus#ACTIVE} (terminationDate == null) — 자유 mutation</li>
 *   <li>{@link UserStatus#DEACTIVATED} (terminationDate != null) — APPROVED 1건 소진 후 mutation 가능
 *       (재고용 / 정정 등 별도 채널)</li>
 * </ul>
 *
 * <p>Designer H4b-be-rollout-checklist § 2.9 참고 — user-service 는 audit only 권고였으나
 * 사용자 task 가 EditLockGuard 명시 도입 → DEACTIVATED 시점 잠금 정책 채택.
 *
 * <p>{@code @Configuration} bean — {@link EditLockPolicy<UserStatus>} 를 spring context 에 등록.
 * 호출자 (EmployeeProvisioningService 등) 가 {@code @Autowired} 로 주입받아 EditLockGuard 호출.
 */
@Configuration
public class UserEditLockPolicy {

    /** {@link EditLockPolicy} bean — 호출자가 EditLockGuard 와 함께 주입받아 사용. */
    @Bean
    public EditLockPolicy<UserStatus> userEditLockPolicy() {
        return EditLockPolicy.<UserStatus>builder()
                .freeStatuses(UserStatus.ACTIVE)
                .lockedRequiresApproval(UserStatus.DEACTIVATED)
                .build();
    }
}
