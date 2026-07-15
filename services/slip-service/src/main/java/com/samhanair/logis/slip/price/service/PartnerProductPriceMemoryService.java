package com.samhanair.logis.slip.price.service;

import com.samhanair.logis.slip.price.config.PartnerProductPriceMemoryProperties;
import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryBatchRepository;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 거래처+품목 최근 수동단가 기억 서비스. */
@Slf4j
@Service
public class PartnerProductPriceMemoryService {

    /** Prometheus export: {@code slip_price_memory_upsert_success_total}. */
    public static final String UPSERT_SUCCESS_COUNTER = "slip_price_memory_upsert_success_total";
    /** Prometheus export: {@code slip_price_memory_upsert_failed_total}. */
    public static final String UPSERT_FAILED_COUNTER = "slip_price_memory_upsert_failed_total";
    /** Prometheus export: count/sum/max 계열 {@code slip_price_memory_batch_size_*}. */
    public static final String BATCH_SIZE_SUMMARY = "slip_price_memory_batch_size";
    /** Prometheus export: {@code slip_price_memory_upsert_duration_seconds_*}. */
    public static final String UPSERT_DURATION_TIMER = "slip_price_memory_upsert_duration";

    /**
     * 동시 flush 배치 간 행 잠금 순서를 통일하는 전역 정렬 기준.
     *
     * <p>deadlock 회피에는 JVM 내 모든 배치가 같은 비교 규칙을 쓰는 일관성만 필요하므로
     * UUID 자연 순서(natural ordering)로 충분하다 (PostgreSQL 바이트 순서와 일치할 필요 없음).
     */
    private static final Comparator<PartnerProductPriceMemoryCommand> PAIR_LOCK_ORDER =
            Comparator.comparing(PartnerProductPriceMemoryCommand::partnerId)
                    .thenComparing(PartnerProductPriceMemoryCommand::productId);

    private final PartnerProductPriceMemoryRepository repository;
    private final PartnerProductPriceMemoryBatchRepository batchRepository;
    private final Clock clock;
    private final PartnerProductPriceMemoryProperties properties;
    private final Executor priceMemoryExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Counter successCounter;
    private final Counter failedCounter;
    private final DistributionSummary batchSizeSummary;
    private final Timer upsertDuration;

    public PartnerProductPriceMemoryService(
            PartnerProductPriceMemoryRepository repository,
            PartnerProductPriceMemoryBatchRepository batchRepository,
            Clock clock,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry,
            PartnerProductPriceMemoryProperties properties,
            @Qualifier("priceMemoryExecutor") Executor priceMemoryExecutor) {
        this.repository = repository;
        this.batchRepository = batchRepository;
        this.clock = clock;
        this.properties = properties;
        this.priceMemoryExecutor = priceMemoryExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setTimeout(properties.getTransactionTimeoutSeconds());
        this.transactionTemplate.setName("partner-product-price-memory");
        this.successCounter = Counter.builder(UPSERT_SUCCESS_COUNTER)
                .description("가격기억 upsert 성공 command 누적 건수")
                .register(meterRegistry);
        this.failedCounter = Counter.builder(UPSERT_FAILED_COUNTER)
                .description("가격기억 upsert 실패 또는 queue 거부 command 누적 건수")
                .register(meterRegistry);
        this.batchSizeSummary = DistributionSummary.builder(BATCH_SIZE_SUMMARY)
                .description("가격기억 set-based upsert batch 크기")
                .register(meterRegistry);
        this.upsertDuration = Timer.builder(UPSERT_DURATION_TIMER)
                .description("가격기억 전용 REQUIRES_NEW 트랜잭션 지연")
                .register(meterRegistry);
    }

    /** 최근 수동단가를 조회한다. */
    @Transactional(readOnly = true)
    public Optional<PartnerProductPriceMemoryResponse> find(UUID partnerId, UUID productId) {
        if (partnerId == null || productId == null) {
            return Optional.empty();
        }
        return repository.findByPartnerIdAndProductId(partnerId, productId)
                .map(PartnerProductPriceMemoryResponse::from);
    }

    /**
     * 같은 거래처의 N개 품목 최근단가 hit 를 요청 순서로 반환한다.
     *
     * <p>miss 와 중복 productId 는 생략한다. 단건 {@link #find(UUID, UUID)} 와 같은 repository
     * 조건을 사용하므로 값과 soft-delete 판정이 동일하다.
     */
    @Transactional(readOnly = true)
    public List<PartnerProductPriceMemoryBulkItemResponse> findAll(
            UUID partnerId, Collection<UUID> productIds) {
        if (partnerId == null || productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<UUID> uniqueProductIds = new ArrayList<>(new LinkedHashSet<>(productIds));
        uniqueProductIds.removeIf(java.util.Objects::isNull);
        if (uniqueProductIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, PartnerProductPriceMemory> byProductId = new LinkedHashMap<>();
        repository.findAllByPartnerIdAndProductIdIn(partnerId, uniqueProductIds)
                .forEach(memory -> byProductId.put(memory.getProductId(), memory));
        return uniqueProductIds.stream()
                .map(byProductId::get)
                .filter(java.util.Objects::nonNull)
                .map(PartnerProductPriceMemoryBulkItemResponse::from)
                .toList();
    }

    /** 최근 수동단가를 별도 짧은 트랜잭션으로 즉시 기억한다. 테스트/관리 경로용. */
    public void remember(UUID partnerId, UUID productId, BigDecimal unitPrice, String actor) {
        remember(partnerId, productId, unitPrice, PartnerProductPriceMemory.SOURCE_LINE_SAVE, actor);
    }

    /** 지정 출처로 라인 저장 단가를 즉시 기억한다. 실패는 caller fail-soft 경계로 전파한다. */
    public void remember(UUID partnerId, UUID productId, BigDecimal unitPrice, String source, String actor) {
        List<PartnerProductPriceMemoryCommand> commands = prepareAndDedupe(List.of(
                new PartnerProductPriceMemoryCommand(partnerId, productId, unitPrice, source, actor)));
        if (commands.isEmpty()) {
            return;
        }
        executeBatchInNewTransaction(commands);
    }

    /**
     * 가격기억 후보를 현재 트랜잭션 커밋 후 1회 set-based flush 한다.
     *
     * <p>command 에 원 전표/견적 트랜잭션의 논리 저장 시각을 커밋 전에 담는다. afterCommit 에서는
     * bounded executor 로 넘기고 즉시 반환하므로 outer connection cleanup 을 두 번째 connection
     * 획득이 막지 않는다. executor 포화/DB 실패는 계측 후 삼켜 원 저장의 fail-soft 계약을 유지한다.
     */
    public void rememberBatchAfterCommit(Collection<PartnerProductPriceMemoryCommand> commands, String context) {
        List<PartnerProductPriceMemoryCommand> deduped = prepareAndDedupe(commands);
        if (deduped.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleBatchFailSoft(deduped, context);
                }
            });
            return;
        }
        // outer 트랜잭션/커넥션이 없는 호출은 기존 즉시 관찰 계약을 유지한다.
        flushBatchFailSoft(deduped, context);
    }

    private void scheduleBatchFailSoft(List<PartnerProductPriceMemoryCommand> commands, String context) {
        try {
            priceMemoryExecutor.execute(() -> flushBatchFailSoft(commands, context));
        } catch (RuntimeException ex) {
            batchSizeSummary.record(commands.size());
            failedCounter.increment(commands.size());
            log.warn("partner-product price memory queue rejected context={} count={}",
                    context, commands.size(), ex);
        }
    }

    private void flushBatchFailSoft(List<PartnerProductPriceMemoryCommand> commands, String context) {
        try {
            executeBatchInNewTransaction(commands);
        } catch (RuntimeException ex) {
            log.warn("partner-product price memory batch upsert failed context={} count={}",
                    context, commands.size(), ex);
        }
    }

    private void executeBatchInNewTransaction(List<PartnerProductPriceMemoryCommand> commands) {
        batchSizeSummary.record(commands.size());
        long startedAtNanos = System.nanoTime();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                batchRepository.applyTransactionTimeouts(
                        properties.getLockTimeoutMs(), properties.getStatementTimeoutMs());
                batchRepository.upsertAll(commands, LocalDateTime.now(clock));
            });
            // recency guard 로 오래된 command 가 skip 되어도 statement 자체는 성공이다.
            successCounter.increment(commands.size());
        } catch (RuntimeException ex) {
            failedCounter.increment(commands.size());
            throw ex;
        } finally {
            upsertDuration.record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * command 를 정제·중복제거하고 전역 잠금 순서로 정렬해 반환한다.
     *
     * <p>같은 pair 는 문서 라인 순서상 마지막 값이 이긴다 (LinkedHashMap 덮어쓰기 — dedup 은
     * 정렬 이전에 끝난다). 반환 직전 {@link #PAIR_LOCK_ORDER} 정렬은 두 문서가 같은 pair 집합을
     * 서로 반대 라인 순서로 담아 동시에 flush 될 때 단일 upsert statement 가 행을 역순으로 잠가
     * 발생하는 PostgreSQL deadlock(→ fail-soft 배치 통째 유실)을 구조적으로 제거한다.
     * set-based upsert 결과는 행 순서와 무관하므로 저장 값은 동일하다.
     */
    private List<PartnerProductPriceMemoryCommand> prepareAndDedupe(
            Collection<PartnerProductPriceMemoryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        LocalDateTime logicalEventTime = LocalDateTime.now(clock);
        Map<PartnerProductKey, PartnerProductPriceMemoryCommand> byPair = new LinkedHashMap<>();
        for (PartnerProductPriceMemoryCommand command : commands) {
            if (command == null || command.partnerId() == null
                    || command.productId() == null || command.unitPrice() == null) {
                continue;
            }
            String actor = command.actor() == null || command.actor().isBlank() ? "system" : command.actor();
            LocalDateTime rememberedAt = command.rememberedAt() == null
                    ? logicalEventTime
                    : command.rememberedAt();
            PartnerProductPriceMemoryCommand prepared = new PartnerProductPriceMemoryCommand(
                    command.partnerId(), command.productId(), command.unitPrice(),
                    normalizeSource(command.source()), actor, rememberedAt);
            byPair.put(new PartnerProductKey(command.partnerId(), command.productId()), prepared);
        }
        List<PartnerProductPriceMemoryCommand> deduped = new ArrayList<>(byPair.values());
        deduped.sort(PAIR_LOCK_ORDER);
        return deduped;
    }

    private String normalizeSource(String source) {
        if (PartnerProductPriceMemory.SOURCE_BUNDLE_SET.equals(source)) {
            return PartnerProductPriceMemory.SOURCE_BUNDLE_SET;
        }
        return PartnerProductPriceMemory.SOURCE_LINE_SAVE;
    }

    private record PartnerProductKey(UUID partnerId, UUID productId) {
    }
}
