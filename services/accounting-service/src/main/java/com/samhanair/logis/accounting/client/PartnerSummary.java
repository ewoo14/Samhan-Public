package com.samhanair.logis.accounting.client;

import java.util.UUID;
import java.math.BigDecimal;

/**
 * partner-service 가 반환하는 거래처 요약 (PR-E2 BE-A8/A9/A10 의존).
 *
 * <p>partner-service 의 {@code GET /internal/partners/{partnerCode}} 응답 envelope.data
 * 매핑. accounting-service 가 partner 도메인을 직접 import 하지 않도록 wire-format record.
 *
 * <p>UUID 비공개 가드: partnerId 는 내부 추적용 (분개 partnerId 매칭). 사용자 노출은
 * partnerCode + name + businessNo.
 */
public record PartnerSummary(
        UUID partnerId,
        String partnerCode,
        String name,
        String businessNo,
        String address,
        BigDecimal creditLimit) {

    public PartnerSummary(UUID partnerId, String partnerCode, String name, String businessNo, String address) {
        this(partnerId, partnerCode, name, businessNo, address, null);
    }

    /** partner-service wire field 이름 호환 accessor. */
    public String bizNo() {
        return businessNo;
    }
}
