package com.samhanair.logis.auth.web.dto;

import java.util.UUID;

/** 결재자 사원 검색 결과. UUID 는 선택 refId 용이며 화면 표시는 displayName 만 사용한다. */
public record AccountSearchResult(UUID id, String displayName) {}
