package com.samhanair.logis.arologis.dto;

import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.VehicleTonnage;
import com.samhanair.logis.arologis.parser.ParsedDispatch;
import java.time.LocalDate;
import java.util.List;

/**
 * KakaoDispatchParser 미리보기 응답 DTO — 저장 전 파싱 결과 검증용.
 *
 * <p>UUID 비공개 — 본 응답은 저장 전 미리보기이므로 dispatchId 자체가 없음.
 */
public record ParsedDispatchResponse(
        LocalDate dispatchDate,
        DispatchType dispatchType,
        int totalLines,
        int parsedLines,
        double accuracy,
        List<ParsedVehicleDto> vehicles
) {

    public static ParsedDispatchResponse from(ParsedDispatch parsed) {
        List<ParsedVehicleDto> vehicleDtos = parsed.vehicles().stream()
                .map(ParsedVehicleDto::from)
                .toList();
        return new ParsedDispatchResponse(
                parsed.dispatchDate(),
                parsed.dispatchType(),
                parsed.totalLines(),
                parsed.parsedLines(),
                parsed.accuracy(),
                vehicleDtos);
    }

    public record ParsedVehicleDto(
            int sequence,
            VehicleTonnage tonnage,
            String label,
            List<ParsedStopDto> stops
    ) {
        static ParsedVehicleDto from(ParsedDispatch.ParsedVehicle pv) {
            List<ParsedStopDto> stopDtos = pv.stops().stream().map(ParsedStopDto::from).toList();
            return new ParsedVehicleDto(pv.sequence(), pv.tonnage(), pv.label(), stopDtos);
        }
    }

    public record ParsedStopDto(
            int sequence,
            String rawText,
            String parsedAddress,
            String parsedPartnerName,
            Long parsedPartnerCode,
            String notes,
            boolean unparsed
    ) {
        static ParsedStopDto from(ParsedDispatch.ParsedStop ps) {
            return new ParsedStopDto(
                    ps.sequence(),
                    ps.rawText(),
                    ps.parsedAddress(),
                    ps.parsedPartnerName(),
                    ps.parsedPartnerCode(),
                    ps.notes(),
                    ps.unparsed());
        }
    }
}
