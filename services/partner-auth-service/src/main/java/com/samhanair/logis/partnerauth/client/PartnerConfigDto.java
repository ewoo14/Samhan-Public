package com.samhanair.logis.partnerauth.client;

import java.util.List;
import java.util.Map;

/**
 * dc-config-service (M3) RPC 응답 — 거래처 설정.
 *
 * <p>설계서 §3 의 {@code TryLoginResponse.config} nested object 구조.
 * W3 정식 구현 시점에 M3 의 실제 DTO 와 1:1 매핑 예정 (현재는 minimal placeholder).
 */
public record PartnerConfigDto(
        String partnerName,
        String representativeName,
        String mobileNo,
        List<String> allowedFeatures,
        Map<String, Object> options
) {}
