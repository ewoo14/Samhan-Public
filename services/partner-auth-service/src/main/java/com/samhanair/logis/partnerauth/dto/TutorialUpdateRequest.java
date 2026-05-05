package com.samhanair.logis.partnerauth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/v1/auth/partner-tutorial 요청.
 *
 * <p>{@code platform} = "PC" 또는 "MOBILE". {@code done} = true (완료 표시).
 */
public record TutorialUpdateRequest(
        @NotBlank String bizNo,
        @NotBlank @Pattern(regexp = "PC|MOBILE", message = "platform 은 PC|MOBILE")
        String platform,
        @NotNull Boolean done
) {}
