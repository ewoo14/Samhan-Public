package com.samhanair.logis.slip.price.service;

import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 거래처+품목 최근 수동단가 기억 서비스. */
@Service
@RequiredArgsConstructor
public class PartnerProductPriceMemoryService {

    private final PartnerProductPriceMemoryRepository repository;

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
        if (partnerId == null || productId == null || unitPrice == null) {
            return;
        }
        String effectiveActor = actor == null || actor.isBlank() ? "system" : actor;
        repository.upsert(UUID.randomUUID(), partnerId, productId, unitPrice,
                PartnerProductPriceMemory.SOURCE_LINE_SAVE, effectiveActor, LocalDateTime.now());
    }
}
