package com.samhanair.logis.auth.web.dto;

import com.samhanair.logis.approval.StepType;

/** 전표 결재란 렌더용 구조 응답 — 결재자 신원/권한 정보는 노출하지 않는다. */
public record ApprovalLineStructureView(
        int sequence,
        String label,
        StepType stepType,
        String actionKey) {}
