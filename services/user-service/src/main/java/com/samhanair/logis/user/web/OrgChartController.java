package com.samhanair.logis.user.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.user.service.OrgChartService;
import com.samhanair.logis.user.web.dto.OrgChartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only org-chart projection. Available to any authenticated caller. */
@RestController
@RequestMapping("/users/org-chart")
@RequiredArgsConstructor
public class OrgChartController {

    private final OrgChartService orgChartService;

    @GetMapping
    public ApiResponse<OrgChartResponse> get() {
        return ApiResponse.ok(orgChartService.getOrgChart());
    }
}
