package com.samhanair.logis.slip.price.service;

import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 거래처+품목 최근 수동단가 기억 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerProductPriceMemoryService {

    private static final String UPSERT_FAILED_COUNTER = "slip_price_memory_upsert_failed_total";

    private final PartnerProductPriceMemoryRepository repository;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    private final MeterRegistry meterRegistry;

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
     * 라인 저장 단가를 기억한다.
     *
     * <p>본 저장은 REQUIRES_NEW 로 호출자 트랜잭션과 격리한다. fail-soft 계약은 호출자 책임이다.
     * 제약조건 위반 등은 트랜잭션 커밋 시점에 발생할 수 있으므로 본 메서드는 예외를 삼키지 않고,
     * 호출자가 try/catch 로 이 가격기억 트랜잭션만 롤백시키며 전표/견적 저장 흐름을 계속해야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remember(UUID partnerId, UUID productId, BigDecimal unitPrice, String actor) {
        remember(partnerId, productId, unitPrice, PartnerProductPriceMemory.SOURCE_LINE_SAVE, actor);
    }

    /**
     * 지정 출처로 라인 저장 단가를 즉시 기억한다.
     *
     * <p>세트 부모 품목은 {@code BUNDLE_SET}, 일반 라인은 {@code LINE_SAVE} 로 구분한다.
     * fail-soft 는 호출자 책임이므로 예외를 그대로 던진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remember(UUID partnerId, UUID productId, BigDecimal unitPrice, String source, String actor) {
        if (partnerId == null || productId == null || unitPrice == null) {
            return;
        }
        String effectiveActor = actor == null || actor.isBlank() ? "system" : actor;
        repository.upsert(UUID.randomUUID(), partnerId, productId, unitPrice,
                normalizeSource(source), effectiveActor, LocalDateTime.now(clock));
    }

    /**
     * 가격기억 후보를 현재 트랜잭션 커밋 후 1회 배치 flush 한다.
     *
     * <p>같은 저장 단위 안에서 동일 partner/product 가 여러 번 들어오면 마지막 값을 남긴다.
     * 원 전표/견적 트랜잭션이 롤백되면 afterCommit 이 호출되지 않아 유령 단가가 남지 않는다.
     *
     * @param commands 가격기억 후보 목록
     * @param context 로그 식별자
     */
    public void rememberBatchAfterCommit(Collection<PartnerProductPriceMemoryCommand> commands, String context) {
        List<PartnerProductPriceMemoryCommand> deduped = dedupe(commands);
        if (deduped.isEmpty()) {
            return;
        }
        Runnable flush = () -> flushBatchFailSoft(deduped, context);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    flush.run();
                }
            });
            return;
        }
        flush.run();
    }

    private void flushBatchFailSoft(List<PartnerProductPriceMemoryCommand> commands, String context) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> {
                LocalDateTime now = LocalDateTime.now(clock);
                for (PartnerProductPriceMemoryCommand command : commands) {
                    String actor = command.actor() == null || command.actor().isBlank() ? "system" : command.actor();
                    repository.upsert(UUID.randomUUID(), command.partnerId(), command.productId(),
                            command.unitPrice(), normalizeSource(command.source()), actor, now);
                }
            });
        } catch (RuntimeException ex) {
            meterRegistry.counter(UPSERT_FAILED_COUNTER).increment();
            log.warn("partner-product price memory batch upsert failed context={} count={}",
                    context, commands.size(), ex);
        }
    }

    private List<PartnerProductPriceMemoryCommand> dedupe(Collection<PartnerProductPriceMemoryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        Map<String, PartnerProductPriceMemoryCommand> byPair = new LinkedHashMap<>();
        for (PartnerProductPriceMemoryCommand command : commands) {
            if (command == null || command.partnerId() == null
                    || command.productId() == null || command.unitPrice() == null) {
                continue;
            }
            byPair.put(command.partnerId() + ":" + command.productId(), command);
        }
        return new ArrayList<>(byPair.values());
    }

    private String normalizeSource(String source) {
        if (PartnerProductPriceMemory.SOURCE_BUNDLE_SET.equals(source)) {
            return PartnerProductPriceMemory.SOURCE_BUNDLE_SET;
        }
        return PartnerProductPriceMemory.SOURCE_LINE_SAVE;
    }
}
