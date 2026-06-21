package com.samhanair.logis.auth.web.dto;

import java.util.UUID;

/** 결재라인 action_key 기준 내부 인가 요청. */
public record ApprovalLineAuthorizeRequest(
        String documentType,
        String actionKey,
        UUID userId
) {
}
