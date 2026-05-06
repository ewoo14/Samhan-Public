package com.samhanair.logis.partner.controller;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.partner.dto.CreditHistoryResponse;
import com.samhanair.logis.partner.dto.PartnerAdminRequest;
import com.samhanair.logis.partner.dto.PartnerAdminResponse;
import com.samhanair.logis.partner.service.PartnerCreditService;
import com.samhanair.logis.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거래처 마스터 관리자 CRUD endpoint.
 *
 * <p>인증 = X-User-* 헤더 (gateway 경유) + {@code @PreAuthorize} 권한 가드 (MASTER / MANAGER).
 * SALES / WAREHOUSE 등 일반 사용자는 admin 작업 불가. 본 endpoint 는 internal token 필요 X.
 *
 * <p>모든 응답은 {@link PartnerAdminResponse} 사용 — UUID 비공개 가드 (memory feedback_uuid_no_user_visibility)
 * 일관 적용. partnerCode 만 응답에 노출, 후속 조회/수정도 partnerCode path variable.
 */
@RestController
@RequestMapping("/admin/partners")
@RequiredArgsConstructor
public class PartnerAdminController {

    private final PartnerService partnerService;
    private final PartnerCreditService creditService;

    /**
     * 신규 거래처 등록.
     *
     * @return 200 + PartnerAdminResponse ; 중복 partnerCode/bizNo → 409 CONFLICT
     */
    @Operation(summary = "신규 거래처 등록", description = "MASTER / MANAGER 권한 필요")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필수값 누락 / 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "partnerCode 또는 bizNo 중복")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<PartnerAdminResponse> create(@Valid @RequestBody PartnerAdminRequest req) {
        return ApiResponse.ok(PartnerAdminResponse.from(partnerService.register(req)));
    }

    /**
     * partnerCode 로 거래처 단건 조회.
     */
    @Operation(summary = "거래처 단건 조회 (partnerCode)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "거래처 미존재")
    })
    @GetMapping("/{partnerCode}")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','SALES','ACCOUNTANT')")
    public ApiResponse<PartnerAdminResponse> findOne(@PathVariable String partnerCode) {
        return ApiResponse.ok(PartnerAdminResponse.from(partnerService.findByCode(partnerCode)));
    }

    /**
     * 거래처 프로필 수정 (name / address / phone).
     *
     * <p>creditLimit 변경은 본 endpoint 가 아닌 별도 사용 — 신용한도 변경은 history 적재 의무.
     */
    @Operation(summary = "거래처 프로필 수정", description = "name / address / phone 만 변경. creditLimit 변경은 별도 사용")
    @PutMapping("/{partnerCode}")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<PartnerAdminResponse> update(@PathVariable String partnerCode,
                                                    @Valid @RequestBody PartnerAdminRequest req) {
        return ApiResponse.ok(PartnerAdminResponse.from(partnerService.updateProfile(partnerCode, req)));
    }

    /**
     * 거래처 soft-delete. partial unique index 가 partnerCode 재사용 허용.
     */
    @Operation(summary = "거래처 soft-delete")
    @DeleteMapping("/{partnerCode}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String partnerCode, Principal principal) {
        String actor = principal != null ? principal.getName() : "system";
        partnerService.delete(partnerCode, actor);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 신용 거래 이력 페이지 조회.
     */
    @Operation(summary = "신용 거래 이력 페이지 조회",
            description = "SLIP_ISSUED / PAYMENT / CREDIT_LIMIT_CHANGE 시간 역순")
    @GetMapping("/{partnerCode}/credit-history")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','ACCOUNTANT')")
    public ApiResponse<List<CreditHistoryResponse>> findHistory(@PathVariable String partnerCode,
                                                                Pageable pageable) {
        Page<CreditHistoryResponse> page = creditService.findHistory(partnerCode, pageable)
                .map(CreditHistoryResponse::from);
        return ApiResponse.ok(page.getContent());
    }
}
