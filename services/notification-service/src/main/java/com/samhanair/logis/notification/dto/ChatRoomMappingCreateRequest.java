package com.samhanair.logis.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 단톡방 매핑 단건 등록 요청 (admin 직접 입력 — source=MANUAL).
 *
 * <p>사용자 명시: partner_code 직접 입력 (사업자명 lookup 우회). business_name 은 화면 표시용 snapshot.
 */
public record ChatRoomMappingCreateRequest(
        @NotBlank @Size(max = 50) String partnerCode,
        @NotBlank @Size(max = 200) String partnerBusinessName,
        @NotBlank @Size(max = 200) String chatRoomName
) {
}
