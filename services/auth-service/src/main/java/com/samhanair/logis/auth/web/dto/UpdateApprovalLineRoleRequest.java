package com.samhanair.logis.auth.web.dto;

/** 역할 갱신 요청 — A2-1c 이후 필수여부만 변경한다. */
public record UpdateApprovalLineRoleRequest(boolean required) {}
