package com.samhanair.logis.auth.web.dto;

import java.util.UUID;

/** 그룹웨어 생성 프리필용 기본 결재자(USER) 응답. */
public record ApprovalLineDefaultApproverView(
        int sequence,
        String label,
        UUID userId,
        String displayName
) {}
