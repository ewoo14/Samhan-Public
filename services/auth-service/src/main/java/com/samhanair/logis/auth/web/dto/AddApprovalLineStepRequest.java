package com.samhanair.logis.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/** 결재라인 표시·서명용 단계 추가 요청. */
public record AddApprovalLineStepRequest(
        @NotBlank String documentType,
        @NotBlank String label) {}
