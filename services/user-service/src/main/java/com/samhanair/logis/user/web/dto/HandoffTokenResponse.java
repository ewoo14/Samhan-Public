package com.samhanair.logis.user.web.dto;

/**
 * 핸드오프 토큰 발급 응답 (slice C1b · contract).
 *
 * @param token base64url 64자 1회용 토큰
 * @param qrUrl 모바일 공개 제출 URL (QR/복사링크용, 실 origin + /api/public/employee-signatures/{token})
 * @param expiresAt ISO-8601 만료 시각
 */
public record HandoffTokenResponse(String token, String qrUrl, String expiresAt) {}
