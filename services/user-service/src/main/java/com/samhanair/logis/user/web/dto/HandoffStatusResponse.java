package com.samhanair.logis.user.web.dto;

/**
 * 핸드오프 토큰 상태 응답 — desktop 폴링용 (slice C1b · contract).
 *
 * @param used 토큰 소진(제출 완료) 여부
 * @param expired 만료 여부
 */
public record HandoffStatusResponse(boolean used, boolean expired) {}
