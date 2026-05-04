package com.samhanair.logis.user.service.dto;

import java.util.List;
import java.util.UUID;

/** A single department + its members for the org-chart view. */
public record OrgChartNode(
        UUID id,
        String code,
        String name,
        EmployeeProjection teamLead,
        List<EmployeeProjection> members) {
}
