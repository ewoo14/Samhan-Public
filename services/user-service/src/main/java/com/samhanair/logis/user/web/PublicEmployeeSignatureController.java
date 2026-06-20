package com.samhanair.logis.user.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.user.service.EmployeeSignatureHandoffService;
import com.samhanair.logis.user.web.dto.PublicEmployeeSignatureRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *
 * <p>토큰 만료 시 slip {@code PublicSlipController} 패턴대로 CONFLICT → 410 GONE 매핑.
 */
@RestController
@RequestMapping("/public/employee-signatures")
@RequiredArgsConstructor
public class PublicEmployeeSignatureController {

    private final EmployeeSignatureHandoffService handoffService;

    @PostMapping("/{token}")
    public ResponseEntity<ApiResponse<Void>> submit(
            @PathVariable String token,
            @Valid @RequestBody PublicEmployeeSignatureRequest request) {
        try {
            handoffService.submitPublic(token, request.signaturePngBase64(), request.signatureHash());
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (BusinessException ex) {
            // 토큰 만료 = CONFLICT → 410 GONE (slip PublicSlipController 정렬).
            // 이미 사용된 토큰은 그대로 409 (GlobalExceptionHandler CONFLICT 매핑) 보존.
            if (ex.getErrorCode() == ErrorCode.CONFLICT
                    && ex.getMessage() != null && ex.getMessage().contains("만료")) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(ApiResponse.fail(ErrorCode.CONFLICT, ex.getMessage()));
            }
            throw ex;
        }
    }
}
