package com.samhanair.logis.slip.repository;

import com.samhanair.logis.slip.domain.SlipNumberSequence;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** SlipNumberSequence — 날짜별 채번 시퀀스. {@code slip_date} 는 unique. */
public interface SlipNumberSequenceRepository extends JpaRepository<SlipNumberSequence, UUID> {

    /** 해당 날짜의 시퀀스 조회. 없으면 호출 측이 {@link SlipNumberSequence#create} 후 저장. */
    Optional<SlipNumberSequence> findBySlipDate(LocalDate slipDate);
}
