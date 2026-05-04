package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.DeliveryTag;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 전표 상세 응답 — 라인 포함. 단건 GET 및 mutation 응답에 사용. */
public record SlipDetailResponse(
        UUID id,
        SlipType slipType,
        String slipNo,
        LocalDate slipDate,
        int seqNo,
        SlipStatus status,
        UUID partnerId,
        String partnerName,
        UUID sourceWarehouseId,
        UUID destinationWarehouseId,
        DeliveryTag deliveryTag,
        String memo,
        String requesterId,
        String acceptedBy,
        LocalDateTime acceptedAt,
        LocalDateTime completedAt,
        LocalDateTime confirmedAt,
        Long version,
        List<SlipLineResponse> lines) {

    public static SlipDetailResponse from(Slip slip) {
        return new SlipDetailResponse(
                slip.getId(),
                slip.getSlipType(),
                slip.getSlipNo(),
                slip.getSlipDate(),
                slip.getSeqNo(),
                slip.getStatus(),
                slip.getPartnerId(),
                slip.getPartnerName(),
                slip.getSourceWarehouseId(),
                slip.getDestinationWarehouseId(),
                slip.getDeliveryTag(),
                slip.getMemo(),
                slip.getRequesterId(),
                slip.getAcceptedBy(),
                slip.getAcceptedAt(),
                slip.getCompletedAt(),
                slip.getConfirmedAt(),
                slip.getVersion(),
                slip.getLines().stream().map(SlipLineResponse::from).toList());
    }
}
