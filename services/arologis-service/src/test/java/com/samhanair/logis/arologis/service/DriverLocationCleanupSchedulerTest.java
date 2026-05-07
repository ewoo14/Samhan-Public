package com.samhanair.logis.arologis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.config.ArologisLocationCleanupProperties;
import com.samhanair.logis.arologis.repository.DriverLocationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DriverLocationCleanupScheduler 단위 테스트 — Phase 10 W10-1.
 *
 * <p>2 case — 30일 cleanup 호출 / retentionDays 1 미만 시 1로 fallback.
 */
class DriverLocationCleanupSchedulerTest {

    @Test
    @DisplayName("30일 cleanup — repository.deleteOlderThan 호출 + threshold 검증")
    void cleanup_calls_repository_with_correct_threshold() {
        DriverLocationRepository repo = mock(DriverLocationRepository.class);
        ArologisLocationCleanupProperties props = new ArologisLocationCleanupProperties();
        props.setRetentionDays(30);
        when(repo.deleteOlderThan(any())).thenReturn(42);

        DriverLocationCleanupScheduler scheduler = new DriverLocationCleanupScheduler(repo, props);
        int deleted = scheduler.cleanupNow();

        assertThat(deleted).isEqualTo(42);
        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(repo, times(1)).deleteOlderThan(captor.capture());
        LocalDate expected = LocalDate.now().minusDays(30);
        assertThat(captor.getValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("retentionDays 0 / 음수 → 1일로 fallback")
    void cleanup_fallback_when_retention_invalid() {
        DriverLocationRepository repo = mock(DriverLocationRepository.class);
        ArologisLocationCleanupProperties props = new ArologisLocationCleanupProperties();
        props.setRetentionDays(0);
        when(repo.deleteOlderThan(any())).thenReturn(0);

        DriverLocationCleanupScheduler scheduler = new DriverLocationCleanupScheduler(repo, props);
        scheduler.cleanupNow();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(repo).deleteOlderThan(captor.capture());
        // 0일 → max(0,1) = 1일 fallback
        assertThat(captor.getValue()).isEqualTo(LocalDate.now().minusDays(1));
    }
}
