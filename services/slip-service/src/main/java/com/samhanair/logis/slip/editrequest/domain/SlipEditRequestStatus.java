package com.samhanair.logis.slip.editrequest.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 슬립 수정/삭제 요청 라이프사이클 status — PR-H3 (Phase 12 Step 3).
 *
 * <p>전이 규칙 (도메인 메서드 가드):
 * <pre>
 *   PENDING → APPROVED (창고 직원 / 관리자 수락)
 *   PENDING → REJECTED (수락 권한자 거절)
 *   PENDING → EXPIRED  (스케줄러 자동 만료, expires_at &lt; now)
 *   APPROVED / REJECTED / EXPIRED → 종결 (재전이 금지)
 * </pre>
 *
 * <p>APPROVED 상태의 활성 요청 1건이라도 있어야 잠금 슬립의 수정/삭제 진행 가능 (SlipPublishService /
 * SlipService 의 mutation 가드).
 */
@Getter
@RequiredArgsConstructor
public enum SlipEditRequestStatus {

    PENDING("요청"),
    APPROVED("수락"),
    REJECTED("거절"),
    EXPIRED("만료");

    private final String displayName;

    /** 종결 상태 여부 — APPROVED/REJECTED/EXPIRED 는 재전이 금지. */
    public boolean isTerminal() {
        return this != PENDING;
    }
}
