package com.samhanair.logis.shared.realtime.editrequest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 수정/삭제 요청 라이프사이클 status — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>14 service 가 자체 도메인 (slip / lot / dispatch / partner-order 등) 의 수정 요청 라이프사이클
 * 을 본 enum 으로 표현. consumer entity 는 본 enum 의 컬럼을 string mapping 으로 보유.
 *
 * <p>전이 규칙 (도메인 메서드 가드):
 * <pre>
 *   PENDING → APPROVED (권한자 수락)
 *   PENDING → REJECTED (권한자 거절)
 *   PENDING → EXPIRED  (스케줄러 자동 만료, expires_at &lt; now)
 *   APPROVED / REJECTED / EXPIRED → 종결 (재전이 금지)
 * </pre>
 *
 * <p>APPROVED 상태의 활성 요청 1건이라도 있어야 잠금 entity 의 mutation 진행 가능.
 */
@Getter
@RequiredArgsConstructor
public enum EditRequestStatus {

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
