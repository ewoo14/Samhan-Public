package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.JournalNumberSequence;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** JournalNumberSequence — 일자별 채번 시퀀스 ({@code journal_date} unique). */
public interface JournalNumberSequenceRepository
        extends JpaRepository<JournalNumberSequence, UUID> {

    /** 해당 날짜의 시퀀스 조회. 없으면 호출 측이 {@link JournalNumberSequence#create} 후 저장. */
    Optional<JournalNumberSequence> findByJournalDate(LocalDate journalDate);
}
