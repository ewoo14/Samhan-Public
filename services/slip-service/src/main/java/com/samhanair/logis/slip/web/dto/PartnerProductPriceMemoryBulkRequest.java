package com.samhanair.logis.slip.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** 거래처+품목 최근단가 bulk 조회 요청. */
@Schema(description = "거래처 한 곳의 품목 최근단가 bulk 조회 요청")
public record PartnerProductPriceMemoryBulkRequest(
        @NotNull
        @Schema(description = "거래처 UUID. 화면 표시가 아닌 API payload 전용")
        UUID partnerId,
        @NotEmpty
        @Size(max = 100, message = "최근단가는 한 번에 최대 100개 품목까지 조회할 수 있습니다")
        @Schema(description = "품목 UUID 목록. 최대 100개이며 중복은 서버가 제거")
        List<@NotNull UUID> productIds) {
}
