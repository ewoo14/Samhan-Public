package com.samhanair.logis.shared.realtime.editrequest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 수정/삭제 요청 수락 권한자 그룹 — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>도메인별 매핑:
 * <ul>
 *   <li>{@link #WAREHOUSE} — 창고 인계 후 단계 (예: SLIP CONFIRMED/ACCEPTED/PROCESSING) 의 요청은
 *       창고 직원 (ROLE_WAREHOUSE) 또는 관리자 (ROLE_MANAGER) 가 수락. 일반적으로 창고 우선.</li>
 *   <li>{@link #MANAGER} — 관리자만 수락 가능한 정책 (도메인별 확장 여지).</li>
 * </ul>
 *
 * <p>ROLE 풀네임 의무 (memory feedback_role_naming_full): 본 enum displayName 도 풀네임 사용.
 */
@Getter
@RequiredArgsConstructor
public enum EditTargetRole {

    WAREHOUSE("창고 직원"),
    MANAGER("관리자");

    private final String displayName;
}
