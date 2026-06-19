package com.samhanair.logis.accounting.web;

import com.samhanair.logis.accounting.service.AccountingMig8OrderExportService;
import com.samhanair.logis.accounting.web.dto.Mig8OrderExportResponse;
import com.samhanair.logis.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MIG-8 이관 주문 service-to-service export API.
 *
 * <p>{@code /internal/**} 경로는 {@code X-Internal-Token} 인증 대상이며 게이트웨이 사용자 화면에
 * 노출하지 않는다. 응답의 UUID는 partner-order-service 이식 전용 내부 식별자이다.
 */
@RestController
@RequestMapping("/internal/accounting")
@RequiredArgsConstructor
@Tag(name = "Accounting Internal - MIG-8 Order Export")
public class AccountingMig8OrderInternalController {

    private static final int DEFAULT_SIZE = 200;
    private static final int MAX_SIZE = 500;

    private final AccountingMig8OrderExportService exportService;

    @GetMapping("/mig8-orders")
    @Operation(summary = "MIG-8 이관 주문과 라인을 내부 API로 export")
    public ApiResponse<Page<Mig8OrderExportResponse>> mig8Orders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizeSize(size));
        return ApiResponse.ok(exportService.exportMig8Orders(pageRequest));
    }

    private static int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
