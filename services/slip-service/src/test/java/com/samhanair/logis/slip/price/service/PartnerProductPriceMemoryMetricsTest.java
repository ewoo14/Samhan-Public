package com.samhanair.logis.slip.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

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
        PartnerProductPriceMemoryService service = new PartnerProductPriceMemoryService(
                repository,
                batchRepository,
                Clock.systemUTC(),
                transactionManager,
                registry,
                new PartnerProductPriceMemoryProperties(),
                Runnable::run);

        service.remember(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.00"), "actor");

        String scrape = registry.scrape();
        assertThat(scrape).contains("slip_price_memory_upsert_success_total 1.0");
        assertThat(scrape).contains("slip_price_memory_upsert_failed_total 0.0");
        assertThat(scrape).contains("slip_price_memory_batch_size_count 1");
        assertThat(scrape).contains("slip_price_memory_upsert_duration_seconds_count 1");
    }
}
