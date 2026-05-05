package com.samhanair.logis.partnerauth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/partner-temp-password 요청. */
public record TempPasswordRequest(
        @NotBlank String bizNo,
        @NotBlank String mobileNo
) {}
