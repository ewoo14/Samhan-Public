package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.NoteStatus;
import com.samhanair.logis.accounting.domain.NotesReceivable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 받을어음 repository. */
public interface NotesReceivableRepository extends JpaRepository<NotesReceivable, UUID> {

    boolean existsByNoteNoAndIsDeletedFalse(String noteNo);

    Optional<NotesReceivable> findByNoteNoAndIsDeletedFalse(String noteNo);

    @Query("""
            SELECT n FROM NotesReceivable n
            WHERE (:status IS NULL OR n.status = :status)
              AND (:partnerId IS NULL OR n.partnerId = :partnerId)
            ORDER BY n.maturityDate ASC, n.issueDate ASC, n.noteNo ASC
            """)
    List<NotesReceivable> search(@Param("status") NoteStatus status,
                                 @Param("partnerId") UUID partnerId);

    @Query("""
            SELECT n FROM NotesReceivable n
            WHERE n.partnerId = :partnerId
              AND n.status IN (
                com.samhanair.logis.accounting.domain.NoteStatus.BOARDING,
                com.samhanair.logis.accounting.domain.NoteStatus.COLLECTING
              )
            ORDER BY n.maturityDate ASC, n.noteNo ASC
            """)
    List<NotesReceivable> findCollectionSuggestionCandidates(@Param("partnerId") UUID partnerId);

    /**
     * 채권채무 현황 병기용 받을어음 거래처별 집계.
     *
     * <p>BOARDING/COLLECTING 상태만 보유액에 포함하고, 기준일~기준일+30일 만기분을
     * 만기임박액으로 별도 합산한다.
     */
    @Query("""
            SELECT n.partnerId AS partnerId,
                   COALESCE(SUM(n.amount), 0) AS heldAmount,
                   COALESCE(SUM(CASE
                       WHEN n.maturityDate >= :asOfDate AND n.maturityDate <= :maturityUntil
                       THEN n.amount ELSE 0 END), 0) AS maturingSoonAmount
            FROM NotesReceivable n
            WHERE n.status IN (
                com.samhanair.logis.accounting.domain.NoteStatus.BOARDING,
                com.samhanair.logis.accounting.domain.NoteStatus.COLLECTING
              )
            GROUP BY n.partnerId
            """)
    List<NoteExposureTotal> aggregateOpenExposureByPartner(@Param("asOfDate") LocalDate asOfDate,
                                                           @Param("maturityUntil") LocalDate maturityUntil);

    /** Spring Data JPA projection — 거래처별 받을어음 보유/만기임박 합계. */
    interface NoteExposureTotal {
        UUID getPartnerId();
        BigDecimal getHeldAmount();
        BigDecimal getMaturingSoonAmount();
    }
}
