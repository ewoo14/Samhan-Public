package com.samhanair.logis.accounting.client;

/** 결재라인 action 인가 결과. */
public record ApprovalLineAuthorizeResult(
        boolean configured,
        boolean allowed) {
}
