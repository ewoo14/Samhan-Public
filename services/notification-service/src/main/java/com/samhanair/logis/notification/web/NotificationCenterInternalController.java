package com.samhanair.logis.notification.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.notification.service.NotificationCenterService;
import com.samhanair.logis.notification.web.dto.NotificationPublishRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal 알림 발송 endpoint — source service 가 X-Internal-Token 헤더로 호출.
 *
 * <p>InternalTokenFilter (path-prefix=/internal/) 가 X-Internal-Token 검증 + ROLE_MASTER 부여.
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
@Tag(name = "Issue 4 — 통합 알림 센터 (Internal)")
public class NotificationCenterInternalController {

    private final NotificationCenterService service;

    @PostMapping
    @PreAuthorize("hasRole('MASTER')")
    @Operation(summary = "알림 발송 (source service 호출용)")
    public ApiResponse<UUID> publish(@Valid @RequestBody NotificationPublishRequest req) {
        UUID id = service.publish(req);
        return ApiResponse.ok(id);
    }
}
