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
 *
 * <p>Slice B (notification-slice-B): {@code driverName}, {@code driverPhone} 2 필드 신규 추가 —
 * 출고 슬립 생성 시 배송 기사 정보를 함께 입력 가능 (선택). 입력되지 않으면 추후 editHeader 로 갱신.
 *
 * <p>PR-G1 backlog #2 — V16 e-Count schema 12 컬럼 신규 (모두 nullable):
 * <ul>
 *   <li>{@code customerTel} / {@code customerAddress} / {@code customerRepresentative}
 *       — 거래처 자동 채움 후 사용자 수정 가능 (snapshot).</li>
 *   <li>{@code shippingAddress} / {@code inspectionAddress} / {@code receiverPhone}
 *       — 배송지/검수지/수령자 별도 입력.</li>
 *   <li>{@code paymentDueLabel} (MM-DD picker label) / {@code discountInfo} (textarea).</li>
 *   <li>{@code collectTerm} / {@code agreeTerm} — 대금 회수 조건 / 거래 약정 조건.</li>
 *   <li>{@code ioType} ({@code "10"}=출고 / {@code "11"}=입고. null 시 slipType 분기 자동).</li>
 *   <li>{@code timeDate} (HHmmss. null 시 서버 시각 자동).</li>
 * </ul>
 *
 * <p>본 12 필드는 publish 흐름 ({@code from-estimate} / {@code from-partner-order}) 과 동일하게
 * {@code Slip.applyEcountSchema} 로 직접 컬럼 저장.
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
        @Size(max = 50) String driverName,
        @Size(max = 20) String driverPhone,
        // PR-G1 backlog #2 — V16 e-Count 12 컬럼 (모두 nullable)
        @Size(max = 10) String ioType,
        @Size(max = 10) String timeDate,
        @Size(max = 100) String customerTel,
        @Size(max = 200) String customerAddress,
        @Size(max = 100) String customerRepresentative,
        @Size(max = 500) String shippingAddress,
        @Size(max = 500) String inspectionAddress,
        @Size(max = 100) String receiverPhone,
        @Size(max = 200) String paymentDueLabel,
        @Size(max = 200) String discountInfo,
        @Size(max = 100) String collectTerm,
        @Size(max = 100) String agreeTerm,
        @NotEmpty @Valid List<SlipLineRequest> lines) {

    /**
     * 전표 라인 — productId / 수량 / 단가 / 메모 + 표시용 snapshot 명칭.
     * Slice A (sales-polish-2): {@code specification} 필드 신규 추가 (사용자 피드백 #4).
     */
    public record SlipLineRequest(
            @NotNull UUID productId,
            @Size(max = 200) String productName,
            @Size(max = 100) String modelName,
            @Size(max = 50) String specification,
            @NotNull @Positive Integer quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @Size(max = 200) String note) {
    }
}
