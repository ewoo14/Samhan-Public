package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.NoteStatus;
import com.samhanair.logis.accounting.domain.NotesReceivable;
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
}
