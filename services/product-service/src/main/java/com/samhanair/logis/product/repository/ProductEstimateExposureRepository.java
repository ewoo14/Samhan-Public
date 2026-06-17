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

    /**
     * 대상 견적 카테고리에 현재 활성 노출된 전체 품목 노출을 조회한다.
     *
     * <p>display-orders 일괄 갱신은 부분 요청으로 기존 순서를 붕괴시키지 않도록
     * 이 결과의 productId 집합과 요청 productId 집합이 같은지 먼저 검증한다.
     *
     * @param estimateCategory 견적 카테고리
     * @return 삭제되지 않은 품목과 삭제되지 않은 노출의 교집합
     */
    @Query("""
            SELECT e
              FROM ProductEstimateExposure e, Product p
             WHERE e.estimateCategory = :estimateCategory
               AND p.id = e.productId
               AND e.isDeleted = false
               AND p.isDeleted = false
            """)
    List<ProductEstimateExposure> findActiveProductExposuresByEstimateCategory(
            @Param("estimateCategory") EstimateCategory estimateCategory);

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
