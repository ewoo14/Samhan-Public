package com.samhanair.logis.user.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.RoleChangeHistory;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.RoleChangeHistoryRepository;
import com.samhanair.logis.user.service.EmployeeProvisioningService;
import com.samhanair.logis.user.web.dto.AdminUserListResponse;
import com.samhanair.logis.user.web.dto.EmployeeResponse;
import com.samhanair.logis.user.web.dto.RoleHistoryResponse;
import com.samhanair.logis.user.web.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 관리 admin endpoint — Phase 10 P0-5.
 *
 * <p>frontend {@code /admin/users} 페이지 backing. UUID 비공개 — 응답 DTO 가 {@code id} 를
 * 보유하지만 화면 routing key 로만 사용, 사용자 노출 라벨은 fullName / loginId 사용.
 *
 * <p>인증 = X-User-Id 헤더 + {@code @PreAuthorize}. 권한 가드:
 * <ul>
 *   <li>목록/조회/role-history — MASTER / MANAGER</li>
 *   <li>disable / enable — MASTER 만 (잘못된 비활성화 회복 비용 큼)</li>
 *   <li>role 변경 — MASTER 만 (auth-service 연쇄 영향)</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final String CALLER_HEADER = "X-User-Id";

    private final EmployeeProvisioningService provisioningService;
    private final EmployeeRepository employeeRepository;
    private final RoleChangeHistoryRepository roleHistoryRepository;

    /**
     * 사용자 목록 조회 — q / role / departmentId 필터 + 페이지네이션.
     *
     * <p>q 는 fullName / loginId / email LIKE (대소문자 무시). frontend 의 검색창 1개로 3 컬럼 동시 검색.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<AdminUserListResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UUID departmentId) {
        String normalizedQ = (q == null || q.isBlank()) ? null : q.trim();
        Page<Employee> result = employeeRepository.searchAdmin(
                normalizedQ, role, departmentId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "fullName")));
        return ApiResponse.ok(AdminUserListResponse.from(result));
    }

    /**
     * 전체 ROLE 목록 조회 — 사용자 관리 화면 dropdown 데이터.
     */
    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<List<Role>> listRoles() {
        return ApiResponse.ok(List.of(Role.values()));
    }

    /**
     * 사용자 비활성화 — terminationDate = today.
     *
     * <p>terminate 와 구분 — soft-delete 미수반, enable 호출로 복구 가능.
     */
    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<EmployeeResponse> disable(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(provisioningService.disable(id, parseCaller(callerHeader)));
    }

    /**
     * 사용자 재활성화 — terminationDate = null.
     */
    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<EmployeeResponse> enable(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(provisioningService.enable(id, parseCaller(callerHeader)));
    }

    /**
     * 역할 변경 + 변경 이력 적재.
     */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<EmployeeResponse> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(provisioningService.updateRole(
                id, request.role(), request.reason(), parseCaller(callerHeader)));
    }

    /**
     * 역할 변경 이력 조회 — 매뉴얼 §4 변경 이력 탭.
     */
    @GetMapping("/{id}/role-history")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<List<RoleHistoryResponse>> roleHistory(@PathVariable UUID id) {
        List<RoleChangeHistory> rows = roleHistoryRepository.findAllByEmployeeIdOrderByCreatedAtDesc(id);
        return ApiResponse.ok(rows.stream().map(RoleHistoryResponse::from).toList());
    }

    private UUID parseCaller(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
