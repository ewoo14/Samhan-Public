package com.samhanair.logis.dashboard.dto;

import com.samhanair.logis.dashboard.domain.KpiCategory;
import com.samhanair.logis.dashboard.domain.KpiSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KPI 스냅샷 응답 DTO — Internal + Admin 양쪽 endpoint 공용.
 *
 * <p>UUID 비공개 가드 — id (UUID) 노출 X. category / snapshotDate / value / createdAt 만 노출.
 */
public record KpiSnapshotResponse(
        LocalDate snapshotDate,
        KpiCategory category,
        BigDecimal value,
        LocalDateTime createdAt
) {

    public static KpiSnapshotResponse from(KpiSnapshot snapshot) {
        return new KpiSnapshotResponse(
                snapshot.getSnapshotDate(),
                snapshot.getCategory(),
                snapshot.getValue(),
                snapshot.getCreatedAt());
    }
}
