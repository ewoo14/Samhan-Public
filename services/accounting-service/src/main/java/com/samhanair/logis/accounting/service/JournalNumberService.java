package com.samhanair.logis.accounting.service;

import com.samhanair.logis.accounting.domain.JournalNumberSequence;
import com.samhanair.logis.accounting.repository.JournalNumberSequenceRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분개번호 채번 — {@code yyyyMMdd-N} 형식. 날짜별 {@link JournalNumberSequence} 시퀀스를
 * 트랜잭션 안에서 조회/생성/증가시키고 {@code yyyyMMdd-N} 문자열로 포맷한다.
 *
 * <p>{@link com.samhanair.logis.accounting.service.JournalNumberService} 는 SlipNumberService 답습.
 * partial UNIQUE INDEX 가 백업.
 */
@Service
@RequiredArgsConstructor
public class JournalNumberService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JournalNumberSequenceRepository sequenceRepository;

    /**
     * 다음 분개번호 채번 — 시퀀스 조회 → 없으면 새로 생성 → next() → {@code yyyyMMdd-N} 포맷.
     *
     * @param journalDate 채번 기준 날짜
     * @return {@code yyyyMMdd-N} 형식 분개번호 (N 은 1, 2, 3, ... 자릿수 가변)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String next(LocalDate journalDate) {
        JournalNumberSequence seq = sequenceRepository.findByJournalDate(journalDate)
                .orElseGet(() -> sequenceRepository.save(JournalNumberSequence.create(journalDate)));
        int seqNo = seq.next();
        return journalDate.format(DATE_FMT) + "-" + seqNo;
    }
}
