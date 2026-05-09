package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.TaxInvoiceNumberSequence;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** TaxInvoiceNumberSequence — 일자별 채번 시퀀스 ({@code issue_date} unique). */
public interface TaxInvoiceNumberSequenceRepository
        extends JpaRepository<TaxInvoiceNumberSequence, UUID> {

    /** 해당 날짜의 시퀀스 조회. 없으면 호출 측이 {@link TaxInvoiceNumberSequence#create} 후 저장. */
    Optional<TaxInvoiceNumberSequence> findByIssueDate(LocalDate issueDate);
}
