package com.samhanair.logis.slip.price.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        String actor,
        LocalDateTime rememberedAt) {

    /** 원 트랜잭션 시각은 service 가 커밋 콜백 등록 전에 보강한다. */
    public PartnerProductPriceMemoryCommand(
            UUID partnerId, UUID productId, BigDecimal unitPrice, String source, String actor) {
        this(partnerId, productId, unitPrice, source, actor, null);
    }

    /** 원 트랜잭션의 논리 저장 시각을 담은 command 를 반환한다. */
    public PartnerProductPriceMemoryCommand withRememberedAt(LocalDateTime logicalEventTime) {
        return new PartnerProductPriceMemoryCommand(
                partnerId, productId, unitPrice, source, actor, logicalEventTime);
    }
}
