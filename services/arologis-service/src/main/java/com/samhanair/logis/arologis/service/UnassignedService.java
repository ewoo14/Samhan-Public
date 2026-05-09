package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.client.SlipServiceClient;
import com.samhanair.logis.arologis.client.SlipServiceClient.OutboundSlipSummary;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.dto.UnassignedSlipResponse;
import com.samhanair.logis.arologis.dto.UnassignedSlipResponse.Entry;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 미배차 출고전표 서비스 — Phase 10 PR-E1 BE-A3 (legacy GAS 7번 이식).
 *
 * <p>출고전표 중 dispatch 미할당 (slip_no 가 어떤 활성 VehicleStop 의 parsed_partner_code 와도
 * 매칭 안 됨) 슬립 목록 조회.
 *
 * <h2>처리 흐름 (left join 시뮬레이션)</h2>
 * <ol>
 *   <li>{@link SlipServiceClient#getOutboundSlips(LocalDate, LocalDate)} 호출 — 일자 OUTBOUND 슬립 조회</li>
 *   <li>{@link VehicleStopRepository#findAllByParsedPartnerCodeIn(List)} 로 본 슬립들의 partnerCode 와
 *       매칭되는 vehicle_stops 일괄 조회 (parsed_partner_code IN (...))</li>
 *   <li>각 슬립에 대해 매칭 vehicle_stop 0건 이면 unassigned 리스트에 추가</li>
 * </ol>
 *
 * <p>service-per-DB 패턴 — arologis 의 vehicle_stops 와 slip-service 의 slips 는 별도 schema.
 * 따라서 SQL 직접 LEFT JOIN 불가 — 본 서비스가 application-level 매칭으로 left join 시뮬레이션.
 *
 * <p>graceful empty — slip-service skeleton-mode 시 totalOutbound=0, unassignedCount=0, entries=[].
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnassignedService {

    private final SlipServiceClient slipServiceClient;
    private final VehicleStopRepository vehicleStopRepository;

    /**
     * 미배차 출고전표 조회.
     *
     * @param date 조회 일자 (필수, from=to=date 로 일자 단위 조회)
     * @return 미배차 슬립 응답
     * @throws BusinessException(INVALID_INPUT) date null
     */
    @Transactional(readOnly = true)
    public UnassignedSlipResponse findUnassigned(LocalDate date) {
        if (date == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "date 는 필수입니다");
        }
        List<OutboundSlipSummary> slips = slipServiceClient.getOutboundSlips(date, date);
        log.info("UnassignedService — date={}, slipsFetched={}", date, slips.size());

        Set<String> assignedPartnerCodes = collectAssignedPartnerCodes(slips);
        List<Entry> entries = new ArrayList<>();
        for (OutboundSlipSummary slip : slips) {
            // partnerCode null 이거나 vehicle_stops 매칭 0 → 미배차로 분류
            if (slip.partnerCode() == null || !assignedPartnerCodes.contains(slip.partnerCode())) {
                entries.add(new Entry(
                        slip.slipNo(),
                        slip.partnerCode(),
                        slip.partnerName(),
                        slip.address()));
            }
        }
        return new UnassignedSlipResponse(
                date.toString(),
                slips.size(),
                entries.size(),
                entries);
    }

    private Set<String> collectAssignedPartnerCodes(List<OutboundSlipSummary> slips) {
        List<String> codes = slips.stream()
                .map(OutboundSlipSummary::partnerCode)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Set.of();
        }
        return vehicleStopRepository.findAllByParsedPartnerCodeIn(codes).stream()
                .map(VehicleStop::getParsedPartnerCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
    }
}
