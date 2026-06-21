package com.samhanair.logis.auth.web;

import com.samhanair.logis.auth.domain.ApproverType;
import com.samhanair.logis.auth.service.ApprovalLineApproverService;
import com.samhanair.logis.auth.service.ApprovalLineConfigService;
import com.samhanair.logis.auth.web.dto.AccountSearchResult;
import com.samhanair.logis.auth.web.dto.AddApproverRequest;
import com.samhanair.logis.auth.web.dto.ApprovalLineGroupOption;
import com.samhanair.logis.auth.web.dto.ApprovalLineRoleView;
import com.samhanair.logis.auth.web.dto.ReorderApprovalLineRequest;
import com.samhanair.logis.auth.web.dto.RenameApprovalLineRoleRequest;
import com.samhanair.logis.auth.web.dto.UpdateApprovalLineRoleRequest;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final ApprovalLineApproverService approverService;

    /** 전표 종류별 결재 역할 조회. */
    @GetMapping("/approval-line-configs")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.VIEW)
    public ApiResponse<List<ApprovalLineRoleView>> listRoles(@RequestParam String documentType) {
        return ApiResponse.ok(service.listRoles(documentType));
    }

    /** 결재라인 설정 picker 용 권한그룹 목록. */
    @GetMapping("/approval-line-configs/groups")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.VIEW)
    public ApiResponse<List<ApprovalLineGroupOption>> listGroups() {
        return ApiResponse.ok(service.listSelectableGroups());
    }

    /** 결재라인 설정 picker 용 사원 목록. */
    @GetMapping("/approval-line-configs/users")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.VIEW)
    public ApiResponse<List<AccountSearchResult>> searchUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(approverService.searchUsers(q, limit));
    }

    /** 역할에 권한 그룹/필수 갱신. */
    @PutMapping("/approval-line-configs/{id}")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.UPDATE)
    public ApiResponse<ApprovalLineRoleView> updateRole(
            @PathVariable UUID id, @RequestBody UpdateApprovalLineRoleRequest request) {
        return ApiResponse.ok(service.updateRole(id, request.required()));
    }

    /** 결재 역할에 그룹/개인 결재자를 추가한다. */
    @PostMapping("/approval-line-configs/{roleId}/approvers")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.UPDATE)
    public ApiResponse<ApprovalLineRoleView> addApprover(
            @PathVariable UUID roleId,
            @RequestBody AddApproverRequest request) {
        approverService.addApprover(roleId, parseApproverType(request.type()), request.refId());
        return ApiResponse.ok(service.getRoleView(roleId));
    }

    /** 결재 역할에서 결재자 1명을 제거한다. */
    @DeleteMapping("/approval-line-configs/{roleId}/approvers/{approverId}")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.UPDATE)
    public ApiResponse<ApprovalLineRoleView> removeApprover(
            @PathVariable UUID roleId,
            @PathVariable UUID approverId) {
        approverService.removeApprover(roleId, approverId);
        return ApiResponse.ok(service.getRoleView(roleId));
    }

    /**
     * 결재 역할 라벨(표시 명칭) 변경.
     *
     * <p>CREATOR(작성자) 역할의 라벨은 변경 불가 — 400 반환.
     *
     * @param id      변경 대상 역할 ID
     * @param request 새 라벨을 담은 요청 본문
     * @return 갱신된 역할 단건
     */
    @PutMapping("/approval-line-configs/{id}/label")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.UPDATE)
    public ApiResponse<ApprovalLineRoleView> renameRole(
            @PathVariable UUID id,
            @Valid @RequestBody RenameApprovalLineRoleRequest request) {
        return ApiResponse.ok(service.renameRole(id, request.label()));
    }

    /**
     * 전표 종류별 결재 역할 순서 재배치(2-phase swap).
     *
     * <p>요청의 {@code orderedIds} 는 해당 {@code documentType} 활성 역할 전체와 정확히 일치해야 한다.
     * 작성자(CREATOR) 역할은 항상 첫 번째여야 한다.
     *
     * @param documentType 전표 종류 (SLIP_OUTBOUND 등)
     * @param request      새 순서로 나열된 역할 UUID 목록
     * @return 순서 재할당 후 역할 목록(sequence 오름차순)
     */
    @PutMapping("/approval-line-configs/reorder")
    @RequirePermission(page = "admin.approval-line-config", action = PermissionAction.UPDATE)
    public ApiResponse<List<ApprovalLineRoleView>> reorderRoles(
            @RequestParam String documentType,
            @Valid @RequestBody ReorderApprovalLineRequest request) {
        return ApiResponse.ok(service.reorderRoles(requireDocumentType(documentType), request.orderedIds()));
    }

    private static String requireDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "전표 종류(documentType)를 입력해야 합니다");
        }
        return documentType.trim();
    }

    private static ApproverType parseApproverType(String type) {
        try {
            return ApproverType.valueOf(type == null ? "" : type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "결재자 유형은 GROUP 또는 USER 여야 합니다");
        }
    }
}
