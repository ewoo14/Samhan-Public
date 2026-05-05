package com.samhanair.logis.partnerauth.dto;

import com.samhanair.logis.partnerauth.domain.PartnerStatus;

/** POST /api/v1/auth/partner-register 응답 — 201 PENDING 또는 409. */
public record PartnerRegisterResponse(
        String bizNo,
        PartnerStatus status,
        String message
) {}
