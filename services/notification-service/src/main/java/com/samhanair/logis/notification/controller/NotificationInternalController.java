package com.samhanair.logis.notification.controller;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.notification.domain.NotificationRequest;
import com.samhanair.logis.notification.dto.NotificationAdminResponse;
import com.samhanair.logis.notification.dto.NotificationSendRequest;
import com.samhanair.logis.notification.dto.NotificationStatusResponse;
import com.samhanair.logis.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 형제 service (groupware-service / partner-order-service / slip-service / dashboard-service) 가
 * 알림 발송을 트리거하는 internal endpoint.
 *
 * <p>인증 = X-Internal-Token 필수. 토큰 누락 시 익명 요청 → AuthorizationFilter 의
 * AccessDeniedException → 403. 토큰 불일치 시 InternalTokenFilter 가 직접 401 응답.
 *
 * <p>UUID 비공개 가드 — 본 응답은 내부 형제 service 만 받는다 (사용자 화면 직접 노출 X).
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationInternalController {

    private final NotificationService notificationService;

    /**
     * backend service-to-service 발송 요청 — 결재선 알림 / 주문 상태 변경 / 배송 SMS 등.
     */
    @Operation(summary = "발송 요청 (Internal)",
            description = "형제 service 가 알림을 트리거할 때 호출. X-Internal-Token 필수")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "발송 요청 등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "내부 토큰 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "내부 토큰 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "수신자 미존재")
    })
    @PostMapping("/send")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<ApiResponse<NotificationAdminResponse>> send(@Valid @RequestBody NotificationSendRequest req) {
        NotificationRequest entity = notificationService.send(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(NotificationAdminResponse.from(entity)));
    }

    /**
     * 발송 상태 조회 — 형제 service 의 polling / dashboard / 재시도 결정 용.
     */
    @Operation(summary = "발송 상태 조회 (Internal)")
    @GetMapping("/{requestId}/status")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<NotificationStatusResponse> status(@PathVariable UUID requestId) {
        NotificationRequest entity = notificationService.findById(requestId);
        return ApiResponse.ok(NotificationStatusResponse.from(entity));
    }
}
