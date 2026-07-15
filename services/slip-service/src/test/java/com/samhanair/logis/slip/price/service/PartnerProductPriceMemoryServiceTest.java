package com.samhanair.logis.slip.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.Collections;
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
    void rememberBatch_sortsFlushBatchByPartnerAndProductAfterDedup() {
        // R4-B2 — flush 배치는 문서 라인 순서가 아니라 (partnerId, productId) 전역 잠금 순서여야 한다.
        UUID partnerEarly = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID partnerLate = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID productEarly = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID productLate = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        when(batchRepository.upsertAll(anyList(), any(LocalDateTime.class))).thenReturn(3);

        // 문서 라인 순서는 정렬 역순 + 같은 pair 중복(마지막 라인 승리가 정렬 후에도 유지되는지 검증).
        service.rememberBatchAfterCommit(List.of(
                new PartnerProductPriceMemoryCommand(
                        partnerLate, productEarly, new BigDecimal("300.00"), "LINE_SAVE", "actor"),
                new PartnerProductPriceMemoryCommand(
                        partnerEarly, productLate, new BigDecimal("200.00"), "LINE_SAVE", "actor"),
                new PartnerProductPriceMemoryCommand(
                        partnerEarly, productEarly, new BigDecimal("50.00"), "LINE_SAVE", "actor"),
                new PartnerProductPriceMemoryCommand(
                        partnerEarly, productEarly, new BigDecimal("100.00"), "LINE_SAVE", "actor")),
                "lock-order-sort");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PartnerProductPriceMemoryCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository).upsertAll(captor.capture(), any(LocalDateTime.class));
        assertThat(captor.getValue())
                .extracting(PartnerProductPriceMemoryCommand::partnerId,
                        PartnerProductPriceMemoryCommand::productId,
                        PartnerProductPriceMemoryCommand::unitPrice)
                .containsExactly(
                        tuple(partnerEarly, productEarly, new BigDecimal("100.00")),
                        tuple(partnerEarly, productLate, new BigDecimal("200.00")),
                        tuple(partnerLate, productEarly, new BigDecimal("300.00")));
    }

    @Test
    void rememberBatch_crossOrderedDocumentsAcquireLocksInSameGlobalOrder() {
        // R4-B2 시나리오 — 문서 A=[X,Y], 문서 B=[Y,X] 동시 flush 시 역순 행 잠금 → PG deadlock →
        // 한쪽 배치 fail-soft 전체 유실. 두 배치가 동일한 전역 순서로 나가면 deadlock 이 구조적으로 소멸한다.
        UUID partnerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID productX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID productY = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        when(batchRepository.upsertAll(anyList(), any(LocalDateTime.class))).thenReturn(2);

        service.rememberBatchAfterCommit(List.of(
                new PartnerProductPriceMemoryCommand(
                        partnerId, productX, new BigDecimal("1000.00"), "LINE_SAVE", "actor"),
                new PartnerProductPriceMemoryCommand(
                        partnerId, productY, new BigDecimal("2000.00"), "LINE_SAVE", "actor")),
                "doc-a");
        service.rememberBatchAfterCommit(List.of(
                new PartnerProductPriceMemoryCommand(
                        partnerId, productY, new BigDecimal("2000.00"), "LINE_SAVE", "actor"),
                new PartnerProductPriceMemoryCommand(
                        partnerId, productX, new BigDecimal("1000.00"), "LINE_SAVE", "actor")),
                "doc-b");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PartnerProductPriceMemoryCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository, times(2)).upsertAll(captor.capture(), any(LocalDateTime.class));
        List<List<PartnerProductPriceMemoryCommand>> batches = captor.getAllValues();
        assertThat(batches.get(0)).extracting(PartnerProductPriceMemoryCommand::productId)
                .containsExactly(productX, productY);
        assertThat(batches.get(1)).extracting(PartnerProductPriceMemoryCommand::productId)
                .containsExactly(productX, productY);
    }

    @Test
    void rememberBatchAfterCommit_samePairUsesLastLineInDocumentOrder() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PartnerProductPriceMemoryCommand firstLine = new PartnerProductPriceMemoryCommand(
                partnerId, productId, new BigDecimal("1000.00"), "LINE_SAVE", "actor");
        PartnerProductPriceMemoryCommand lastLine = new PartnerProductPriceMemoryCommand(
                partnerId, productId, new BigDecimal("2000.00"), "LINE_SAVE", "actor");
        when(batchRepository.upsertAll(anyList(), any(LocalDateTime.class))).thenReturn(1);

        service.rememberBatchAfterCommit(List.of(firstLine, lastLine), "same-pair-last-wins");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PartnerProductPriceMemoryCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository, times(1)).upsertAll(captor.capture(), any(LocalDateTime.class));
        assertThat(captor.getValue()).singleElement().satisfies(command -> {
            assertThat(command.partnerId()).isEqualTo(partnerId);
            assertThat(command.productId()).isEqualTo(productId);
            assertThat(command.unitPrice()).isEqualByComparingTo("2000.00");
        });
    }

    @Test
    void rememberBatchAfterCommit_nullCommandIsIgnored() {
        service.rememberBatchAfterCommit(Collections.singletonList(null), "null-command");

        verify(batchRepository, never()).applyTransactionTimeouts(anyInt(), anyInt());
        verify(batchRepository, never()).upsertAll(anyList(), any(LocalDateTime.class));
        assertThat(queuedTasks).isEmpty();
    }

    @Test
    void rememberBatchAfterCommit_blankActorUsesSystemActor() {
        PartnerProductPriceMemoryCommand command = new PartnerProductPriceMemoryCommand(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("3000.00"), "LINE_SAVE", "  ");
        when(batchRepository.upsertAll(anyList(), any(LocalDateTime.class))).thenReturn(1);

        service.rememberBatchAfterCommit(List.of(command), "blank-actor");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PartnerProductPriceMemoryCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository).upsertAll(captor.capture(), any(LocalDateTime.class));
        assertThat(captor.getValue()).singleElement()
                .extracting(PartnerProductPriceMemoryCommand::actor)
                .isEqualTo("system");
    }

    @Test
    void rememberBatchAfterCommit_withoutSynchronizationFlushesImmediately() {
        PartnerProductPriceMemoryCommand command = new PartnerProductPriceMemoryCommand(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("4000.00"), "LINE_SAVE", "actor");
        when(batchRepository.upsertAll(anyList(), any(LocalDateTime.class))).thenReturn(1);

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        service.rememberBatchAfterCommit(List.of(command), "no-synchronization");

        verify(batchRepository).upsertAll(anyList(), any(LocalDateTime.class));
        assertThat(queuedTasks).isEmpty();
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
