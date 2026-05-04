package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.user.service.dto.OrgChartNode;
import java.util.List;

/** Top-level org-chart view returned by {@code GET /users/org-chart}. */
public record OrgChartResponse(List<OrgChartNode> departments) {
}
