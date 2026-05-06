package com.samhanair.logis.dashboard.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialized view REFRESH service — Phase 9 W4 (D-P9-13).
 *
 * <p>5분 간격 scheduled (또는 admin trigger) 로 {@code mv_realtime_stock_summary} +
 * {@code mv_sales_daily_summary} 를 REFRESH MATERIALIZED VIEW CONCURRENTLY 호출.
 *
 * <p>fail-soft 정책 — REFRESH 실패 시 다음 주기에 재시도 (silent skip + warn log).
 *
 * <p>H2 환경 (test local) 에서는 MATERIALIZED VIEW 미지원 → 호출 자체를 try/catch fail-soft 로 흡수.
 */
@Slf4j
@Service
public class MaterializedViewRefreshService {

    public static final String MV_REALTIME_STOCK_SUMMARY = "mv_realtime_stock_summary";
    public static final String MV_SALES_DAILY_SUMMARY = "mv_sales_daily_summary";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 2 view 모두 REFRESH. CONCURRENTLY 모드 — unique index 의무 (V1 SQL 보유).
     */
    @Transactional
    public RefreshResult refreshAll() {
        boolean stockOk = tryRefresh(MV_REALTIME_STOCK_SUMMARY);
        boolean salesOk = tryRefresh(MV_SALES_DAILY_SUMMARY);
        return new RefreshResult(stockOk, salesOk);
    }

    /**
     * 단건 REFRESH. CONCURRENTLY 옵션 활성. fail 시 false 반환 (예외 전파 X).
     */
    public boolean tryRefresh(String viewName) {
        if (viewName == null || viewName.isBlank()) {
            return false;
        }
        try {
            entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName)
                    .executeUpdate();
            return true;
        } catch (Exception ex) {
            log.warn("REFRESH MATERIALIZED VIEW {} 실패 (fail-soft) — msg={}", viewName, ex.getMessage());
            return false;
        }
    }

    /** REFRESH 결과 — 양쪽 view 별 성공 여부. */
    public record RefreshResult(boolean realtimeStockOk, boolean salesDailyOk) { }
}
