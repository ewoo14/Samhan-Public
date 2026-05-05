package com.samhanair.logis.partnerauth.dto;

import com.samhanair.logis.partnerauth.client.PartnerConfigDto;
import com.samhanair.logis.partnerauth.domain.PartnerStatus;

/**
 * POST /api/v1/auth/partner-login 응답.
 *
 * <p>{@code status} = OK 시 token + config (M3 dc-config-service RPC 결과) 반환.
 * 그 외 status (LOCKED/LONG_UNUSED/...) 는 token=null + config=null + message 만 반환.
 */
public record TryLoginResponse(
        PartnerStatus status,
        String token,
        PartnerConfigDto config,
        String message
) {}
