package com.samhanair.logis.auth.web.dto;

/** 결재라인 action_key 기준 내부 인가 결과. */
public record ApprovalLineAuthorizeResponse(
        boolean configured,
        boolean allowed
) {
}
