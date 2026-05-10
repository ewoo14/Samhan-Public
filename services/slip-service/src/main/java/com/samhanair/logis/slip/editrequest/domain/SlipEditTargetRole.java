package com.samhanair.logis.slip.editrequest.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 슬립 수정/삭제 요청 수락 권한자 그룹 — PR-H3 (Phase 12 Step 3).
 *
 * <p>사용자 명시 잠금 정책에 따른 매핑:
 * <ul>
 *   <li>{@link #WAREHOUSE} — CONFIRMED (창고 인계 = ACCEPTED 이후) 단계의 수정/삭제 요청은
 *       창고 직원 (ROLE_WAREHOUSE) 또는 관리자 (ROLE_MANAGER) 가 수락. 일반적으로 창고가 우선.</li>
 *   <li>{@link #MANAGER} — INSPECTING/SHIPPING 단계는 창고도 수락 불가, 관리자만 수락.
 *       (현 PR-H3 시범 한정 — 실제 도메인 정책은 INSPECTING/SHIPPING 자체가 완전 잠금이라
 *       MANAGER 도 거부될 수 있음. 본 enum 은 추후 정책 확장을 위한 channel 분리 의도.)</li>
 * </ul>
 *
 * <p>ROLE 풀네임 의무 (memory feedback_role_naming_full): 본 enum 의 displayName 도 풀네임 사용.
 */
@Getter
@RequiredArgsConstructor
public enum SlipEditTargetRole {

    WAREHOUSE("창고 직원"),
    MANAGER("관리자");

    private final String displayName;
}
