package com.samhanair.logis.partnerorder.vendor.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * vendor 발주서 upload 응답 — OCR + parser + 단가 lookup + DC 적용 미리보기.
 *
 * <p>UUID 비공개 가드 — productId 노출 X. 사용자 식별자 (vendorName / partnerCode / modelCode /
 * productName) 만 응답.
 *
 * @param vendorName 인식된 vendor (또는 사용자 명시)
 * @param partnerCode 거래처 코드 (parser 인식 또는 사용자 명시)
 * @param ocrText 추출된 raw text (admin 검증용 — 길면 잘림)
 * @param parsedLines 파싱된 라인 + 단가 + DC 적용
 * @param totalAmount 라인 합산 (DC 적용 후)
 * @param parsedTotal OCR 에서 직접 추출한 총액 (cross-check 용)
 * @param suggestions 사용자에게 제공할 안내 (예: "단가 누락 라인 N건", "DC 미적용")
 */
public record VendorOrderUploadResponse(
        String vendorName,
        String partnerCode,
        String ocrText,
        List<PreviewLine> parsedLines,
        BigDecimal totalAmount,
        BigDecimal parsedTotal,
        List<String> suggestions) {

    /**
     * 미리보기 라인 — confirm 시 그대로 전달.
     *
     * @param productName 사용자 표시 제품명
     * @param modelCode 모델코드 (시트 lookup key + 사용자 식별자)
     * @param quantity 수량
     * @param unitPrice 단가 (시트 lookup 우선, 없으면 OCR 단가)
     * @param dcRate DC 적용율 (0.0~1.0). 0 = DC 미적용
     * @param finalPrice 단가 * (1 - dcRate) — 최종 적용 단가
     * @param subtotal finalPrice * quantity
     * @param source 단가 source ("CATALOG" / "OCR" / "MANUAL")
     */
    public record PreviewLine(
            String productName,
            String modelCode,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal dcRate,
            BigDecimal finalPrice,
            BigDecimal subtotal,
            String source) {
    }
}
