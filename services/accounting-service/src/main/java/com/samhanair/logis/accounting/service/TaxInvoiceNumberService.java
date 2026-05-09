package com.samhanair.logis.accounting.service;

import com.samhanair.logis.accounting.domain.TaxInvoiceNumberSequence;
import com.samhanair.logis.accounting.repository.TaxInvoiceNumberSequenceRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세금계산서 발행번호 채번 — {@code yyyyMMdd-NNNN} 형식 (NNNN 4자리 zero-pad).
 *
 * <p>JournalNumberService 답습 패턴. partial UNIQUE INDEX 백업.
 */
@Service
@RequiredArgsConstructor
public class TaxInvoiceNumberService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TaxInvoiceNumberSequenceRepository sequenceRepository;

    /**
     * 다음 발행번호 채번 — 시퀀스 조회 → 없으면 새로 생성 → next() → {@code yyyyMMdd-NNNN} 포맷.
     *
     * @param issueDate 채번 기준 날짜 (보통 supplyDate)
     * @return {@code yyyyMMdd-NNNN} 형식 발행번호 (NNNN 4자리)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String next(LocalDate issueDate) {
        TaxInvoiceNumberSequence seq = sequenceRepository.findByIssueDate(issueDate)
                .orElseGet(() -> sequenceRepository.save(TaxInvoiceNumberSequence.create(issueDate)));
        int seqNo = seq.next();
        return issueDate.format(DATE_FMT) + "-" + String.format("%04d", seqNo);
    }
}
