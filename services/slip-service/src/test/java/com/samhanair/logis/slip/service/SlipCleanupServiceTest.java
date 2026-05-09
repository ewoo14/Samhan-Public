package com.samhanair.logis.slip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.repository.SlipRepository;
import com.samhanair.logis.slip.web.dto.SlipCleanupResponse;
import com.samhanair.logis.slip.web.dto.SlipCleanupResponse.CleanupEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PR-E1 BE-A6 — SlipCleanupService 단위 테스트 4 case + 1 가드 case = 5.
 *
 * <p>Test case (정합성 flag 별):
 * <ol>
 *   <li>정상 — flag 4종 모두 false, status/partner 그룹핑 정상</li>
 *   <li>partnerCodeMissing + linesMissing + amountZero — 라인 0건 + partner_code null</li>
 *   <li>regionMissing — region 만 null, 그 외 정상</li>
 *   <li>status/partner 그룹핑 — 같은 status/partner 슬립 N건 카운트 정확</li>
 *   <li>가드 — to < from 시 BusinessException(INVALID_INPUT)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SlipCleanupServiceTest {

    @Mock private SlipRepository slipRepository;

    @InjectMocks private SlipCleanupService service;

    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        from = LocalDate.of(2026, 5, 1);
        to = LocalDate.of(2026, 5, 31);
    }

    /** 도메인 reflection 헬퍼 — protected ctor 우회 + 라인 N건 동봉. */
    private Slip mockSlip(String slipNo, SlipStatus status,
                          String partnerCode, String partnerName,
                          String regionGroup, List<BigDecimal> lineTotals) {
        Slip slip = new Slip() { };
        ReflectionTestUtils.setField(slip, "slipNo", slipNo);
        ReflectionTestUtils.setField(slip, "slipDate", LocalDate.of(2026, 5, 15));
        ReflectionTestUtils.setField(slip, "status", status);
        ReflectionTestUtils.setField(slip, "partnerCode", partnerCode);
        ReflectionTestUtils.setField(slip, "partnerName", partnerName);
        ReflectionTestUtils.setField(slip, "classifiedRegionGroup", regionGroup);
        List<SlipLine> lines = new ArrayList<>();
        for (BigDecimal total : lineTotals) {
            SlipLine line = new SlipLine() { };
            ReflectionTestUtils.setField(line, "lineTotal", total);
            lines.add(line);
        }
        ReflectionTestUtils.setField(slip, "lines", lines);
        return slip;
    }

    @Test
    void cleanup_normalSlip_allFlagsFalse() {
        Slip slip = mockSlip("2026/05/15-001", SlipStatus.CONFIRMED,
                "P-2026-0001", "정상거래처", "서울특별시",
                List.of(new BigDecimal("100.00")));
        when(slipRepository.findAllBySlipDateBetweenAndIsDeletedFalse(from, to))
                .thenReturn(List.of(slip));

        SlipCleanupResponse res = service.buildCleanupReport(from, to);

        assertThat(res.totalSlips()).isEqualTo(1);
        assertThat(res.entries()).hasSize(1);
        CleanupEntry e = res.entries().get(0);
        assertThat(e.partnerCodeMissing()).isFalse();
        assertThat(e.amountZero()).isFalse();
        assertThat(e.linesMissing()).isFalse();
        assertThat(e.regionMissing()).isFalse();
        assertThat(e.totalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(e.lineCount()).isEqualTo(1);
    }

    @Test
    void cleanup_partnerNullAndLinesEmpty_setsMultipleFlags() {
        Slip slip = mockSlip("2026/05/15-002", SlipStatus.DRAFT,
                null, null, "경기남부", List.of());
        when(slipRepository.findAllBySlipDateBetweenAndIsDeletedFalse(from, to))
                .thenReturn(List.of(slip));

        SlipCleanupResponse res = service.buildCleanupReport(from, to);

        CleanupEntry e = res.entries().get(0);
        assertThat(e.partnerCodeMissing()).isTrue();
        assertThat(e.linesMissing()).isTrue();
        assertThat(e.amountZero()).isTrue(); // 라인 0건 → 합계 0
        assertThat(e.regionMissing()).isFalse();
        // partner 그룹핑 — partner_code null → "(미매핑)" key
        assertThat(res.byPartner()).extracting(SlipCleanupResponse.PartnerCount::partnerCode)
                .contains("(미매핑)");
    }

    @Test
    void cleanup_regionMissing_setsRegionFlagOnly() {
        Slip slip = mockSlip("2026/05/15-003", SlipStatus.SAVED,
                "P-2026-0002", "거래처B", null,
                List.of(new BigDecimal("50.00"), new BigDecimal("25.00")));
        when(slipRepository.findAllBySlipDateBetweenAndIsDeletedFalse(from, to))
                .thenReturn(List.of(slip));

        SlipCleanupResponse res = service.buildCleanupReport(from, to);

        CleanupEntry e = res.entries().get(0);
        assertThat(e.regionMissing()).isTrue();
        assertThat(e.partnerCodeMissing()).isFalse();
        assertThat(e.amountZero()).isFalse();
        assertThat(e.linesMissing()).isFalse();
        assertThat(e.totalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void cleanup_groupingByStatusAndPartner_counts() {
        Slip s1 = mockSlip("2026/05/15-004", SlipStatus.SAVED,
                "P-2026-0001", "거래처A", "서울특별시",
                List.of(new BigDecimal("10.00")));
        Slip s2 = mockSlip("2026/05/15-005", SlipStatus.SAVED,
                "P-2026-0001", "거래처A", "서울특별시",
                List.of(new BigDecimal("20.00")));
        Slip s3 = mockSlip("2026/05/15-006", SlipStatus.CONFIRMED,
                "P-2026-0002", "거래처B", "경기남부",
                List.of(new BigDecimal("30.00")));
        when(slipRepository.findAllBySlipDateBetweenAndIsDeletedFalse(from, to))
                .thenReturn(List.of(s1, s2, s3));

        SlipCleanupResponse res = service.buildCleanupReport(from, to);

        assertThat(res.totalSlips()).isEqualTo(3);
        // status: SAVED=2, CONFIRMED=1
        assertThat(res.byStatus())
                .anyMatch(sc -> sc.status() == SlipStatus.SAVED && sc.count() == 2)
                .anyMatch(sc -> sc.status() == SlipStatus.CONFIRMED && sc.count() == 1);
        // partner: P-0001=2, P-0002=1
        assertThat(res.byPartner())
                .anyMatch(pc -> "P-2026-0001".equals(pc.partnerCode()) && pc.count() == 2)
                .anyMatch(pc -> "P-2026-0002".equals(pc.partnerCode()) && pc.count() == 1);
    }

    @Test
    void cleanup_toBeforeFrom_throwsInvalidInput() {
        assertThatThrownBy(() -> service.buildCleanupReport(
                LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
