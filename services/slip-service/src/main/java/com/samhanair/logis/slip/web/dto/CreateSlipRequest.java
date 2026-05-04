package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.DeliveryTag;
import com.samhanair.logis.slip.domain.SlipType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 전표 생성 요청 — slipType 분기로 OUTBOUND/INBOUND 처리. slipDate 가 null 이면 서비스 레이어에서
 * {@code LocalDate.now()} 사용.
 */
public record CreateSlipRequest(
        @NotNull SlipType slipType,
        LocalDate slipDate,
        UUID sourceWarehouseId,
        UUID destinationWarehouseId,
        UUID partnerId,
        @Size(max = 100) String partnerName,
        DeliveryTag deliveryTag,
        @Size(max = 1000) String memo,
        @NotEmpty @Valid List<SlipLineRequest> lines) {

    /** 전표 라인 — productId / 수량 / 단가 / 메모 + 표시용 snapshot 명칭. */
    public record SlipLineRequest(
            @NotNull UUID productId,
            @Size(max = 200) String productName,
            @Size(max = 100) String modelName,
            @NotNull @Positive Integer quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @Size(max = 200) String note) {
    }
}
