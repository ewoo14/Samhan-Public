package com.samhanair.logis.auth.web.dto;

import java.util.UUID;

/** 결재 역할에 그룹/개인 결재자 추가 요청. */
public record AddApproverRequest(String type, UUID refId) {}
