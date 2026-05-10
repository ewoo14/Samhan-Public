package com.samhanair.logis.arologis.realtime.web.dto;

import com.samhanair.logis.arologis.realtime.domain.ArologisEditRequest;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestStatus;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestType;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * arologis 수정 요청 응답 DTO — PR-H4b (Phase 12 Step 4b).
 */
public record ArologisEditRequestResponse(
        UUID id,
        UUID entityId,
        UUID requesterId,
        String requesterName,
        EditRequestType requestType,
        String reason,
        EditRequestStatus status,
        EditTargetRole targetRole,
        UUID decidedById,
        String decidedByName,
        String decisionReason,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        LocalDateTime expiresAt) {

    public static ArologisEditRequestResponse from(ArologisEditRequest r) {
        return new ArologisEditRequestResponse(
                r.getId(),
                r.getEntityId(),
                r.getRequesterId(),
                r.getRequesterName(),
                r.getRequestType(),
                r.getReason(),
                r.getStatus(),
                r.getTargetRole(),
                r.getDecidedById(),
                r.getDecidedByName(),
                r.getDecisionReason(),
                r.getRequestedAt(),
                r.getDecidedAt(),
                r.getExpiresAt());
    }
}
