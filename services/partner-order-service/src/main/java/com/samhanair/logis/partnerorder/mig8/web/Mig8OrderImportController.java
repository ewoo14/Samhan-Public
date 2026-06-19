package com.samhanair.logis.partnerorder.mig8.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.partnerorder.mig8.service.Mig8OrderImportResult;
import com.samhanair.logis.partnerorder.mig8.service.Mig8OrderImportService;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MIG-8 이관 주문을 native partner_orders 로 이식하는 admin endpoint. */
@RestController
@RequestMapping("/admin/partner-orders")
@RequiredArgsConstructor
public class Mig8OrderImportController {

    private final Mig8OrderImportService importService;

    @Operation(summary = "MIG-8 이관 주문 native 이식")
    @PostMapping("/mig8-import")
    @RequirePermission(page = "sales.partner-order.convert", action = PermissionAction.CREATE)
    public ApiResponse<Mig8OrderImportResult> importMig8Orders(
            @RequestParam(required = false) Integer batchSize) {
        return ApiResponse.ok(importService.importMig8Orders(batchSize));
    }
}
