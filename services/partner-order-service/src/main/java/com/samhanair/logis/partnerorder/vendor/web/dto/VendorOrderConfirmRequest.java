package com.samhanair.logis.partnerorder.vendor.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * vendor 발주서 → confirm 요청. upload 응답을 사용자가 검토 후 (수정 포함) 전송.
 *
 * @param vendorName 확정 vendor 식별 (필수)
 * @param partnerCode 확정 거래처 코드 (필수)
 * @param lines 확정 라인 (수정/삭제 후)
 */
public record VendorOrderConfirmRequest(
        @NotBlank String vendorName,
        @NotBlank String partnerCode,
        @NotEmpty @Valid List<ConfirmLine> lines) {

    /**
     * 확정 라인. modelCode + quantity + finalPrice 만으로 PartnerOrder 신규 생성.
     *
     * @param modelCode 모델코드 (필수)
     * @param productName 표시명
     * @param quantity 수량 (>=1)
     * @param finalPrice 최종 단가 (DC 적용 후, 필수)
     */
    public record ConfirmLine(
            @NotBlank String modelCode,
            String productName,
            @NotNull @Min(1) Integer quantity,
            @NotNull BigDecimal finalPrice) {
    }
}
