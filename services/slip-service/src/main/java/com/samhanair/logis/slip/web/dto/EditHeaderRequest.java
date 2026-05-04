package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.DeliveryTag;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 전표 헤더 부분 수정 — null 이 아닌 필드만 적용. DRAFT/SAVED 단계에서만 허용. */
public record EditHeaderRequest(
        UUID partnerId,
        @Size(max = 100) String partnerName,
        DeliveryTag deliveryTag,
        @Size(max = 1000) String memo) {
}
