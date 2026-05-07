package com.samhanair.logis.arologis.dto;

import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.MatchSource;
import com.samhanair.logis.arologis.domain.StopStatus;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStatus;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.domain.VehicleTonnage;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dispatch 상세 응답 — vehicles + stops 포함.
 *
 * <p>UUID 비공개 가드 — assignedDriverId UUID 는 응답에서 제외 (driverCode 만 별도 lookup 후 첨부).
 * 본 PR (W10-1) 은 dispatchId 노출 (admin 화면 routing) + driverCode 매핑 (옵션).
 */
public record DispatchDetailResponse(
        String dispatchId,
        LocalDate dispatchDate,
        DispatchType dispatchType,
        List<VehicleDetail> vehicles
) {

    public static DispatchDetailResponse from(Dispatch dispatch, List<Vehicle> vehicles,
                                              List<VehicleStop> stops,
                                              Map<String, String> driverIdToCode) {
        List<VehicleDetail> vehicleDetails = vehicles.stream()
                .map(v -> VehicleDetail.from(v, stops, driverIdToCode))
                .toList();
        return new DispatchDetailResponse(
                dispatch.getId() == null ? null : dispatch.getId().toString(),
                dispatch.getDispatchDate(),
                dispatch.getDispatchType(),
                vehicleDetails);
    }

    public record VehicleDetail(
            int sequence,
            VehicleTonnage tonnage,
            String label,
            String assignedDriverCode,
            MatchSource matchSource,
            String externalRefId,
            VehicleStatus status,
            List<StopDetail> stops
    ) {
        static VehicleDetail from(Vehicle v, List<VehicleStop> allStops, Map<String, String> driverIdToCode) {
            List<StopDetail> stopDetails = allStops.stream()
                    .filter(s -> s.getVehicleId().equals(v.getId()))
                    .map(StopDetail::from)
                    .toList();
            String driverCode = null;
            if (v.getAssignedDriverId() != null && driverIdToCode != null) {
                driverCode = driverIdToCode.get(v.getAssignedDriverId().toString());
            }
            return new VehicleDetail(
                    v.getSequence(),
                    v.getTonnage(),
                    v.getLabel(),
                    driverCode,
                    v.getMatchSource(),
                    v.getExternalRefId(),
                    v.getStatus(),
                    stopDetails);
        }
    }

    public record StopDetail(
            int sequence,
            String rawText,
            String parsedAddress,
            String parsedPartnerName,
            Long parsedPartnerCode,
            String notes,
            StopStatus status
    ) {
        static StopDetail from(VehicleStop s) {
            return new StopDetail(
                    s.getSequence(),
                    s.getRawText(),
                    s.getParsedAddress(),
                    s.getParsedPartnerName(),
                    s.getParsedPartnerCode(),
                    s.getNotes(),
                    s.getStatus());
        }
    }
}
