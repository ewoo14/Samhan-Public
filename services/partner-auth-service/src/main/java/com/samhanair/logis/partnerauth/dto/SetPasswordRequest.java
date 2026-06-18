package com.samhanair.logis.partnerauth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** PATCH /api/v1/auth/partner-password 요청 — BizGate 비밀번호 설정/변경(숫자 4자리 PIN). */
public record SetPasswordRequest(
        @NotBlank String bizNo,
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "비밀번호는 숫자 4자리 PIN이어야 합니다")
        String newPassword,
        // 변경 시 현재 비밀번호 (NEED_PW_INPUT 단계). NEED_PW_SET 단계는 null 허용.
        @Pattern(regexp = "\\d{4}", message = "현재 비밀번호는 숫자 4자리 PIN이어야 합니다")
        String currentPassword
) {}
