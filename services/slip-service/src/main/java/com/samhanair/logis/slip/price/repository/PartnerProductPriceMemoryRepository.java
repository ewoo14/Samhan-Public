package com.samhanair.logis.slip.price.repository;

import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 거래처+품목 최근 수동단가 기억 저장소. */
public interface PartnerProductPriceMemoryRepository extends JpaRepository<PartnerProductPriceMemory, UUID> {

    /** 활성 가격기억 단건 조회. */
    Optional<PartnerProductPriceMemory> findByPartnerIdAndProductId(UUID partnerId, UUID productId);

    /** 같은 거래처의 요청 품목 중 활성 가격기억 hit 만 조회한다. */
    List<PartnerProductPriceMemory> findAllByPartnerIdAndProductIdIn(
            UUID partnerId, Collection<UUID> productIds);
}
