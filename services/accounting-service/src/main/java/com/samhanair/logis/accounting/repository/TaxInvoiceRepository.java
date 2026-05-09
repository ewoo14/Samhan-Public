package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.TaxInvoice;
import com.samhanair.logis.accounting.domain.TaxInvoiceStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * TaxInvoice — 세금계산서. 필터: status / 공급일자 [from, to] / partnerId.
 * 모두 nullable (null 이면 해당 조건 무시).
 */
public interface TaxInvoiceRepository extends JpaRepository<TaxInvoice, UUID> {

    /**
     * 페이지 조회 — 4개 필터 조합 (status, from, to, partnerId). null 인 필터는 무시.
     */
    @Query("""
            SELECT t FROM TaxInvoice t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:from IS NULL OR t.supplyDate >= :from)
              AND (:to IS NULL OR t.supplyDate <= :to)
              AND (:partnerId IS NULL OR t.partnerId = :partnerId)
            """)
    Page<TaxInvoice> findByFilters(@Param("status") TaxInvoiceStatus status,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("partnerId") UUID partnerId,
                                   Pageable pageable);

    /**
     * 발행 상태 + 공급일자 범위 list 조회 (PR-E2 BE-A11 hometax export 용).
     *
     * <p>페이지 없이 전체 — caller (HometaxExportService) 가 100건 단위 sheet 분할.
     * 일반적으로 일별/주간 export 라 수십~수백 건 규모.
     */
    @Query("""
            SELECT t FROM TaxInvoice t
            WHERE t.status = :status
              AND t.supplyDate >= :from
              AND t.supplyDate <= :to
            ORDER BY t.supplyDate ASC, t.taxInvoiceNo ASC
            """)
    java.util.List<TaxInvoice> findIssuedInRange(@Param("status") TaxInvoiceStatus status,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);
}
