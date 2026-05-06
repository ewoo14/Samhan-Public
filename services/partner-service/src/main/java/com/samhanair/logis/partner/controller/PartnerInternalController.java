package com.samhanair.logis.partner.controller;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.partner.dto.PartnerInternalResponse;
import com.samhanair.logis.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 형제 service (현재 slip-service) 가 partnerCode 로 거래처 마스터 정보를 조회하는 internal endpoint.
 *
 * <p>본 endpoint 도입의 1차 동기 = M5 slip-service 의 partnerCode → partnerId lookup 의존성 해소
 * (M-PHASE-9-readiness §2-3). slip-service 측 client 구현 시점은 Phase 9 W5 또는 Phase 10 cutover
 * 시점에 결정 (별도 PR scope).
 *
 * <p>인증 = X-Internal-Token 필수 ({@code InternalTokenFilter} 가 ROLE_MASTER 부여).
 * 토큰 누락 시 익명 요청으로 처리되어 Spring Security AuthorizationFilter 의
 * AccessDeniedException 으로 403 응답. 토큰 불일치 시 InternalTokenFilter 가
 * 직접 401 응답 (filter chain 단절).
 */
@RestController
@RequestMapping("/internal/partners")
@RequiredArgsConstructor
public class PartnerInternalController {

    private final PartnerService partnerService;

    /**
     * partnerCode 로 거래처 마스터 lookup (slip-service M5 의존성 해소용).
     *
     * @param partnerCode 사용자 노출 식별자 (path)
     * @return 200 + PartnerInternalResponse (partnerId UUID + 마스터 + 신용 정보)
     *         ; 미존재 시 404 NOT_FOUND ; 토큰 누락 시 403 FORBIDDEN ; 토큰 불일치 시 401 UNAUTHORIZED
     */
    @Operation(summary = "partnerCode 로 거래처 마스터 lookup",
            description = "slip-service M5 의 partnerCode → partnerId lookup 의존성 해소. X-Internal-Token 필수.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "내부 토큰 불일치 (InternalTokenFilter 직접 응답)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "내부 토큰 누락 (Spring Security AccessDeniedException)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "거래처 미존재")
    })
    @GetMapping("/{partnerCode}")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<PartnerInternalResponse> lookup(@PathVariable String partnerCode) {
        return ApiResponse.ok(PartnerInternalResponse.from(partnerService.findByCode(partnerCode)));
    }
}
