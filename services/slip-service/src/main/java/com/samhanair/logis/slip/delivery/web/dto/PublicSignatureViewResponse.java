package com.samhanair.logis.slip.delivery.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 인수자 view 응답 — Slice C (signature-slice-C Plan §2 + design mobile-spec.md §2.2).
 *
 * <p>{@code GET /public/signatures/{shareToken}} 응답.
 * UUID 비공개 가드 (memory {@code feedback_uuid_no_user_visibility.md}): slip.id / signature.id 미노출.
 * slipNo + 거래처명 + 라인 정보 만으로 인수증 표시.
 *
 * @param slip read-only 슬립 핵심 정보 (UUID 없음)
 * @param signature 서명 메타 (PNG base64 + 해시 short prefix)
 * @param shareTokenExpiresAt 본 토큰 만료 시각 (인수자 표시용)
 */
public record PublicSignatureViewResponse(
        Slip slip,
        Signature signature,
        LocalDateTime shareTokenExpiresAt) {

    /**
     * 인수증 표시용 슬립 정보 — UUID 미노출.
     *
     * @param slipNo 전표번호 ({@code yyyy/MM/dd-N})
     * @param partnerName 거래처명 snapshot
     * @param deliveryDate 배송일 (slipDate)
     * @param lines 라인 목록 (productName + quantity 만)
     * @param totalAmount 총 금액 (lineTotal 합계)
     */
    public record Slip(
            String slipNo,
            String partnerName,
            LocalDate deliveryDate,
            List<Line> lines,
            BigDecimal totalAmount) {

        /**
         * @param itemName 품목명 (productName)
         * @param specification 규격 (Slice A)
         * @param quantity 수량
         */
        public record Line(
                String itemName,
                String specification,
                int quantity) {
        }
    }

    /**
     * 서명 메타 — PNG 와 해시 short prefix 만 노출.
     *
     * @param signerName 인수자명
     * @param signedAt 서명 시각
     * @param signaturePngBase64 PNG data URI
     * @param signatureHashShort 해시 앞 8자 (전체 64자 미노출 — 인수자 화면 단축 표시)
     */
    public record Signature(
            String signerName,
            LocalDateTime signedAt,
            String signaturePngBase64,
            String signatureHashShort) {
    }
}
