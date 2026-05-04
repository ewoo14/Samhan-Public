package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.DeliveryTag;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** 전표 요약 응답 — 라인 미포함, 페이지/리스트 용. */
public record SlipResponse(
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
        String requesterId,
        String acceptedBy,
        LocalDateTime acceptedAt,
        LocalDateTime completedAt,
        LocalDateTime confirmedAt,
        Long version) {

    public static SlipResponse from(Slip slip) {
        return new SlipResponse(
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
                slip.getRequesterId(),
                slip.getAcceptedBy(),
                slip.getAcceptedAt(),
                slip.getCompletedAt(),
                slip.getConfirmedAt(),
                slip.getVersion());
    }
}
