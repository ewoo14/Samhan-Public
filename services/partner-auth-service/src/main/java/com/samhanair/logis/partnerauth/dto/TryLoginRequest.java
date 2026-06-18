package com.samhanair.logis.partnerauth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /api/v1/auth/partner-login 요청 — BizGate 비밀번호는 숫자 4자리 PIN. */
public record TryLoginRequest(
        @NotBlank String bizNo,
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "비밀번호는 숫자 4자리 PIN이어야 합니다")
        String password,
        boolean mobile
) {}
