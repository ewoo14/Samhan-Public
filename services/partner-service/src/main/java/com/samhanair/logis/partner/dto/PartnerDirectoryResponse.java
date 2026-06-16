package com.samhanair.logis.partner.dto;

import com.samhanair.logis.partner.domain.Partner;
import java.util.UUID;

/**
 * 종합견적서 거래처 directory internal 응답.
 *
 * <p>형제 service 전용 X-Internal-Token endpoint 응답이므로 partnerId UUID 를 포함한다.
 * estimate-app 화면 식별은 partnerCode/bizNo 만 사용한다.
 *
 * @param partnerId 거래처 UUID
 * @param partnerCode 사용자 노출 거래처 코드
 * @param name 거래처 상호
 * @param bizNo 사업자등록번호
 * @param representative 대표자명
 * @param address 주소
 * @param phone 대표 연락처
 * @param group 거래처분류1
 * @param note 특이사항
 */
public record PartnerDirectoryResponse(
        UUID partnerId,
        String partnerCode,
        String name,
        String bizNo,
        String representative,
        String address,
        String phone,
        String group,
        String note
) {

    public static PartnerDirectoryResponse from(Partner partner) {
        return new PartnerDirectoryResponse(
                partner.getId(),
                partner.getPartnerCode(),
                partner.getName(),
                partner.getBizNo(),
                partner.getRepresentative(),
                partner.getAddress(),
                partner.getPhone(),
                partner.getPartnerGroup1(),
                partner.getNote());
    }
}
