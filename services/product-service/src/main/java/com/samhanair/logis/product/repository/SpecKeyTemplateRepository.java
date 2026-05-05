package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.EstimateCategory;
import com.samhanair.logis.product.domain.SpecKeyTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SpecKeyTemplate CRUD + 카테고리별 추천 키 조회. */
public interface SpecKeyTemplateRepository extends JpaRepository<SpecKeyTemplate, UUID> {

    List<SpecKeyTemplate> findByEstimateCategoryOrderByDisplayOrderAsc(EstimateCategory estimateCategory);

    List<SpecKeyTemplate> findByEstimateCategoryAndIsRecommendedTrueOrderByDisplayOrderAsc(EstimateCategory estimateCategory);
}
