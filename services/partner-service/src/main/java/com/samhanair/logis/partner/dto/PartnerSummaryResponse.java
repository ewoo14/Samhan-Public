package com.samhanair.logis.partner.dto;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import java.math.BigDecimal;

/**
 * 거래처 페이지 응답 요약 DTO — admin 목록 조회 ({@code GET /admin/partners}) 전용.
 *
 * <p>UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) 일관 — partnerCode 만 노출.
 * 목록 화면에 필요한 최소 필드 (partnerCode / name / bizNo / phone / status / creditLimit /
 * outstandingBalance) 만 포함하여 응답 페이로드 최소화. 단건 상세는 별도 {@link PartnerAdminResponse}.
 *
 * <p>Phase 10 W10-6 — 50 partner 시드 검증을 위한 조회 endpoint 신설 시 도입.
 *
 * @param partnerCode 사용자 노출 식별자
 * @param name 거래처 상호
 * @param bizNo 사업자번호
 * @param phone 연락처
 * @param status 거래 상태
 * @param creditLimit 신용한도
 * @param outstandingBalance 미수금 잔액
 */
public record PartnerSummaryResponse(
        String partnerCode,
        String name,
        String bizNo,
        String phone,
        PartnerStatus status,
        BigDecimal creditLimit,
        BigDecimal outstandingBalance
) {

    public static PartnerSummaryResponse from(Partner p) {
        return new PartnerSummaryResponse(
                p.getPartnerCode(),
                p.getName(),
                p.getBizNo(),
                p.getPhone(),
                p.getStatus(),
                p.getCreditLimit(),
                p.getOutstandingBalance());
    }
}
