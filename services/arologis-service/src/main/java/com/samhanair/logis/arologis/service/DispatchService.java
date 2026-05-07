package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.client.NotificationClient;
import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.MatchSource;
import com.samhanair.logis.arologis.domain.StopStatus;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStatus;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.matcher.DriverMatchResult;
import com.samhanair.logis.arologis.matcher.DriverMatcher;
import com.samhanair.logis.arologis.parser.ParsedDispatch;
import com.samhanair.logis.arologis.repository.DispatchRepository;
import com.samhanair.logis.arologis.repository.DriverRepository;
import com.samhanair.logis.arologis.repository.VehicleRepository;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatch service — Phase 10 W10-1.
 *
 * <p>저장 / 조회 / 자동 매칭 / 수동 배정 / 정차 상태 갱신 / Soft Delete.
 *
 * <p>본 service 의 자동 매칭 메서드는 활성 {@link DriverMatcher} (Mock or Insung) 호출 + 매칭
 * 결과 반영 + (옵션) NotificationClient.send 호출.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final DispatchRepository dispatchRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleStopRepository stopRepository;
    private final DriverRepository driverRepository;
    private final DriverMatcher driverMatcher;
    private final NotificationClient notificationClient;

    /**
     * Parsed dispatch → 영속화. dispatch + vehicles + stops 일괄 저장.
     *
     * @param parsed 카톡 파싱 결과
     * @param rawKakaoText 원본 메시지 (audit)
     * @return 저장된 dispatchId
     */
    @Transactional
    public UUID create(ParsedDispatch parsed, String rawKakaoText) {
        if (parsed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파싱 결과 필수");
        }
        Dispatch dispatch = dispatchRepository.save(
                Dispatch.of(parsed.dispatchDate(), parsed.dispatchType(), rawKakaoText));

        for (ParsedDispatch.ParsedVehicle pv : parsed.vehicles()) {
            Vehicle vehicle = vehicleRepository.save(
                    Vehicle.of(dispatch.getId(), pv.sequence(), pv.tonnage(), pv.label()));
            for (ParsedDispatch.ParsedStop ps : pv.stops()) {
                StopStatus initial = ps.unparsed() ? StopStatus.UNPARSED : StopStatus.PENDING;
                stopRepository.save(VehicleStop.of(
                        vehicle.getId(),
                        ps.sequence(),
                        ps.rawText(),
                        ps.parsedAddress(),
                        ps.parsedPartnerName(),
                        ps.parsedPartnerCode(),
                        ps.notes(),
                        initial));
            }
        }
        log.info("Dispatch 저장 완료 — dispatchId={}, date={}, type={}, vehicles={}",
                dispatch.getId(), parsed.dispatchDate(), parsed.dispatchType(), parsed.vehicles().size());
        return dispatch.getId();
    }

    /** 단건 조회 (vehicles + stops 포함). */
    @Transactional(readOnly = true)
    public DispatchAggregate findById(UUID dispatchId) {
        Dispatch dispatch = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "dispatch 미존재: " + dispatchId));
        List<Vehicle> vehicles = vehicleRepository.findAllByDispatchIdOrderBySequenceAsc(dispatch.getId());
        List<VehicleStop> stops = new ArrayList<>();
        for (Vehicle v : vehicles) {
            stops.addAll(stopRepository.findAllByVehicleIdOrderBySequenceAsc(v.getId()));
        }
        return new DispatchAggregate(dispatch, vehicles, stops);
    }

    /** 날짜 + 유형 필터 조회. */
    @Transactional(readOnly = true)
    public List<Dispatch> findByDateAndType(LocalDate date, DispatchType type) {
        if (date == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "date 필수");
        }
        if (type == null) {
            return dispatchRepository.findAllByDispatchDateOrderByCreatedAtDesc(date);
        }
        return dispatchRepository.findAllByDispatchDateAndDispatchTypeOrderByCreatedAtDesc(date, type);
    }

    /**
     * 모든 vehicle 자동 매칭 — 활성 DriverMatcher 호출 + 매칭 결과 반영 + 알림.
     */
    @Transactional
    public AutoMatchResult autoMatch(UUID dispatchId) {
        DispatchAggregate agg = findById(dispatchId);
        int total = agg.vehicles().size();
        int matched = 0;
        for (Vehicle vehicle : agg.vehicles()) {
            if (vehicle.getStatus() != VehicleStatus.PENDING) {
                continue;
            }
            vehicle.markMatching();
            List<VehicleStop> vehicleStops = agg.stops().stream()
                    .filter(s -> s.getVehicleId().equals(vehicle.getId()))
                    .toList();
            try {
                DriverMatchResult result = driverMatcher.match(vehicle, vehicleStops);
                if (result.driver().isPresent()) {
                    Driver driver = result.driver().get();
                    vehicle.assignDriver(driver.getId(), result.source(), result.externalRefId());
                    matched++;
                    notificationClient.send(driver.getAppUserId(), "PUSH",
                            "신규 배차 매칭",
                            "차량 #" + vehicle.getSequence() + " (" + vehicle.getTonnage() + ") 배정");
                } else {
                    log.info("자동 매칭 실패 — vehicleSeq={}, source={}",
                            vehicle.getSequence(), result.source());
                }
            } catch (UnsupportedOperationException ex) {
                log.warn("Matcher placeholder — vehicleSeq={}, msg={}", vehicle.getSequence(), ex.getMessage());
            } catch (Exception ex) {
                log.warn("Matcher 호출 실패 (fail-soft) — vehicleSeq={}, msg={}",
                        vehicle.getSequence(), ex.getMessage());
            }
        }
        return new AutoMatchResult(total, matched);
    }

    /**
     * 수동 기사 배정 — driverCode 로 lookup → vehicle.assignDriver.
     */
    @Transactional
    public void assignDriverManual(UUID dispatchId, Integer vehicleSeq, String driverCode) {
        if (driverCode == null || driverCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "driverCode 필수");
        }
        Vehicle vehicle = vehicleRepository.findFirstByDispatchIdAndSequence(dispatchId, vehicleSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "vehicle 미존재 — dispatchId=" + dispatchId + " seq=" + vehicleSeq));
        Driver driver = driverRepository.findByDriverCode(driverCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "driver 미존재: " + driverCode));
        vehicle.assignDriver(driver.getId(), MatchSource.MANUAL, null);
        log.info("수동 배정 완료 — dispatchId={} vehicleSeq={} driverCode={}",
                dispatchId, vehicleSeq, driverCode);
    }

    /** 정차 상태 갱신. */
    @Transactional
    public void updateStopStatus(UUID dispatchId, Integer vehicleSeq, Integer stopSeq, StopStatus status) {
        Vehicle vehicle = vehicleRepository.findFirstByDispatchIdAndSequence(dispatchId, vehicleSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "vehicle 미존재 — dispatchId=" + dispatchId + " seq=" + vehicleSeq));
        VehicleStop stop = stopRepository.findFirstByVehicleIdAndSequence(vehicle.getId(), stopSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "stop 미존재 — vehicleId=" + vehicle.getId() + " seq=" + stopSeq));
        stop.updateStatus(status, LocalDateTime.now());
    }

    /** Soft Delete (BaseEntity.markDeleted). */
    @Transactional
    public void softDelete(UUID dispatchId, String userId) {
        Dispatch dispatch = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "dispatch 미존재: " + dispatchId));
        dispatch.markDeleted(userId);
    }

    /** 단건 조회 응답 — dispatch + vehicles + stops aggregate. */
    public record DispatchAggregate(Dispatch dispatch, List<Vehicle> vehicles, List<VehicleStop> stops) {}

    /** 자동 매칭 결과 — 시도 차량 수 + 성공 차량 수. */
    public record AutoMatchResult(int totalVehicles, int matched) {}
}
