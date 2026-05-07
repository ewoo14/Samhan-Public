package com.samhanair.logis.dashboard.client;

import java.util.UUID;

/**
 * Partner lookup 결과 요약 DTO — partner-service `/internal/partners/{partnerCode}` 응답에서
 * dashboard 가 사용하는 최소 필드만 추출.
 *
 * <p>PR #94 W4 후속 fix (QA Q-W4-2 채택) — {@link PartnerClient#findByCode(String)} 의 응답을
 * raw String 에서 본 record 로 강화하여 service-side resolve (partnerCode → partnerId UUID) 가능.
 *
 * <p>UUID 비공개 가드 — 본 record 의 {@code partnerId} 는 dashboard 내부 service 계산용 only,
 * 사용자 응답 (예: SalesAggregateResponse) 에는 직접 노출 금지. 응답 DTO 는 {@code partnerCode} 만 첨부.
 *
 * @param partnerId 거래처 UUID (내부 service-side resolve 용, 사용자 응답 노출 금지)
 * @param partnerCode 사용자 노출 식별자
 * @param name 거래처 상호 (dashboard 응답 partnerName 첨부 시 사용)
 */
public record PartnerSummary(
        UUID partnerId,
        String partnerCode,
        String name
) {
}
