package com.samhanair.logis.auth.web;

import com.samhanair.logis.auth.service.ApprovalLineAuthorizationService;
import com.samhanair.logis.auth.web.dto.ApprovalLineAuthorizeRequest;
import com.samhanair.logis.auth.web.dto.ApprovalLineAuthorizeResponse;
import com.samhanair.logis.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 간 결재라인 action 수행자 인가 API. */
@RestController
@RequestMapping("/auth/internal/approval-line")
@RequiredArgsConstructor
public class ApprovalLineAuthorizeController {

    private final ApprovalLineAuthorizationService authorizationService;

    /**
     * documentType + actionKey 에 지정된 결재자(그룹 또는 개인)인지 확인한다.
     *
     * <p>{@code X-Internal-Token} 검증은 auth-service 의 {@code /auth/internal/**} security filter 가 담당한다.
     */
    @PostMapping("/authorize")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<ApprovalLineAuthorizeResponse> authorize(
            @RequestBody ApprovalLineAuthorizeRequest request) {
        return ApiResponse.ok(authorizationService.authorize(
                request.documentType(), request.actionKey(), request.userId()));
    }
}
