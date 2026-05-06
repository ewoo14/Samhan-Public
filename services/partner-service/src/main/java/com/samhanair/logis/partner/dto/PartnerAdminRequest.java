package com.samhanair.logis.partner.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 관리자 거래처 등록/수정 요청 DTO.
 *
 * <p>등록 시 ({@code POST /admin/partners}) 모든 필드 사용. 수정 시 ({@code PUT /admin/partners/{partnerCode}})
 * partnerCode / bizNo 는 path/식별자이므로 변경 불가, name / address / phone / creditLimit 만 반영
 * (creditLimit 변경 시 service 레이어가 PartnerCreditHistory 자동 적재).
 *
 * @param partnerCode 사용자 노출 식별자 (등록 시 필수, 수정 시 무시)
 * @param bizNo 사업자번호 (등록 시 필수, 수정 시 무시)
 * @param name 거래처 상호
 * @param address 주소 (선택)
 * @param phone 연락처 (선택)
 * @param creditLimit 신용한도 (0 이상)
 */
public record PartnerAdminRequest(
        @Size(max = 50) String partnerCode,
        @Size(max = 20) String bizNo,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String address,
        @Size(max = 30) String phone,
        @DecimalMin(value = "0", inclusive = true) BigDecimal creditLimit
) {
}
