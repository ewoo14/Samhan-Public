package com.samhanair.logis.arologis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.client.SlipServiceClient;
import com.samhanair.logis.arologis.client.SlipServiceClient.OutboundSlipSummary;
import com.samhanair.logis.arologis.domain.StopStatus;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.dto.UnassignedSlipResponse;
import com.samhanair.logis.arologis.dto.UnassignedSlipResponse.Entry;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import com.samhanair.logis.common.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UnassignedService 단위 테스트 — Phase 10 PR-E1 BE-A3.
 *
 * <p>left join 미할당 4 case:
 *
 * <ol>
 *   <li>case 1 — 정상 케이스 일부만 매칭 → 미매칭 슬립 반환</li>
 *   <li>case 2 — 모든 슬립 매칭 → 빈 entries (totalOutbound 만 보존)</li>
 *   <li>case 3 — slip-service 빈 응답 → totalOutbound=0 / unassignedCount=0</li>
 *   <li>case 4 — date null → BusinessException</li>
 * </ol>
 */
class UnassignedServiceTest {

    private SlipServiceClient slipServiceClient;
    private VehicleStopRepository vehicleStopRepository;
    private UnassignedService service;

    @BeforeEach
    void setUp() {
        slipServiceClient = mock(SlipServiceClient.class);
        vehicleStopRepository = mock(VehicleStopRepository.class);
        service = new UnassignedService(slipServiceClient, vehicleStopRepository);
    }

    @Test
    @DisplayName("case 1 — 정상 케이스 일부만 매칭 → 미매칭 슬립 반환")
    void findUnassigned_partialMatch_returnsUnmatched() {
        when(slipServiceClient.getOutboundSlips(any(), any())).thenReturn(List.of(
                new OutboundSlipSummary("id-1", "2026/05/10-001", "P-2026-0001",
                        "이미배차공조", "서울 강남구 역삼동"),
                new OutboundSlipSummary("id-2", "2026/05/10-002", "P-2026-0002",
                        "미배차공조A", "서울 송파구 잠실동"),
                new OutboundSlipSummary("id-3", "2026/05/10-003", "P-2026-0003",
                        "미배차공조B", "수원시 영통구")
        ));
        // P-2026-0001 만 vehicle_stops 매칭 → 나머지 2건 미배차
        VehicleStop matched = VehicleStop.of(
                UUID.randomUUID(), 1, "raw",
                "서울 강남구 역삼동", "이미배차공조", null, null, StopStatus.PENDING,
                "서울특별시", "P-2026-0001");
        when(vehicleStopRepository.findAllByParsedPartnerCodeIn(any())).thenReturn(List.of(matched));

        UnassignedSlipResponse result = service.findUnassigned(LocalDate.of(2026, 5, 10));

        assertThat(result.totalOutbound()).isEqualTo(3);
        assertThat(result.unassignedCount()).isEqualTo(2);
        assertThat(result.entries()).extracting(Entry::partnerCode)
                .containsExactlyInAnyOrder("P-2026-0002", "P-2026-0003");
        assertThat(result.date()).isEqualTo("2026-05-10");
    }

    @Test
    @DisplayName("case 2 — 모든 슬립 매칭 → 빈 entries (totalOutbound 보존)")
    void findUnassigned_allMatched_returnsEmptyEntries() {
        when(slipServiceClient.getOutboundSlips(any(), any())).thenReturn(List.of(
                new OutboundSlipSummary("id-1", "2026/05/10-001", "P-2026-0001",
                        "공조A", "서울 강남구"),
                new OutboundSlipSummary("id-2", "2026/05/10-002", "P-2026-0002",
                        "공조B", "서울 송파구")
        ));
        VehicleStop m1 = VehicleStop.of(UUID.randomUUID(), 1, "raw1",
                "서울 강남구", "공조A", null, null, StopStatus.PENDING, "서울특별시", "P-2026-0001");
        VehicleStop m2 = VehicleStop.of(UUID.randomUUID(), 2, "raw2",
                "서울 송파구", "공조B", null, null, StopStatus.PENDING, "서울특별시", "P-2026-0002");
        when(vehicleStopRepository.findAllByParsedPartnerCodeIn(any())).thenReturn(List.of(m1, m2));

        UnassignedSlipResponse result = service.findUnassigned(LocalDate.of(2026, 5, 10));

        assertThat(result.totalOutbound()).isEqualTo(2);
        assertThat(result.unassignedCount()).isEqualTo(0);
        assertThat(result.entries()).isEmpty();
    }

    @Test
    @DisplayName("case 3 — slip-service 빈 응답 → totalOutbound=0")
    void findUnassigned_emptySlips_returnsAllZero() {
        when(slipServiceClient.getOutboundSlips(any(), any())).thenReturn(List.of());

        UnassignedSlipResponse result = service.findUnassigned(LocalDate.of(2026, 5, 10));

        assertThat(result.totalOutbound()).isEqualTo(0);
        assertThat(result.unassignedCount()).isEqualTo(0);
        assertThat(result.entries()).isEmpty();
    }

    @Test
    @DisplayName("case 4 — date null → BusinessException")
    void findUnassigned_nullDate_throwsBusinessException() {
        assertThatThrownBy(() -> service.findUnassigned(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("date");
    }
}
