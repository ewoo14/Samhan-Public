package com.samhanair.logis.dcconfig.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.dcconfig.dto.EstimateConfigResponse;
import com.samhanair.logis.dcconfig.dto.UpdateEstimateConfigRequest;
import com.samhanair.logis.dcconfig.service.EstimateConfigService;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 데스크톱 영업 "견적 가격 설정" 화면 endpoint. */
@RestController
@RequestMapping("/api/v1/estimate-config")
@RequiredArgsConstructor
public class EstimateConfigController {

    private final EstimateConfigService estimateConfigService;

    @GetMapping
    @RequirePermission(page = "sales.estimate-config", action = PermissionAction.VIEW)
    public ApiResponse<EstimateConfigResponse> get() {
        return ApiResponse.ok(EstimateConfigResponse.from(estimateConfigService.getOrSeedDefault()));
    }

    @PutMapping
    @RequirePermission(page = "sales.estimate-config", action = PermissionAction.UPDATE)
    public ApiResponse<EstimateConfigResponse> update(@Valid @RequestBody UpdateEstimateConfigRequest request) {
        return ApiResponse.ok(EstimateConfigResponse.from(estimateConfigService.update(request)));
    }
}
