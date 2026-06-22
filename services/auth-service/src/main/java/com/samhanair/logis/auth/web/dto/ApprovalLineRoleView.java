package com.samhanair.logis.auth.web.dto;

import com.samhanair.logis.approval.StepType;
import java.util.List;
import java.util.UUID;

/** 결재라인 설정 역할 응답 — 화면 표시는 approvers.displayName 을 사용하고 UUID 는 선택/삭제 계약용으로만 전달한다. */
public record ApprovalLineRoleView(
        UUID id, int sequence, String label, StepType stepType,
        List<ApproverView> approvers, boolean required,
        boolean enforced, boolean seedManaged) {}
