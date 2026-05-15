package com.samhanair.logis.slip.repository;

import com.samhanair.logis.slip.domain.SlipNumberSequence;
import com.samhanair.logis.slip.domain.SlipType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SlipNumberSequence — 날짜 + 전표 유형별 채번 시퀀스. */
public interface SlipNumberSequenceRepository extends JpaRepository<SlipNumberSequence, UUID> {

    /** 해당 날짜 + 전표 유형의 시퀀스 조회. */
    Optional<SlipNumberSequence> findBySlipDateAndSlipType(LocalDate slipDate, SlipType slipType);
}
