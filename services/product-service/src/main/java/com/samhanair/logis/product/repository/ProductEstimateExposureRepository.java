package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.EstimateCategory;
import com.samhanair.logis.product.domain.ProductEstimateExposure;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 품목 견적 카탈로그 M:N 노출 repository. */
public interface ProductEstimateExposureRepository extends JpaRepository<ProductEstimateExposure, UUID> {

    List<ProductEstimateExposure> findByProductIdAndIsDeletedFalse(UUID productId);

    List<ProductEstimateExposure> findByProductIdInAndIsDeletedFalse(Collection<UUID> productIds);

    List<ProductEstimateExposure> findByEstimateCategoryAndIsDeletedFalse(EstimateCategory estimateCategory);

    Optional<ProductEstimateExposure> findByProductIdAndEstimateCategoryAndIsDeletedFalse(
            UUID productId, EstimateCategory estimateCategory);

    List<ProductEstimateExposure> findByProductIdInAndEstimateCategoryAndIsDeletedFalse(
            Collection<UUID> productIds, EstimateCategory estimateCategory);

    @Query("""
            SELECT COALESCE(MAX(e.displayOrder), 0)
              FROM ProductEstimateExposure e, Product p
             WHERE e.estimateCategory = :estimateCategory
               AND p.id = e.productId
               AND e.isDeleted = false
               AND p.isDeleted = false
            """)
    Integer maxDisplayOrder(@Param("estimateCategory") EstimateCategory estimateCategory);
}
