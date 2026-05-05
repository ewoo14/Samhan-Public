package com.samhanair.logis.slip.publish;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Phase 6 M5 (slip-service-integration) — estimate-app v2 의 견적 finalize → 출고전표 발행 요청.
 *
 * <p>endpoint: {@code POST /api/v1/slips/from-estimate}
 *
 * <p>호출자: estimate-app v2 의 {@code lib/slip-bridge.js} ({@link
 * #buildSlipRequest} 와 1:1 mapping). legacy {@code sendOrderFromUi} 의 e-Count {@code SaleList POST}
 * 동작을 1:1 대체.
 *
 * <p>설계 §3 헤더 매핑:
 * <ul>
 *   <li>{@code estimateNumber} → {@code Slip.sourceId} (출처 비즈니스 식별자)</li>
 *   <li>{@code partnerCode} → partner-service lookup → {@code Slip.partnerId}
 *       (현 슬라이스 미지원 — partnerCode 그대로 partnerName 으로 보존, 추후 partner-service IT 추가)</li>
 *   <li>{@code warehouseCode} (legacy "00003"/"2"/"14"/"1") → {@link WarehouseCodeMapper}
 *       으로 UUID 매핑 → {@code Slip.sourceWarehouseId}</li>
 *   <li>{@code employeeCode} → {@code Slip.requesterId} (legacy {@code EMP-0001~0019})</li>
 *   <li>{@code ioDate} (yyyyMMdd) → {@code Slip.slipDate} (서비스 레이어에서 파싱)</li>
 *   <li>{@code shippingAddress / inspectionAddress / receiverPhone / paymentDueLabel /
 *       discountInfo / memo} 6 필드 → {@code Slip.memo} 1000자 결합 prepend
 *       (라벨 포맷, 서비스 레이어가 처리).</li>
 * </ul>
 *
 * <p>모든 필드 nullable 허용 (legacy 입력이 비어있는 경우 다수). 다만 {@code estimateNumber},
 * {@code warehouseCode}, {@code lines} 는 발행에 필수.
 */
public record PublishFromEstimateRequest(
        @NotBlank @Size(max = 64) String estimateNumber,
        String ioDate,
        String timeDate,
        @Size(max = 100) String partnerCode,
        @Size(max = 100) String partnerName,
        @Size(max = 50) String employeeCode,
        @NotBlank @Size(max = 50) String warehouseCode,
        @Size(max = 10) String ioType,
        @Size(max = 500) String shippingAddress,
        @Size(max = 500) String inspectionAddress,
        @Size(max = 100) String receiverPhone,
        @Size(max = 500) String memo,
        @Size(max = 200) String paymentDueLabel,
        @Size(max = 200) String discountInfo,
        @Size(max = 100) String customerTel,
        @Size(max = 200) String customerAddr,
        @Size(max = 100) String customerRep,
        @NotEmpty @Valid List<PublishLineRequest> lines) {
}
