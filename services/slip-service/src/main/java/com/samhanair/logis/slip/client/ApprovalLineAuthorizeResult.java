package com.samhanair.logis.slip.client;

/** auth-service 결재라인 action 인가 결과. */
public record ApprovalLineAuthorizeResult(
        boolean configured,
        boolean allowed
) {
}
