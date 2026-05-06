package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * UserClient bulk verify 요청 DTO — Phase 9 W3 BE backlog #4 채택.
 *
 * @param userIds 검증 대상 user UUID 목록
 */
public record BulkVerifyRequest(
        @NotNull List<UUID> userIds
) {
}
