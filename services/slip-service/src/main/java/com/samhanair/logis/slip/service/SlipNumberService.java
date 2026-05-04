package com.samhanair.logis.slip.service;

import com.samhanair.logis.slip.domain.SlipNumberSequence;
import com.samhanair.logis.slip.repository.SlipNumberSequenceRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전표번호 채번 — {@code yyyy/MM/dd-NNN} 형식. 날짜별 {@link SlipNumberSequence} 시퀀스를
 * 트랜잭션 안에서 조회/생성/증가시키고 {@code yyyy/MM/dd-NNN} 문자열로 포맷한다.
 *
 * <p>동시 충돌은 {@code slips(slip_no) WHERE is_deleted=false} 의 partial unique 인덱스가
 * 백업 — 호출 측이 충돌 시 재시도 정책 결정.
 */
@Service
@RequiredArgsConstructor
public class SlipNumberService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String SEQ_FMT = "%03d";

    private final SlipNumberSequenceRepository sequenceRepository;

    /**
     * 다음 전표번호를 채번한다 — 시퀀스 조회 → 없으면 새로 생성 → next() → {@code yyyy/MM/dd-NNN} 포맷.
     *
     * <p>호출 트랜잭션이 있으면 합류, 없으면 새로 시작 (REQUIRED). 같은 트랜잭션 안에서 호출되면
     * lastSeq 갱신은 같은 영속성 컨텍스트에서 일어난다.
     *
     * @param slipDate 채번 기준 날짜
     * @return {@code yyyy/MM/dd-NNN} 형식 전표번호 문자열
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String next(LocalDate slipDate) {
        SlipNumberSequence seq = sequenceRepository.findBySlipDate(slipDate)
                .orElseGet(() -> sequenceRepository.save(SlipNumberSequence.create(slipDate)));
        int seqNo = seq.next();
        return slipDate.format(DATE_FMT) + "-" + String.format(SEQ_FMT, seqNo);
    }

    /**
     * {@link #next} 의 결과 문자열에서 순번 부분만 분리해 반환 — 도메인의 {@code seqNo} 컬럼 채움용.
     *
     * @param slipNo {@link #next} 가 반환한 문자열
     * @return 순번 정수 (1, 2, 3, ...)
     */
    public int extractSeqNo(String slipNo) {
        int dashIdx = slipNo.lastIndexOf('-');
        return Integer.parseInt(slipNo.substring(dashIdx + 1));
    }
}
