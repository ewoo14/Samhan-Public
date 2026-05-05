package com.samhanair.logis.product.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.product.client.GoogleSheetsClient;
import com.samhanair.logis.product.service.ProductSheetSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제품 admin endpoint — 옵션 C-3 결합 (시트 → DB 수동 sync trigger).
 *
 * <p><b>출처</b>: 개발책임자 결정 2026-05-05 — 옵션 C-2 (cron 1시간) + C-3 (admin trigger)
 * 결합. 시트 변경 즉시 반영이 필요한 경우 본 endpoint 호출 → 캐시 invalidate + sync 실행.
 *
 * <p><b>인증</b>: 본 path 는 {@code /products/internal/} prefix 가 아니므로
 * {@link com.samhanair.logis.product.config.HeaderAuthenticationFilter} 가 gateway 헤더
 * (X-User-Role) 로 인증. 운영 시 ADMIN role gate 는 후속 PR (현재 SecurityConfig 의
 * {@code anyRequest().authenticated()} 만 통과). MVP 게이트.
 */
@RestController
@RequestMapping("/api/v1/products/admin")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductSheetSyncService syncService;
    private final GoogleSheetsClient sheetsClient;

    /**
     * 시트 → DB 수동 sync trigger (옵션 C-3).
     * 캐시 invalidate 후 sync 실행 — 시트 최신값 즉시 반영.
     *
     * @return 응답 envelope 안 SyncSummary (총 inserted/updated/softDeleted/skipped + tab 별 분포)
     */
    @Operation(summary = "구글 시트 → DB 수동 sync trigger (옵션 C-3)",
            description = "cron 1시간 주기 (옵션 C-2) 와 별개로 시트 변경 즉시 반영이 필요할 때 호출. "
                    + "Caffeine 캐시 invalidate 후 sync 실행 — 시트 read 1회 추가 발생.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "sync 성공 (per-tab 결과)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "시트 read 실패 / Service Account 미설정")
    })
    @PostMapping("/sync")
    public ApiResponse<ProductSheetSyncService.SyncSummary> triggerSync() {
        sheetsClient.invalidateCache();
        ProductSheetSyncService.SyncSummary summary = syncService.syncAll();
        return ApiResponse.ok(summary);
    }
}
