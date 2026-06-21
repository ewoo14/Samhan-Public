package com.samhanair.logis.auth.service;

import com.samhanair.logis.auth.domain.ApprovalLineApprover;
import com.samhanair.logis.auth.domain.ApproverType;
import com.samhanair.logis.auth.repository.AccountGroupRepository;
import com.samhanair.logis.auth.repository.ApprovalLineApproverRepository;
import com.samhanair.logis.auth.repository.ApprovalLineConfigRepository;
import com.samhanair.logis.auth.web.dto.ApprovalLineAuthorizeResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결재라인 역할에 지정된 그룹/개인 결재자 기준으로 action 수행 가능 여부를 판정한다. */
@Service
@RequiredArgsConstructor
public class ApprovalLineAuthorizationService {

    private final ApprovalLineConfigRepository approvalLineConfigRepository;
    private final ApprovalLineApproverRepository approvalLineApproverRepository;
    private final AccountGroupRepository accountGroupRepository;

    /**
     * documentType + actionKey 에 매핑된 결재자 집합을 조회하여 userId 가 포함되는지 확인한다.
     *
     * <p>결재 역할 자체가 없거나 결재자 0명이면 opt-in 미설정으로 간주해 {@code configured=false} 를 반환한다.
     *
     * @param documentType 문서 종류
     * @param actionKey    액션 앵커
     * @param userId       수행자 계정 UUID
     * @return configured/allowed 판정 결과
     */
    @Transactional(readOnly = true)
    public ApprovalLineAuthorizeResponse authorize(String documentType, String actionKey, UUID userId) {
        return approvalLineConfigRepository
                .findFirstByDocumentTypeAndActionKeyAndIsDeletedFalseOrderBySequenceAsc(documentType, actionKey)
                .map(role -> authorizeRole(role.getId(), userId))
                .orElseGet(() -> new ApprovalLineAuthorizeResponse(false, false));
    }

    private ApprovalLineAuthorizeResponse authorizeRole(UUID roleId, UUID userId) {
        List<ApprovalLineApprover> approvers =
                approvalLineApproverRepository.findByConfigRoleIdAndIsDeletedFalse(roleId);
        if (approvers.isEmpty() || userId == null) {
            return new ApprovalLineAuthorizeResponse(!approvers.isEmpty(), false);
        }
        if (approvers.stream().anyMatch(approver ->
                approver.getApproverType() == ApproverType.USER
                        && userId.equals(approver.getApproverRefId()))) {
            return new ApprovalLineAuthorizeResponse(true, true);
        }

        Set<UUID> approverGroupIds = approvers.stream()
                .filter(approver -> approver.getApproverType() == ApproverType.GROUP)
                .map(ApprovalLineApprover::getApproverRefId)
                .collect(Collectors.toSet());
        if (approverGroupIds.isEmpty()) {
            return new ApprovalLineAuthorizeResponse(true, false);
        }

        boolean groupAllowed = accountGroupRepository
                .findByAccountIdAndIsDeletedFalseOrderByGroupIdAsc(userId)
                .stream()
                .anyMatch(accountGroup -> approverGroupIds.contains(accountGroup.getGroupId()));
        return new ApprovalLineAuthorizeResponse(true, groupAllowed);
    }
}
