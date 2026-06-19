package com.samhanair.logis.accounting.web;

import com.samhanair.logis.accounting.domain.OrderProgressStatus;
import com.samhanair.logis.accounting.service.AccountingAdminQueryService;
import com.samhanair.logis.accounting.web.dto.LedgerStagingResponse;
import com.samhanair.logis.accounting.web.dto.OrderDetailResponse;
import com.samhanair.logis.accounting.web.dto.OrderSummaryResponse;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MIG-14 admin UI read endpoints. */
@Slf4j
@RestController
@RequestMapping("/accounting")
@RequiredArgsConstructor
public class AccountingAdminQueryController {

    private static final String ORDER_PAGE_CODE = "ecount.mig14.order-list";
    private static final String LEDGER_PAGE_CODE = "ecount.mig14.ledger";
    private static final String ROLE_HEADER = "X-User-Role";

    private final AccountingAdminQueryService service;
    private final DynamicPermissionClient dynamicPermissionClient;

    @GetMapping("/orders")
    @RequirePermission(page = ORDER_PAGE_CODE, action = com.samhanair.logis.security.permission.PermissionAction.VIEW)
    @Operation(summary = "MIG-14 order admin list")
    public ApiResponse<Page<OrderSummaryResponse>> orders(
            @RequestParam(required = false) OrderProgressStatus progressStatus,
            @RequestParam(required = false) String managerName,
            @RequestParam(required = false) String partnerName,
            @PageableDefault(size = 50, sort = "validUntil", direction = Sort.Direction.DESC)
            Pageable pageable,
            @RequestHeader(value = ROLE_HEADER, required = false) String roleHeader) {
        checkViewPermission(ORDER_PAGE_CODE, roleHeader);
        return ApiResponse.ok(service.listOrders(progressStatus, managerName, partnerName, pageable));
    }

    @GetMapping("/orders/{orderNo}")
    @RequirePermission(page = ORDER_PAGE_CODE, action = com.samhanair.logis.security.permission.PermissionAction.VIEW)
    @Operation(summary = "MIG-14 order admin detail")
    public ApiResponse<OrderDetailResponse> orderDetail(
            @PathVariable String orderNo,
            @RequestHeader(value = ROLE_HEADER, required = false) String roleHeader) {
        checkViewPermission(ORDER_PAGE_CODE, roleHeader);
        return ApiResponse.ok(service.getOrderDetail(orderNo));
    }

    @GetMapping("/ledger/sales")
    @RequirePermission(page = LEDGER_PAGE_CODE, action = com.samhanair.logis.security.permission.PermissionAction.VIEW)
    @Operation(summary = "MIG-14 sales ledger staging list")
    public ApiResponse<Page<LedgerStagingResponse>> salesLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String transformStatus,
            @PageableDefault(size = 50)
            Pageable pageable,
            @RequestHeader(value = ROLE_HEADER, required = false) String roleHeader) {
        checkViewPermission(LEDGER_PAGE_CODE, roleHeader);
        return ApiResponse.ok(service.listSalesLedger(from, to, partnerName, transformStatus, pageable));
    }

    @GetMapping("/ledger/purchase")
    @RequirePermission(page = LEDGER_PAGE_CODE, action = com.samhanair.logis.security.permission.PermissionAction.VIEW)
    @Operation(summary = "MIG-14 purchase ledger staging list")
    public ApiResponse<Page<LedgerStagingResponse>> purchaseLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String partnerName,
            @RequestParam(required = false) String transformStatus,
            @PageableDefault(size = 50)
            Pageable pageable,
            @RequestHeader(value = ROLE_HEADER, required = false) String roleHeader) {
        checkViewPermission(LEDGER_PAGE_CODE, roleHeader);
        return ApiResponse.ok(service.listPurchaseLedger(from, to, partnerName, transformStatus, pageable));
    }

    private void checkViewPermission(String pageCode, String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }
        if (!dynamicPermissionClient.canView(roleCode, pageCode)) {
            log.warn("[MIG-14] admin VIEW permission denied roleCode={} pageCode={}", roleCode, pageCode);
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "MIG-14 admin view permission denied.");
        }
    }
}
