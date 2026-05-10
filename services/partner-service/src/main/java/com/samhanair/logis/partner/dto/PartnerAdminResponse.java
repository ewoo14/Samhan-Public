package com.samhanair.logis.partner.dto;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import java.math.BigDecimal;

/**
 * 관리자 거래처 응답 DTO.
 *
 * <p>UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) 일관 — partnerCode 만 노출,
 * 내부 UUID 는 응답에 포함하지 않는다. 관리자 admin 화면도 partnerCode 만으로 후속 조회/수정 호출.
 *
 * <p>PR-G1 backlog #2 — 출고전표 작성 화면 "거래처 자동 채움" 버튼이 본 응답을 사용하여 customerTel
 * / customerAddress / customerRepresentative 12 컬럼 필드를 자동 fill. 사용자 수정 가능 (snapshot).
 *
 * @param partnerCode 사용자 노출 식별자
 * @param bizNo 사업자번호
 * @param name 거래처 상호
 * @param address 주소
 * @param phone 연락처
 * @param representative 대표자명 (PR-G1 신규)
 * @param creditLimit 신용한도
 * @param outstandingBalance 미수금 잔액
 * @param status 거래 상태
 */
public record PartnerAdminResponse(
        String partnerCode,
        String bizNo,
        String name,
        String address,
        String phone,
        String representative,
        BigDecimal creditLimit,
        BigDecimal outstandingBalance,
        PartnerStatus status
) {

    public static PartnerAdminResponse from(Partner p) {
        return new PartnerAdminResponse(
                p.getPartnerCode(),
                p.getBizNo(),
                p.getName(),
                p.getAddress(),
                p.getPhone(),
                p.getRepresentative(),
                p.getCreditLimit(),
                p.getOutstandingBalance(),
                p.getStatus());
    }
}
