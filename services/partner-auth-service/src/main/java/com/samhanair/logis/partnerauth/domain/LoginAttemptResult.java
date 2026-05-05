package com.samhanair.logis.partnerauth.domain;

/**
 * 로그인 시도 결과 (audit) — {@link PartnerLoginAttempt#result}.
 *
 * <p>SUCCESS / FAIL_BAD_PASSWORD / FAIL_LOCKED / FAIL_LONG_UNUSED /
 * FAIL_ACCESS_DENIED / FAIL_NOT_FOUND.
 */
public enum LoginAttemptResult {
    SUCCESS,
    FAIL_BAD_PASSWORD,
    FAIL_LOCKED,
    FAIL_LONG_UNUSED,
    FAIL_ACCESS_DENIED,
    FAIL_PW_EXPIRED,
    FAIL_NOT_FOUND
}
