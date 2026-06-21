package com.samhanair.logis.auth.web.dto;

import java.util.UUID;

/** 결재 역할에 지정된 결재자 칩 응답. refId 는 선택/삭제 계약용이며 화면에는 displayName 만 표시한다. */
public record ApproverView(UUID id, String type, UUID refId, String displayName) {}
