package com.samhanair.logis.auth.web;

import com.samhanair.logis.auth.service.ApprovalLineConfigService;
import com.samhanair.logis.auth.web.dto.ApprovalLineRoleView;
import com.samhanair.logis.auth.web.dto.UpdateApprovalLineRoleRequest;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 결재라인 설정 admin 엔드포인트(인사 그룹, admin.approval-line-config). */
@RestController
@RequestMapping("/auth/admin")
@RequiredArgsConstructor
public class ApprovalLineConfigController {

    private final ApprovalLineConfigService service;

    /** 전표 종류별 결재 역할 조회. */
    @GetMapping("/approval-line-configs")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.VIEW)
    public ApiResponse<List<ApprovalLineRoleView>> listRoles(@RequestParam String documentType) {
        return ApiResponse.ok(service.listRoles(documentType));
    }

    /** 역할에 권한 그룹/필수 갱신. */
    @PutMapping("/approval-line-configs/{id}")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.UPDATE)
    public ApiResponse<ApprovalLineRoleView> updateRole(
            @PathVariable UUID id, @RequestBody UpdateApprovalLineRoleRequest request) {
        return ApiResponse.ok(service.updateRole(id, request.approverGroupId(), request.required()));
    }
}
