package com.samhanair.logis.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.dashboard.domain.KpiCategory;
import com.samhanair.logis.dashboard.domain.KpiSnapshot;
import com.samhanair.logis.dashboard.repository.KpiSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * KpiService 단위 테스트 — Phase 9 W4 (6 case).
 *
 * <ol>
 *   <li>category null → 400</li>
 *   <li>from > to → 400</li>
 *   <li>category + range 정상 lookup → repository delegate</li>
 *   <li>전체 카테고리 lookup → repository delegate</li>
 *   <li>upsert (insert) → repository.save 호출</li>
 *   <li>upsert (update) → 기존 row updateValue</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiServiceTest {

    @Mock
    private KpiSnapshotRepository repository;

    @InjectMocks
    private KpiService service;

    @BeforeEach
    void setup() {
        lenient().when(repository.save(any(KpiSnapshot.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void findByCategoryAndDateRange_with_null_category_throws_400() {
        assertThatThrownBy(() -> service.findByCategoryAndDateRange(
                null, LocalDate.now().minusDays(7), LocalDate.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("category 필수");
    }

    @Test
    void findByCategoryAndDateRange_with_from_after_to_throws_400() {
        assertThatThrownBy(() -> service.findByCategoryAndDateRange(
                KpiCategory.DAILY_SALES, LocalDate.now(), LocalDate.now().minusDays(7)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("from 이 to");
    }

    @Test
    void findByCategoryAndDateRange_delegates_to_repository() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        KpiSnapshot row = KpiSnapshot.of(to, KpiCategory.DAILY_SALES, BigDecimal.valueOf(100));
        when(repository.findAllByCategoryAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                eq(KpiCategory.DAILY_SALES), eq(from), eq(to)))
                .thenReturn(List.of(row));

        List<KpiSnapshot> result = service.findByCategoryAndDateRange(KpiCategory.DAILY_SALES, from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo(KpiCategory.DAILY_SALES);
    }

    @Test
    void findByDateRange_returns_all_categories() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        when(repository.findAllBySnapshotDateBetweenOrderBySnapshotDateAsc(eq(from), eq(to)))
                .thenReturn(List.of());

        List<KpiSnapshot> result = service.findByDateRange(from, to);

        assertThat(result).isEmpty();
        verify(repository, times(1)).findAllBySnapshotDateBetweenOrderBySnapshotDateAsc(from, to);
    }

    @Test
    void upsert_inserts_when_missing() {
        LocalDate today = LocalDate.now();
        when(repository.findFirstByCategoryAndSnapshotDate(KpiCategory.ORDER_COUNT, today))
                .thenReturn(Optional.empty());

        KpiSnapshot result = service.upsert(today, KpiCategory.ORDER_COUNT, BigDecimal.valueOf(42));

        assertThat(result.getCategory()).isEqualTo(KpiCategory.ORDER_COUNT);
        assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.valueOf(42));
        verify(repository, times(1)).save(any(KpiSnapshot.class));
    }

    @Test
    void upsert_updates_when_existing() {
        LocalDate today = LocalDate.now();
        KpiSnapshot existing = KpiSnapshot.of(today, KpiCategory.ORDER_COUNT, BigDecimal.valueOf(10));
        when(repository.findFirstByCategoryAndSnapshotDate(KpiCategory.ORDER_COUNT, today))
                .thenReturn(Optional.of(existing));

        KpiSnapshot result = service.upsert(today, KpiCategory.ORDER_COUNT, BigDecimal.valueOf(99));

        assertThat(result.getValue()).isEqualByComparingTo(BigDecimal.valueOf(99));
        // existing 객체 재사용 — save() 호출 안함
        verify(repository, times(0)).save(any(KpiSnapshot.class));
    }
}
