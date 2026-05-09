package com.samhanair.logis.slip.estimate.repository;

import com.samhanair.logis.slip.estimate.domain.EstimateLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 견적 라인 — 헤더 cascade 로 대부분 처리되지만, 직접 조회 용도. */
public interface EstimateLineRepository extends JpaRepository<EstimateLine, UUID> {

    List<EstimateLine> findByEstimateIdAndIsDeletedFalseOrderByLineNoAsc(UUID estimateId);
}
