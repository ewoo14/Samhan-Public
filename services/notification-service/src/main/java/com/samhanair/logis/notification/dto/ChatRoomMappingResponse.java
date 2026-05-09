package com.samhanair.logis.notification.dto;

import com.samhanair.logis.notification.domain.MappingSource;
import com.samhanair.logis.notification.domain.PartnerChatRoomMapping;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 단톡방 매핑 단건 응답 DTO.
 *
 * <p>UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) — id 필드는 admin 화면 한정 노출
 * (DELETE 단건 path variable 용). 사용자 노출 영역에는 partner_business_name_snapshot + chat_room_name
 * 만 사용한다.
 */
public record ChatRoomMappingResponse(
        UUID id,
        String partnerCode,
        String partnerBusinessName,
        String chatRoomName,
        MappingSource source,
        LocalDateTime notionCreatedAt,
        LocalDateTime createdAt
) {

    public static ChatRoomMappingResponse from(PartnerChatRoomMapping m) {
        return new ChatRoomMappingResponse(
                m.getId(),
                m.getPartnerCode(),
                m.getPartnerBusinessNameSnapshot(),
                m.getChatRoomName(),
                m.getSource(),
                m.getNotionCreatedAt(),
                m.getCreatedAt());
    }
}
