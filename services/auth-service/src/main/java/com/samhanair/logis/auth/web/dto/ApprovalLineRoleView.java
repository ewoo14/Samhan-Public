package com.samhanair.logis.auth.web.dto;

import com.samhanair.logis.approval.StepType;
import java.util.UUID;

/** 결재라인 설정 역할 응답 — UUID 비공개 가드: approverGroupName 은 표시용, group UUID 는 picker 선택값. */
public record ApprovalLineRoleView(
        UUID id, int sequence, String label, StepType stepType,
        UUID approverGroupId, String approverGroupName, boolean required) {}
