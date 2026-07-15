package com.samhanair.logis.slip.price.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 거래처+품목 단가 기억 커밋 후 저장 후보.
 *
 * <p>라인 저장 루프에서는 본 값만 수집하고, 원 전표/견적 트랜잭션 커밋 후 배치 flush 한다.
 * productId 는 세트 구성품이 아니라 실제 기억 대상 품목 ID 다.
 */
public record PartnerProductPriceMemoryCommand(
        UUID partnerId,
        UUID productId,
        BigDecimal unitPrice,
        String source,
        String actor) {
}
