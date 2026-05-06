package com.samhanair.logis.partner.exception;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * partner-service 의 도메인 예외 → {@link ApiResponse} 봉투 매핑.
 * slip-service / partner-order-service 의 동일 패턴 (한국어 message 보존).
 */
@RestControllerAdvice
public class PartnerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PartnerExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.fail(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("입력값이 유효하지 않습니다");
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INVALID_INPUT, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(ErrorCode.CONFLICT.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception in partner-service", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }
}
