package com.samhanair.logis.partnerauth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/partner-login 요청. */
public record TryLoginRequest(
        @NotBlank String bizNo,
        @NotBlank String password,
        boolean mobile
) {}
