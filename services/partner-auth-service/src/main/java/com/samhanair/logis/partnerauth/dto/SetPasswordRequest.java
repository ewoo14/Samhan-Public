package com.samhanair.logis.partnerauth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH /api/v1/auth/partner-password 요청 — 비밀번호 설정/변경. */
public record SetPasswordRequest(
        @NotBlank String bizNo,
        @NotBlank @Size(min = 8, max = 100) String newPassword,
        // 변경 시 현재 비밀번호 (NEED_PW_INPUT 단계). NEED_PW_SET 단계는 null 허용.
        String currentPassword
) {}
