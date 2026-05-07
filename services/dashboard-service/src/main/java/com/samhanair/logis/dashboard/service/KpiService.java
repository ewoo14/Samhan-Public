package com.samhanair.logis.dashboard.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.dashboard.config.CacheConfig;
import com.samhanair.logis.dashboard.domain.KpiCategory;
import com.samhanair.logis.dashboard.domain.KpiSnapshot;
import com.samhanair.logis.dashboard.repository.KpiSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPI 조회 service — Phase 9 W4.
 *
 * <p>category + 날짜 범위 조회 + Caffeine cache (TTL 60s, D-P9-12) 적용. 산출 자체는 별도 batch job
 * 가정 (현 슬라이스 미포함 — Phase 10 cutover 시점 또는 별도 PR scope).
 *
 * <p>upsert/delete 메서드는 cache evict — 일관성 보존.
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    private final KpiSnapshotRepository repository;

    /**
     * category + 날짜 범위로 KPI 시계열 조회. cache key = {@code category-from-to}.
     *
     * @param category KPI 카테고리
     * @param from 시작 일자
     * @param to 종료 일자
     * @return 시계열 (snapshotDate ASC)
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_KPI, key = "#category.name() + '-' + #from + '-' + #to")
    public List<KpiSnapshot> findByCategoryAndDateRange(KpiCategory category, LocalDate from, LocalDate to) {
        if (category == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "category 필수");
        }
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from / to 필수");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from 이 to 보다 이후일 수 없음");
        }
        return repository.findAllByCategoryAndSnapshotDateBetweenOrderBySnapshotDateAsc(category, from, to);
    }

    /**
     * 날짜 범위 전체 카테고리 조회.
     */
    @Transactional(readOnly = true)
    public List<KpiSnapshot> findByDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from / to 필수");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from 이 to 보다 이후일 수 없음");
        }
        return repository.findAllBySnapshotDateBetweenOrderBySnapshotDateAsc(from, to);
    }

    /**
     * KPI 스냅샷 upsert — 같은 (category, snapshotDate) 가 있으면 update, 없으면 insert.
     * cache evict 로 일관성 보존.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_KPI, allEntries = true)
    public KpiSnapshot upsert(LocalDate snapshotDate, KpiCategory category, BigDecimal value) {
        return repository.findFirstByCategoryAndSnapshotDate(category, snapshotDate)
                .map(existing -> {
                    existing.updateValue(value);
                    return existing;
                })
                .orElseGet(() -> repository.save(KpiSnapshot.of(snapshotDate, category, value)));
    }

    /** cache 명시 invalidate (admin refresh trigger 시점). */
    @CacheEvict(value = CacheConfig.CACHE_KPI, allEntries = true)
    public void invalidateCache() {
        // method body intentionally empty — annotation-driven evict
    }
}
