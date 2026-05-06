package com.samhanair.logis.user.web.dto;

import java.util.Map;
import java.util.UUID;

/**
 * UserClient bulk verify 응답 DTO — Phase 9 W3 BE backlog #4 채택.
 *
 * @param exists user UUID → 존재 여부 매핑 (요청한 모든 ID 포함, true=존재 / false=미존재)
 */
public record BulkVerifyResponse(
        Map<UUID, Boolean> exists
) {
}
