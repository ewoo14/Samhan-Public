package com.samhanair.logis.slip.delivery.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.delivery.service.DeliveryBatchService;
import com.samhanair.logis.slip.delivery.web.dto.PublicBatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 모바일 endpoint — Plan §4.2.
 * 인증 없음 (API Gateway 의 {@code /api/public/**} 라우트가 JwtAuthentication 필터 미적용 +
 * slip-service {@link com.samhanair.logis.slip.config.SecurityConfig} 의 {@code /public/**}
 * permitAll). 토큰만 검증.
 *
 * <p>UUID 비공개 가드 (memory {@code feedback_uuid_no_user_visibility.md}):
 * 응답에 slip.id / batch.id UUID 노출 금지. 본 controller 는 {@link PublicBatchResponse}
 * (slipNo / partnerName / lineCount / status 만) 를 반환.
 *
 * <p>토큰 만료 시 {@link DeliveryBatchService#findByToken} 가 CONFLICT BusinessException 을
 * 던지므로 본 controller 가 410 GONE 으로 매핑한다 (Plan §8 권한 모델).
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicSlipController {

    private final DeliveryBatchService batchService;

    /** 모바일 배치 read-only 조회 (no auth). */
    @Operation(summary = "공개 모바일 배치 조회",
            description = "토큰 검증 + 슬립 N건 read-only. 만료 시 410 GONE")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "토큰 미발견"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "토큰 만료")
    })
    @GetMapping("/batches/{token}")
    public ResponseEntity<ApiResponse<PublicBatchResponse>> getBatch(@PathVariable String token) {
        try {
            PublicBatchResponse body = batchService.findByToken(token);
            return ResponseEntity.ok(ApiResponse.ok(body));
        } catch (BusinessException ex) {
            // CONFLICT 는 만료 — 410 GONE 으로 변환 (Plan §8)
            if (ex.getErrorCode() == ErrorCode.CONFLICT) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(ApiResponse.fail(ErrorCode.CONFLICT, ex.getMessage()));
            }
            // NOT_FOUND 는 그대로 다시 던져 GlobalExceptionHandler 가 처리
            throw ex;
        }
    }
}
