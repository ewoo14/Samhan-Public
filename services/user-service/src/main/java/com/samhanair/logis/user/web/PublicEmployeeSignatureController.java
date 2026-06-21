package com.samhanair.logis.user.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.user.service.EmployeeSignatureHandoffService;
import com.samhanair.logis.user.web.dto.PublicEmployeeSignatureRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사원 서명 공개 제출 endpoint — NO-AUTH 토큰 게이트 (slice C1b · spec §5.2 · §7).
 *
 * <p>게이트웨이 {@code /api/public/employee-signatures/**} → {@code StripPrefix=1} →
 * 본 컨트롤러 {@code /public/employee-signatures/{token}}. JwtAuthentication 필터 미적용 +
 * {@code StripInboundIdentityHeaders} 로 위조 identity 헤더 strip. user-service SecurityConfig
 * 의 {@code /public/**} permitAll + identity fail-CLOSED ([[feedback_identity_header_authz_antipattern]]).
 */
@RestController
@RequestMapping("/public/employee-signatures")
@RequiredArgsConstructor
public class PublicEmployeeSignatureController {

    private final EmployeeSignatureHandoffService handoffService;

    @PostMapping("/{token}")
    public ApiResponse<Void> submit(
            @PathVariable String token,
            @Valid @RequestBody PublicEmployeeSignatureRequest request) {
        handoffService.submitPublic(token, request.signaturePngBase64(), request.signatureHash());
        return ApiResponse.ok(null);
    }
}
