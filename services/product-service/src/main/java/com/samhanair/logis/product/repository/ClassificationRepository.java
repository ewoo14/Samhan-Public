package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.Classification;
import com.samhanair.logis.product.domain.EstimateCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Classification soft-delete 는 엔티티 {@code @SQLRestriction} 으로 강제한다. */
public interface ClassificationRepository extends JpaRepository<Classification, UUID> {

    List<Classification> findByEstimateCategoryAndParentIsNullOrderByDisplayOrderAsc(
            EstimateCategory estimateCategory);

    List<Classification> findByParent_IdOrderByDisplayOrderAsc(UUID parentId);

    Optional<Classification> findByEstimateCategoryAndCatLevelAndNameAndIsDeletedFalse(
            EstimateCategory estimateCategory, Classification.CatLevel catLevel, String name);

    Optional<Classification> findByEstimateCategoryAndCatLevelAndParent_IdAndNameAndIsDeletedFalse(
            EstimateCategory estimateCategory, Classification.CatLevel catLevel, UUID parentId, String name);

    boolean existsByParent_IdAndIsDeletedFalse(UUID parentId);

    @Query("""
            SELECT COALESCE(MAX(c.displayOrder), 0)
              FROM Classification c
             WHERE c.estimateCategory = :estimateCategory
               AND c.catLevel = :catLevel
               AND ((:parentId IS NULL AND c.parent IS NULL)
                    OR (:parentId IS NOT NULL AND c.parent.id = :parentId))
            """)
    int maxDisplayOrder(@Param("estimateCategory") EstimateCategory estimateCategory,
                        @Param("catLevel") Classification.CatLevel catLevel,
                        @Param("parentId") UUID parentId);
}
