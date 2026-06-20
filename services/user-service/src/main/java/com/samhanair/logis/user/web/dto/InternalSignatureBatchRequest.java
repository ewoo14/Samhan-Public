package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 내부 서명 배치 조회 요청 - C1a. POST /internal/users/signatures body.
 * slip-service 가 dispatcher/inspector/owner userId 다건의 서명을 한 번에 조회.
 *
 * @param userIds 서명을 조회할 user UUID 목록
 */
public record InternalSignatureBatchRequest(
        @NotNull
        @Size(max = 50, message = "userIds 는 최대 50개까지 허용됩니다")
        List<UUID> userIds
) {
}
