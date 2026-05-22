package com.samhanair.logis.auth.web;

import com.samhanair.logis.auth.service.DynamicPermissionService;
import com.samhanair.logis.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 동적 RBAC 권한 조회 API.
 *
 * <p>{@code /auth/internal/**} prefix 로 {@code X-Internal-Token} 검증을 강제한다.
 */
@RestController
@RequestMapping("/auth/internal/permissions")
@RequiredArgsConstructor
public class PermissionInternalController {

    private final DynamicPermissionService permissionService;

    /**
     * 단일 권한 조회 — 타 서비스가 권한 체크 시 호출.
     *
     * @param roleCode 역할 코드
     * @param pageCode 페이지 코드
     * @param type     권한 유형 (VIEW 또는 EDIT, 기본값 EDIT)
     * @return 권한 허용 여부 {@code {"allowed": true/false}}
     */
    @GetMapping("/check")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PermissionCheckResponse> checkPermission(
            @RequestParam String roleCode,
            @RequestParam String pageCode,
            @RequestParam(defaultValue = "EDIT") String type) {
        boolean allowed = permissionService.canAccess(roleCode, pageCode, type);
        return ApiResponse.ok(new PermissionCheckResponse(allowed));
    }

    /**
     * 단일 권한 조회 응답 DTO.
     *
     * @param allowed 권한 부여 여부
     */
    public record PermissionCheckResponse(boolean allowed) {
    }
}
