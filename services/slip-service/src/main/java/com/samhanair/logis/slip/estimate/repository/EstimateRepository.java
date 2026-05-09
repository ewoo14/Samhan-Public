package com.samhanair.logis.slip.estimate.repository;

import com.samhanair.logis.slip.estimate.domain.Estimate;
import com.samhanair.logis.slip.estimate.domain.EstimateStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 견적서 헤더 — 단건 / 필터 페이지 조회. partial UNIQUE INDEX 는 V13 SQL 의
 * {@code estimate_no WHERE is_deleted=false} 에 적용.
 */
public interface EstimateRepository extends JpaRepository<Estimate, UUID> {

    /** 견적번호 단건 조회. soft-delete 제외. */
    Optional<Estimate> findByEstimateNo(String estimateNo);

    /** 활성 전체 페이지. */
    Page<Estimate> findAllByIsDeletedFalse(Pageable pageable);

    /** 상태별 페이지. */
    Page<Estimate> findAllByStatusAndIsDeletedFalse(EstimateStatus status, Pageable pageable);

    /** 거래처별 페이지. */
    Page<Estimate> findAllByPartnerIdAndIsDeletedFalse(UUID partnerId, Pageable pageable);

    /** 상태 + 거래처 동시 필터. */
    Page<Estimate> findAllByStatusAndPartnerIdAndIsDeletedFalse(
            EstimateStatus status, UUID partnerId, Pageable pageable);

    /** 기간 필터 (estimate_date BETWEEN). */
    Page<Estimate> findAllByEstimateDateBetweenAndIsDeletedFalse(
            LocalDate startDate, LocalDate endDate, Pageable pageable);

    /** 상태 + 기간 동시 필터. */
    Page<Estimate> findAllByStatusAndEstimateDateBetweenAndIsDeletedFalse(
            EstimateStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
