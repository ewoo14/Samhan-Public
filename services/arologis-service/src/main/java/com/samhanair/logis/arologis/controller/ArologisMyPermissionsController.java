package com.samhanair.logis.arologis.controller;

import com.samhanair.logis.arologis.service.ArologisMyPermissionService;
import com.samhanair.logis.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
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

    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";
    private static final String AROLOGIS_ROLE_AUTHORITY_PREFIX = "ROLE_AROLOGIS_";

    private final ArologisMyPermissionService arologisMyPermissionService;

    /**
     * 현재 사용자에게 허용된 아로로지스 page-code/action 목록을 조회한다.
     *
     * <p>신뢰 경계: inbound {@code X-User-Role} 헤더는 클라이언트가 위조할 수 있으므로
     * 권한 판정 입력으로 사용하지 않는다. {@link com.samhanair.logis.arologis.config.ArologisJwtFilter}
     * 가 서명 JWT claim 검증 후 적재한 {@code ROLE_AROLOGIS_<role>} authority 만 role 원천으로 신뢰한다.
     * 인증주체 또는 role authority 가 없으면 중앙 매트릭스를 조회하지 않고 200 + 빈 map 으로
     * fail-closed 한다.
     *
     * @return pageCode → 허용 action enum name 목록
     */
    @Operation(summary = "아로로지스 현재 사용자 권한 조회")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, List<String>>> getMyPermissions() {
        String role = roleFromSecurityContext();
        if (role == null) {
            return ApiResponse.ok(Map.of());
        }
        return ApiResponse.ok(arologisMyPermissionService.getMyPermissions(role));
    }

    private String roleFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(AROLOGIS_ROLE_AUTHORITY_PREFIX))
                .map(authority -> authority.substring(ROLE_AUTHORITY_PREFIX.length()))
                .findFirst()
                .orElse(null);
    }
}
