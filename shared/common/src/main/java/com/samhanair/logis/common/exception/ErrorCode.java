package com.samhanair.logis.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** Canonical error catalogue mapped to HTTP status + Korean default messages. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "리소스 상태가 충돌합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    /**
     * 도메인 specific — product-service 의 modelCode/UUID 조회 미존재.
     * Phase 7 종합 TM 신규 — generic NOT_FOUND + 문자열 메시지 패턴은 클라이언트 분기/모니터링 필터에서
     * 도메인 식별이 어려우므로 product 전용 코드를 분리. HTTP 404 동일.
     */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 코드의 제품을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
