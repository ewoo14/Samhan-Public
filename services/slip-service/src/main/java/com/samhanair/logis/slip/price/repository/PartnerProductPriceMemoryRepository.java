package com.samhanair.logis.slip.price.repository;

import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 거래처+품목 최근 수동단가 기억 저장소. */
public interface PartnerProductPriceMemoryRepository extends JpaRepository<PartnerProductPriceMemory, UUID> {

    /** 활성 가격기억 단건 조회. */
    Optional<PartnerProductPriceMemory> findByPartnerIdAndProductId(UUID partnerId, UUID productId);

    /**
     * 최근 라인 저장 단가를 upsert 한다.
     *
     * <p>soft-delete 된 같은 partner/product row 가 있으면 활성화하면서 단가를 갱신한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO partner_product_price_memory
                (id, partner_id, product_id, unit_price, source,
                 created_at, created_by, is_deleted)
            VALUES
                (:id, :partnerId, :productId, :unitPrice, :source,
                 :ts, :actor, FALSE)
            ON CONFLICT (partner_id, product_id) DO UPDATE
            SET unit_price = EXCLUDED.unit_price,
                source = EXCLUDED.source,
                modified_at = :ts,
                modified_by = :actor,
                deleted_at = NULL,
                deleted_by = NULL,
                is_deleted = FALSE
            """, nativeQuery = true)
    void upsert(@Param("id") UUID id,
                @Param("partnerId") UUID partnerId,
                @Param("productId") UUID productId,
                @Param("unitPrice") BigDecimal unitPrice,
                @Param("source") String source,
                @Param("actor") String actor,
                @Param("ts") LocalDateTime ts);
}
