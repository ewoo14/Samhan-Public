package com.samhanair.logis.slip.estimate.repository;

import com.samhanair.logis.slip.estimate.domain.EstimateNumberSequence;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 견적번호 시퀀스 — 일자별 단건 조회. */
public interface EstimateNumberSequenceRepository extends JpaRepository<EstimateNumberSequence, UUID> {

    Optional<EstimateNumberSequence> findByEstimateDate(LocalDate estimateDate);
}
