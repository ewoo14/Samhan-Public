package com.samhanair.logis.dcconfig.lock;

import com.samhanair.logis.shared.realtime.lock.EditLockPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * dc-config-service 잠금 정책 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>사용자 task 명시:
 * <ul>
 *   <li>{@link DcConfigStatus#DRAFT} (ADMIN_EDIT) — 자유 mutation</li>
 *   <li>{@link DcConfigStatus#IN_USE} (외부 시드/RPC 적용) — APPROVED 1건 소진 후 mutation 가능</li>
 * </ul>
 *
 * <p>Designer H4b-be-rollout-checklist § 2.7 (DcRule 잠금 정책) 참고. DcConfig 의 status 컬럼
 * 부재 → DcConfigSource 기반 가상 status 매핑.
 */
@Configuration
public class DcConfigEditLockPolicy {

    @Bean
    public EditLockPolicy<DcConfigStatus> dcConfigEditLockPolicy() {
        return EditLockPolicy.<DcConfigStatus>builder()
                .freeStatuses(DcConfigStatus.DRAFT)
                .lockedRequiresApproval(DcConfigStatus.IN_USE)
                .build();
    }
}
