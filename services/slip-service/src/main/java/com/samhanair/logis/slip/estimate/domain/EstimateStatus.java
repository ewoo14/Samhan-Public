package com.samhanair.logis.slip.estimate.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 견적서 상태 — P2-1 (Stage 4) 5 단계.
 *
 * <p>전이 규칙:
 * <pre>
 *   QUOTE_DRAFT → QUOTE_SENT → QUOTE_ACCEPTED → QUOTE_CONVERTED
 *                     ↘ QUOTE_REJECTED
 *   QUOTE_DRAFT/SENT 단계만 수정 가능 (라인 추가/제거/금액 변경)
 *   QUOTE_ACCEPTED 단계만 convert 가능 (Slip 자동 생성)
 * </pre>
 *
 * <p>위반 전이는 모두 {@link com.samhanair.logis.common.exception.BusinessException}
 * (CONFLICT) 으로 통일 — Slip 도메인과 동일 패턴.
 */
@Getter
@RequiredArgsConstructor
public enum EstimateStatus {

    QUOTE_DRAFT("작성중"),
    QUOTE_SENT("발송완료"),
    QUOTE_ACCEPTED("수주완료"),
    QUOTE_REJECTED("거절"),
    QUOTE_CONVERTED("슬립변환완료");

    private final String displayName;
}
