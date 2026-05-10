package com.samhanair.logis.partnerorder.vendor.parser;

import java.math.BigDecimal;
import java.util.List;

/**
 * vendor 발주서 OCR text → 구조화 결과. controller 가 단가 lookup + DC 적용 전 단계 형식.
 *
 * <p>UUID 비공개 가드 — productId 는 본 record 에 포함하지 않으며, 사용자 노출 식별자 (vendorName /
 * partnerCode / modelCode / productName) 만 보유. controller 가 이후 단계에서 종합견적서 시트로
 * modelCode → 단가 lookup 수행.
 *
 * @param vendorName vendor 식별 (예: "에어디자이너", "제이시스템")
 * @param partnerCode 거래처 코드 (parser 가 인식 못하면 null — 사용자가 confirm 시 명시)
 * @param lines 발주 라인
 * @param totalAmount OCR 에서 직접 추출한 총액 (parsed lines 합산과 cross-check 용)
 */
public record ParsedVendorOrder(
        String vendorName,
        String partnerCode,
        List<Line> lines,
        BigDecimal totalAmount) {

    /**
     * 발주 라인 — modelCode 가 종합견적서 시트 lookup key.
     *
     * @param productName 사용자 표시 제품명 (예: "헬로멀티 5kW")
     * @param modelCode 모델 코드 (예: "HM-5000") — 시트 lookup key
     * @param quantity 수량
     * @param unitPrice OCR 에서 직접 추출한 단가 (시트 lookup 으로 override 가능)
     */
    public record Line(
            String productName,
            String modelCode,
            int quantity,
            BigDecimal unitPrice) {
    }

    public static ParsedVendorOrder empty(String vendorName) {
        return new ParsedVendorOrder(vendorName, null, List.of(), BigDecimal.ZERO);
    }
}
