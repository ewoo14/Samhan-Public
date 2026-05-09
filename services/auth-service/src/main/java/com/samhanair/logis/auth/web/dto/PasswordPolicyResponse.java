package com.samhanair.logis.auth.web.dto;

/** Response for {@code GET /auth/password/policy} — UI 표시용. Phase 10 P0-2. */
public record PasswordPolicyResponse(
        int minLength,
        int maxLength,
        boolean requireLetter,
        boolean requireDigit,
        boolean requireSpecial,
        int historyReuseBlock,
        int maxFailedLoginAttempts,
        long resetTokenTtlMinutes,
        String description) {
}
