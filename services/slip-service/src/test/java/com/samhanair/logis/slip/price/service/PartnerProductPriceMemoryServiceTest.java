package com.samhanair.logis.slip.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.slip.price.config.PartnerProductPriceMemoryProperties;
import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryBatchRepository;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PartnerProductPriceMemoryService — 최근 단가 조회/저장 계약 테스트. */
@ExtendWith(MockitoExtension.class)
class PartnerProductPriceMemoryServiceTest {

    @Mock private PartnerProductPriceMemoryRepository repository;
    @Mock private PartnerProductPriceMemoryBatchRepository batchRepository;
    @Mock private PlatformTransactionManager transactionManager;

    private SimpleMeterRegistry meterRegistry;
    private PartnerProductPriceMemoryProperties properties;
    private List<Runnable> queuedTasks;
    private PartnerProductPriceMemoryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        meterRegistry = new SimpleMeterRegistry();
        properties = new PartnerProductPriceMemoryProperties();
        queuedTasks = new ArrayList<>();
        Executor collectingExecutor = queuedTasks::add;
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new PartnerProductPriceMemoryService(
                repository, batchRepository, clock, transactionManager, meterRegistry,
                properties, collectingExecutor);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void find_returnsVatInclusiveInputPriceWithoutTransforming() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PartnerProductPriceMemory memory = PartnerProductPriceMemory.create(
                partnerId, productId, new BigDecimal("110000.00"), "LINE_SAVE",
                LocalDateTime.of(2026, 7, 15, 10, 0));

        when(repository.findByPartnerIdAndProductId(partnerId, productId)).thenReturn(Optional.of(memory));

        Optional<PartnerProductPriceMemoryResponse> response = service.find(partnerId, productId);

        assertThat(response).isPresent();
        assertThat(response.get().unitPrice()).isEqualByComparingTo("110000.00");
    }

    @Test
    void findAll_returnsOnlyHitsInDedupedRequestOrder() {
        UUID partnerId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID missProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        PartnerProductPriceMemory first = PartnerProductPriceMemory.create(
                partnerId, firstProductId, new BigDecimal("1000.00"), "LINE_SAVE",
                LocalDateTime.of(2026, 7, 15, 10, 0));
        PartnerProductPriceMemory second = PartnerProductPriceMemory.create(
                partnerId, secondProductId, new BigDecimal("2000.00"), "BUNDLE_SET",
                LocalDateTime.of(2026, 7, 15, 11, 0));
        when(repository.findAllByPartnerIdAndProductIdIn(any(), any())).thenReturn(List.of(second, first));

        List<PartnerProductPriceMemoryBulkItemResponse> found = service.findAll(
                partnerId, List.of(firstProductId, missProductId, secondProductId, firstProductId));

        assertThat(found).extracting(PartnerProductPriceMemoryBulkItemResponse::productId)
                .containsExactly(firstProductId, secondProductId);
        assertThat(found).extracting(PartnerProductPriceMemoryBulkItemResponse::unitPrice)
                .containsExactly(new BigDecimal("1000.00"), new BigDecimal("2000.00"));
    }

    @Test
    void remember_skipsNullPartnerId() {
        service.remember(null, UUID.randomUUID(), new BigDecimal("100.00"), "actor");

        verify(batchRepository, never()).upsertAll(anyList(), any(LocalDateTime.class));
    }

    @Test
    void remember_propagatesRepositoryFailureForCallerFailSoftBoundary() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        doThrow(new RuntimeException("db down")).when(batchRepository)
                .upsertAll(anyList(), any(LocalDateTime.class));

        assertThatThrownBy(() -> service.remember(partnerId, productId, new BigDecimal("100.00"), "actor"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        assertThat(meterRegistry.get(PartnerProductPriceMemoryService.UPSERT_FAILED_COUNTER)
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void rememberBatch_usesOneSetBasedUpsertAndDedicatedTimeouts() {
        UUID partnerId = UUID.randomUUID();
        List<PartnerProductPriceMemoryCommand> commands = List.of(
                new PartnerProductPriceMemoryCommand(
                        partnerId, UUID.randomUUID(), new BigDecimal("100.00"), "LINE_SAVE", "actor"),
                new PartnerProductPriceMemoryCommand(
                        partnerId, UUID.randomUUID(), new BigDecimal("200.00"), "LINE_SAVE", "actor"));
        when(batchRepository.upsertAll(anyList(), any(LocalDateTime.class))).thenReturn(2);

        service.rememberBatchAfterCommit(commands, "set-based-test");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PartnerProductPriceMemoryCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository).applyTransactionTimeouts(1_000, 3_000);
        verify(batchRepository).upsertAll(captor.capture(), any(LocalDateTime.class));
        assertThat(captor.getValue()).hasSize(2);
        assertThat(meterRegistry.get(PartnerProductPriceMemoryService.UPSERT_SUCCESS_COUNTER)
                .counter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.get(PartnerProductPriceMemoryService.BATCH_SIZE_SUMMARY)
                .summary().count()).isEqualTo(1L);
    }

    @Test
    void afterCommit_capturesLogicalEventTimeBeforeAsyncFlush() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        service.rememberBatchAfterCommit(List.of(new PartnerProductPriceMemoryCommand(
                partnerId, productId, new BigDecimal("1000.00"), "LINE_SAVE", "actor")), "test");
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        TransactionSynchronizationManager.clearSynchronization();

        synchronization.afterCommit();

        verify(batchRepository, never()).upsertAll(anyList(), any(LocalDateTime.class));
        assertThat(queuedTasks).hasSize(1);
        queuedTasks.get(0).run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PartnerProductPriceMemoryCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository).upsertAll(captor.capture(), any(LocalDateTime.class));
        assertThat(captor.getValue()).singleElement().satisfies(command -> {
            assertThat(command.rememberedAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 10, 0));
            assertThat(command.actor()).isEqualTo("actor");
        });
    }
}
