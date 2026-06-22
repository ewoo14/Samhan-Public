package com.samhanair.logis.arologis.controller;

import com.samhanair.logis.arologis.service.ArologisMyPermissionService;
import com.samhanair.logis.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아로로지스 현재 사용자 권한 조회 API.
 *
 * <p>MASTER 전용 권한 관리 API({@link ArologisPermissionAdminController})와 분리하여,
 * 인증된 모든 백오피스 사용자가 자신의 {@code arologis.*} page-code 권한만 조회할 수 있게 한다.
 * 응답은 FE {@code canAccess(pageCode, action)} 캐시가 직접 소비하는 action enum name 목록이다.
 */
@RestController
@RequestMapping("/admin/arologis/permissions/my")
@RequiredArgsConstructor
public class ArologisMyPermissionsController {

    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final ArologisMyPermissionService arologisMyPermissionService;

    /**
     * 현재 사용자에게 허용된 아로로지스 page-code/action 목록을 조회한다.
     *
     * <p>게이트웨이/JWT 필터가 주입한 {@code X-User-Role} 을 기준으로 중앙 role matrix 의 해당 행만
     * 변환한다. 매트릭스에 없는 롤은 200 + 빈 map 으로 fail-closed 한다.
     *
     * @param role 원본 사용자 롤 헤더
     * @return pageCode → 허용 action enum name 목록
     */
    @Operation(summary = "아로로지스 현재 사용자 권한 조회")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, List<String>>> getMyPermissions(
            @RequestHeader(USER_ROLE_HEADER) String role) {
        return ApiResponse.ok(arologisMyPermissionService.getMyPermissions(role));
    }
}
