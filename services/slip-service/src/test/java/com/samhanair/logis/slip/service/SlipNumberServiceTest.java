package com.samhanair.logis.slip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.samhanair.logis.slip.domain.SlipNumberSequence;
import com.samhanair.logis.slip.repository.SlipNumberSequenceRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SlipNumberService — 날짜별 채번 + 시퀀스 자동 생성 + 동일 날짜 다중 호출 시 순번 증가. */
@ExtendWith(MockitoExtension.class)
class SlipNumberServiceTest {

    @Mock private SlipNumberSequenceRepository sequenceRepository;

    @InjectMocks private SlipNumberService service;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.of(2026, 5, 4);
    }

    @Test
    void next_firstCall_createsSequence_andReturns001() {
        when(sequenceRepository.findBySlipDate(today)).thenReturn(Optional.empty());
        when(sequenceRepository.save(any(SlipNumberSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String slipNo = service.next(today);

        assertThat(slipNo).isEqualTo("2026/05/04-001");
    }

    @Test
    void next_existingSequence_incrementsLastSeq() {
        SlipNumberSequence existing = SlipNumberSequence.create(today);
        existing.next(); // lastSeq=1
        existing.next(); // lastSeq=2
        when(sequenceRepository.findBySlipDate(today)).thenReturn(Optional.of(existing));

        String slipNo = service.next(today);

        assertThat(slipNo).isEqualTo("2026/05/04-003");
    }

    @Test
    void next_twoCallsSameDay_returnSequentialNos() {
        SlipNumberSequence seq = SlipNumberSequence.create(today);
        when(sequenceRepository.findBySlipDate(today)).thenReturn(Optional.of(seq));

        String first = service.next(today);
        String second = service.next(today);

        assertThat(first).isEqualTo("2026/05/04-001");
        assertThat(second).isEqualTo("2026/05/04-002");
    }

    @Test
    void extractSeqNo_parsesTrailingNumber() {
        assertThat(service.extractSeqNo("2026/05/04-005")).isEqualTo(5);
        assertThat(service.extractSeqNo("2026/05/04-123")).isEqualTo(123);
    }

    @Test
    void next_differentDates_independentSequences() {
        LocalDate yesterday = today.minusDays(1);
        SlipNumberSequence todaySeq = SlipNumberSequence.create(today);
        SlipNumberSequence yesterdaySeq = SlipNumberSequence.create(yesterday);
        yesterdaySeq.next();
        yesterdaySeq.next();
        when(sequenceRepository.findBySlipDate(today)).thenReturn(Optional.of(todaySeq));
        when(sequenceRepository.findBySlipDate(yesterday)).thenReturn(Optional.of(yesterdaySeq));

        assertThat(service.next(today)).isEqualTo("2026/05/04-001");
        assertThat(service.next(yesterday)).isEqualTo("2026/05/03-003");
    }
}
