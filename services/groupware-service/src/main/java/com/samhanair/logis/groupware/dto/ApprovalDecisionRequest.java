package com.samhanair.logis.groupware.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 결재 승인/반려 요청 DTO.
 *
 * @param approverId 결재자 user UUID (현재 step 의 결재자와 일치 필수)
 * @param reason 반려 사유 (반려 시 사용, 승인 시 무시)
 */
public record ApprovalDecisionRequest(
        @NotNull UUID approverId,
        @Size(max = 500) String reason
) {
}
