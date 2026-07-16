package com.samhanair.logis.slip.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.samhanair.logis.slip.config.SlipDataSourceConfig.PriceMemoryJdbcAccess;
import com.samhanair.logis.slip.price.config.PartnerProductPriceMemoryProperties;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryBatchRepository;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryRepository;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** #809 운영 alert 가 참조하는 실제 Prometheus export 이름을 잠근다. */
@ExtendWith(MockitoExtension.class)
class PartnerProductPriceMemoryMetricsTest {

    @Mock private PartnerProductPriceMemoryRepository repository;
    @Mock private PartnerProductPriceMemoryBatchRepository batchRepository;
    @Mock private PlatformTransactionManager transactionManager;

    @Test
    void prometheusExportsDocumentedCounterSummaryAndTimerNames() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(batchRepository.upsertAll(anyList(), any())).thenReturn(1);
        PartnerProductPriceMemoryService service = service(registry);

        service.remember(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), "actor");

        String scrape = registry.scrape();
        assertThat(scrape).contains("slip_price_memory_upsert_success_total 1.0");
        assertThat(scrape).contains("slip_price_memory_upsert_failed_total 0.0");
        assertThat(scrape).contains("slip_price_memory_upsert_skipped_total 0.0");
        assertThat(scrape).contains("slip_price_memory_batch_size_count 1");
        assertThat(scrape).contains("slip_price_memory_upsert_duration_seconds_count 1");
    }

    /**
     * [R8-BE-6] recency guard 로 <b>전량 skip</b> 된 배치도 statement 는 성공이므로 success 는
     * 그대로 오른다 (D-R4-2 — 최신성 권위 = remembered_at). 종전에는 그것이 유일한 신호라
     * "전량 유실 = 100% 성공" 으로 보였다. 갱신되지 않은 건수를 별도 카운터로 관측 가능하게 한다.
     */
    @Test
    void skippedCounter_recordsCommandsThatRecencyGuardDidNotApply() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        // 더 최신 remembered_at 행이 이미 있어 upsert 의 WHERE 절이 갱신을 건너뛴 상황 = affected 0
        when(batchRepository.upsertAll(anyList(), any())).thenReturn(0);
        PartnerProductPriceMemoryService service = service(registry);

        service.remember(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), "actor");

        String scrape = registry.scrape();
        assertThat(scrape).contains("slip_price_memory_upsert_skipped_total 1.0");
        assertThat(scrape).contains("slip_price_memory_upsert_success_total 1.0");
        assertThat(scrape).contains("slip_price_memory_upsert_failed_total 0.0");
    }

    private PartnerProductPriceMemoryService service(PrometheusMeterRegistry registry) {
        return new PartnerProductPriceMemoryService(
                repository,
                batchRepository,
                Clock.systemUTC(),
                new PriceMemoryJdbcAccess(null, null, transactionManager),
                registry,
                new PartnerProductPriceMemoryProperties(),
                Runnable::run);
    }
}
