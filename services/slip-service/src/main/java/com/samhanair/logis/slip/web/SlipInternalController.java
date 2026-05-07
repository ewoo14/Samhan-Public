package com.samhanair.logis.slip.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.service.SlipSignatureService;
import com.samhanair.logis.slip.web.dto.InternalSignatureRegistrationRequest;
import com.samhanair.logis.slip.web.dto.InternalSignatureResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal 전자서명 endpoint — Phase 10 W10-4 (PR #99) 신규.
 *
 * <p>arologis-service 의 SlipClient (driver-app 정차 완료 시 호출) 가 본 controller 의 endpoint 를
 * 통해 전자서명을 slip-service 에 전파한다.
 *
 * <ul>
 *   <li>{@code POST /internal/slips/{slipId}/signatures} — APP source 서명 등록 (driver-app 캡처)</li>
 *   <li>{@code GET /internal/slips/by-partner/{partnerId}/recent} — partnerId 의 최근 활성 슬립 lookup
 *       (arologis SlipResolver 의 partnerCode → slipId 매핑 단계)</li>
 * </ul>
 *
 * <p>인증: X-Internal-Token 헤더 → ROLE_MASTER 권한으로 통과 ({@link com.samhanair.logis.slip.config.SecurityConfig}).
 * SecurityConfig 가 InternalTokenFilter 를 등록하여 {@code /internal/**} prefix 한정 인증 처리.
 *
 * <p>UUID 가드: GET /by-partner endpoint 는 응답에 slipNo (사용자 노출 식별자) 만 포함 — slipId 는
 * 호출자(arologis-service) 내부 상태로 보존되되 화면 노출 시 슬립번호 우선.
 */
@Slf4j
@RestController
@RequestMapping("/internal/slips")
@RequiredArgsConstructor
public class SlipInternalController {

    private final SlipSignatureService signatureService;

    /**
     * Internal 전자서명 등록 — arologis-service 가 driver-app 캡처 서명을 slip-service 로 전파.
     *
     * <p>본 endpoint 는 APP source 만 허용 — LINK 는 기존 공개 모바일 endpoint 사용. controller 진입
     * 시점 X-Internal-Token 으로 ROLE_MASTER 인증 + @PreAuthorize 추가 가드.
     *
     * <p>응답 형식: {@code ApiResponse<InternalSignatureResponse>} wrapper (W10-3 F-3 채택 — IT 의무).
     *
     * @param slipId 슬립 UUID
     * @param request 등록 요청
     * @return ApiResponse wrapper 안 InternalSignatureResponse
     */
    @Operation(summary = "Internal 전자서명 등록 (W10-4 — arologis driver-app)",
            description = "X-Internal-Token 인증. APP source 만 허용 (LINK 는 공개 모바일 endpoint 사용)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "등록 성공 (ApiResponse wrapper, ok=true)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_INPUT — source != APP / imageRef blank / capturedAt null"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "X-Internal-Token 누락/불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "슬립 미발견"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "SIGNABLE_STATUSES 미충족 / 동시 수정 충돌")
    })
    @PostMapping("/{slipId}/signatures")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<InternalSignatureResponse> registerSignature(
            @PathVariable UUID slipId,
            @Valid @RequestBody InternalSignatureRegistrationRequest request) {
        log.info("W10-4 internal signature register — slipId={}, source={}, isDriver={}",
                slipId, request.signatureSource(),
                request.driverCode() != null && !request.driverCode().isBlank());
        return ApiResponse.ok(signatureService.registerFromInternal(slipId, request));
    }

    /**
     * partnerId 기준 최근 활성 슬립 lookup — arologis SlipResolver 가 호출.
     *
     * <p>arologis-service 의 partnerCode → partnerId resolve (PartnerClient.findByCode) 결과를 받아
     * slipId 로 변환하기 위한 GET endpoint. 응답에는 slipId + slipNo 모두 포함하되 사용자 노출 시는
     * slipNo 만 사용해야 한다.
     *
     * @param partnerId 거래처 UUID
     * @return ApiResponse wrapper 안 LookupResponse (slipId + slipNo + status)
     */
    @Operation(summary = "Internal 거래처 최근 활성 슬립 lookup (W10-4 — arologis SlipResolver)",
            description = "X-Internal-Token 인증. order by slipDate DESC, seqNo DESC 의 첫 슬립 1건")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "lookup 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "X-Internal-Token 누락/불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "해당 partnerId 의 활성 슬립 없음")
    })
    @GetMapping("/by-partner/{partnerId}/recent")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<LookupResponse> findRecentByPartner(@PathVariable UUID partnerId) {
        Slip slip = signatureService.findRecentByPartnerId(partnerId);
        return ApiResponse.ok(new LookupResponse(
                slip.getId(),
                slip.getSlipNo(),
                slip.getStatus().name()));
    }

    /**
     * partnerCode 기준 최근 활성 슬립 lookup — Phase 10 W10-4 종합 TM (BE-1 채택) 신규.
     *
     * <p>arologis-service 의 SlipResolver 가 카톡 파싱 partnerCode (사용자 노출 식별자) 로 직접 호출.
     * slip-service 가 자체 PartnerInternalClient 로 partner-service 의
     * {@code GET /internal/partners/{partnerCode}} 를 호출하여 partnerId UUID resolve 후 lookup.
     *
     * <p>graceful empty 패턴 (404 미반환) — partner-service 매핑 실패 또는 슬립 미존재 시 200 + data=null.
     * 호출자(arologis SlipResolver) 가 자체 INSERT 만 graceful skip (slipBridged=false) 처리.
     *
     * @param partnerCode 사용자 노출 식별자
     * @return ApiResponse wrapper 안 LookupResponse (매핑 실패 시 data=null)
     */
    @Operation(summary = "Internal partnerCode 최근 활성 슬립 lookup (W10-4 종합 TM — arologis SlipResolver)",
            description = "X-Internal-Token 인증. partner-service /internal/partners/{partnerCode} 위임 후 slipId resolve. "
                    + "매핑 실패 시 200 + data=null (404 미반환, graceful fallback).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "lookup 성공 (data) 또는 매핑 실패 (data=null)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "X-Internal-Token 누락/불일치")
    })
    @GetMapping("/by-partner-code/{partnerCode}/recent")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<LookupResponse> findRecentByPartnerCode(@PathVariable String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "partnerCode 필수");
        }
        Optional<Slip> slipOpt = signatureService.findRecentByPartnerCode(partnerCode);
        if (slipOpt.isEmpty()) {
            // graceful empty — 200 + data=null (BE-1 채택, 호출자 자체 fallback 보존)
            return ApiResponse.ok(null);
        }
        Slip slip = slipOpt.get();
        return ApiResponse.ok(new LookupResponse(
                slip.getId(),
                slip.getSlipNo(),
                slip.getStatus().name()));
    }

    /**
     * partner-recent lookup 응답 record — Phase 10 W10-4 신규.
     *
     * @param slipId 슬립 UUID (호출자 내부 상태용)
     * @param slipNo 전표번호 (사용자 노출 식별자)
     * @param status 슬립 상태 (SIGNABLE_STATUSES 가드용 hint)
     */
    public record LookupResponse(UUID slipId, String slipNo, String status) {}
}
