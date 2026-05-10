package com.samhanair.logis.partnerorder.vendor.client;

import java.util.UUID;

/**
 * partner-service 가 반환하는 거래처 요약 — vendor 발주서 검증 + DC 적용 시 사용.
 *
 * <p>UUID 비공개 가드: partnerId 는 내부 추적용. 사용자 노출은 partnerCode + name + businessNo.
 *
 * @param partnerId 내부 식별 (비공개)
 * @param partnerCode 사용자 노출 식별 (필수)
 * @param name 상호
 * @param businessNo 사업자번호
 */
public record PartnerSummary(
        UUID partnerId,
        String partnerCode,
        String name,
        String businessNo) {
}
