package com.samhanair.logis.auth.web.dto;

import java.util.UUID;

/** 역할 갱신 요청 — approverGroupId null=그룹 해제. */
public record UpdateApprovalLineRoleRequest(UUID approverGroupId, boolean required) {}
