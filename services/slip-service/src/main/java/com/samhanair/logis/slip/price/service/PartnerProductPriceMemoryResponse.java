package com.samhanair.logis.slip.price.service;

import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 거래처+품목 최근 수동단가 조회 응답. */
@Schema(description = "거래처+품목 최근 수동단가 기억 응답")
public record PartnerProductPriceMemoryResponse(
        @Schema(description = "VAT 포함 입력 단가. 화면 단가 필드에 그대로 채운다.")
        BigDecimal unitPrice,
        @Schema(description = "가격기억 출처. 현재는 LINE_SAVE")
        String source,
        @Schema(description = "최근 갱신 시각")
        LocalDateTime updatedAt) {

    /** 엔티티를 API 응답으로 변환한다. */
    public static PartnerProductPriceMemoryResponse from(PartnerProductPriceMemory memory) {
        LocalDateTime updatedAt = memory.getModifiedAt() != null
                ? memory.getModifiedAt()
                : memory.getCreatedAt();
        return new PartnerProductPriceMemoryResponse(memory.getUnitPrice(), memory.getSource(), updatedAt);
    }
}
