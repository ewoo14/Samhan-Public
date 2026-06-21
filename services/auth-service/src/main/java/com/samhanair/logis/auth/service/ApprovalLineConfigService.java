package com.samhanair.logis.auth.service;

import com.samhanair.logis.approval.StepType;
import com.samhanair.logis.auth.domain.ApprovalLineConfig;
import com.samhanair.logis.auth.repository.ApprovalLineConfigRepository;
import com.samhanair.logis.auth.repository.PermissionGroupRepository;
import com.samhanair.logis.auth.web.dto.ApprovalLineGroupOption;
import com.samhanair.logis.auth.web.dto.ApprovalLineRoleView;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결재라인 설정 — 전표종류별 역할 조회 + 역할별 권한 그룹/필수 갱신(선언적). */
@Service
@RequiredArgsConstructor
public class ApprovalLineConfigService {

    private final ApprovalLineConfigRepository repository;
    private final PermissionGroupRepository groupRepository;

    /** 전표 종류별 결재 역할(sequence 순). */
    @Transactional(readOnly = true)
    public List<ApprovalLineRoleView> listRoles(String documentType) {
        return repository.findByDocumentTypeOrderBySequenceAsc(documentType).stream()
                .map(this::toView)
                .toList();
    }

    /** 결재 역할에 지정 가능한 권한그룹 목록. 시스템마스터 그룹은 결재자 그룹 후보에서 제외한다. */
    @Transactional(readOnly = true)
    public List<ApprovalLineGroupOption> listSelectableGroups() {
        return groupRepository.findByIsDeletedFalse().stream()
                .filter(group -> !group.isSystemMaster())
                .sorted(Comparator.comparing(group -> group.getName()))
                .map(group -> new ApprovalLineGroupOption(group.getId(), group.getName()))
                .toList();
    }

    /** 역할에 권한 그룹/필수 갱신. CREATOR 역할 그룹 지정은 거부. groupId=null 이면 그룹 해제. */
    @Transactional
    public ApprovalLineRoleView updateRole(UUID id, UUID approverGroupId, boolean required) {
        ApprovalLineConfig role = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "결재 역할을 찾을 수 없습니다: " + id));
        if (role.getStepType() == StepType.CREATOR) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "작성자 역할은 변경할 수 없습니다");
        }
        try {
            if (approverGroupId == null) {
                role.clearGroup();
            } else {
                var group = groupRepository.findById(approverGroupId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.INVALID_INPUT,
                                "존재하지 않는 권한 그룹입니다: " + approverGroupId));
                if (group.isSystemMaster()) {
                    throw new BusinessException(
                            ErrorCode.INVALID_INPUT,
                            "시스템 마스터 그룹은 결재 그룹으로 지정할 수 없습니다");
                }
                role.assignGroup(approverGroupId);
            }
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
        role.changeRequired(required);
        return toView(repository.save(role));
    }

    private ApprovalLineRoleView toView(ApprovalLineConfig role) {
        String groupName = role.getApproverGroupId() == null ? null
                : groupRepository.findById(role.getApproverGroupId())
                        .map(g -> g.getName()).orElse(null);
        return new ApprovalLineRoleView(role.getId(), role.getSequence(), role.getLabel(),
                role.getStepType(), role.getApproverGroupId(), groupName, role.isRequired());
    }
}
