package com.samhanair.logis.slip.price.service;

import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** 최근단가 bulk 조회의 hit 단건. */
@Schema(description = "거래처+품목 최근단가 bulk 조회 hit")
public record PartnerProductPriceMemoryBulkItemResponse(
        @Schema(description = "요청한 품목 UUID. FE 라인 매핑용이며 화면에는 표시하지 않는다.")
        UUID productId,
        @Schema(description = "VAT 포함 입력 단가. 화면 단가 필드에 그대로 채운다.")
        BigDecimal unitPrice,
        @Schema(description = "가격기억 출처. LINE_SAVE | BUNDLE_SET")
        String source,
        @Schema(description = "원 전표/견적에서 이 단가가 저장된 논리 시각")
        LocalDateTime updatedAt) {

    /** 엔티티를 bulk wire 응답으로 변환한다. */
    public static PartnerProductPriceMemoryBulkItemResponse from(PartnerProductPriceMemory memory) {
        return new PartnerProductPriceMemoryBulkItemResponse(
                memory.getProductId(), memory.getUnitPrice(), memory.getSource(), memory.getRememberedAt());
    }
}
