package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.CollectionPlan;
import com.samhanair.logis.accounting.domain.PlanBasis;
import com.samhanair.logis.accounting.domain.PlanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 수금계획 repository. */
public interface CollectionPlanRepository extends JpaRepository<CollectionPlan, UUID> {

    boolean existsByPlanNoAndIsDeletedFalse(String planNo);

    Optional<CollectionPlan> findByPlanNoAndIsDeletedFalse(String planNo);

    boolean existsByPartnerIdAndBasisAndSourceReferenceAndStatusInAndIsDeletedFalse(
            UUID partnerId,
            PlanBasis basis,
            String sourceReference,
            List<PlanStatus> statuses);

    @Query("""
            SELECT p FROM CollectionPlan p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:partnerId IS NULL OR p.partnerId = :partnerId)
            ORDER BY p.plannedDate ASC, p.planNo ASC
            """)
    List<CollectionPlan> search(@Param("status") PlanStatus status,
                                @Param("partnerId") UUID partnerId);

    @Query("""
            SELECT p FROM CollectionPlan p
            WHERE p.plannedDate >= :from
              AND p.plannedDate <= :to
              AND p.status <> com.samhanair.logis.accounting.domain.PlanStatus.COLLECTED
            ORDER BY p.plannedDate ASC, p.planNo ASC
            """)
    List<CollectionPlan> findOpenForecastRows(@Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
