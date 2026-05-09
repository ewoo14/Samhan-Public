package com.samhanair.logis.arologis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.DriverSource;
import com.samhanair.logis.arologis.domain.MatchSource;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStatus;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.domain.VehicleTonnage;
import com.samhanair.logis.arologis.dto.ManualDispatchPreviewResponse;
import com.samhanair.logis.arologis.dto.ManualDispatchRequest;
import com.samhanair.logis.arologis.matcher.DriverMatchResult;
import com.samhanair.logis.arologis.matcher.DriverMatcher;
import com.samhanair.logis.arologis.repository.DispatchRepository;
import com.samhanair.logis.arologis.repository.DriverRepository;
import com.samhanair.logis.arologis.repository.VehicleRepository;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import com.samhanair.logis.common.exception.BusinessException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DispatchManualService 단위 테스트 — Phase 10 P1-5.
 *
 * <p>2 시나리오 — 저장 (driverCode 미지정 → MockMatcher 자동 매칭) + 미리보기 (저장 X).
 * 추가로 검증 가드 (sequence 중복 / driverCode 미존재) 회귀 케이스 포함.
 */
class DispatchManualServiceTest {

    private final DispatchRepository dispatchRepository = mock(DispatchRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleStopRepository stopRepository = mock(VehicleStopRepository.class);
    private final DriverRepository driverRepository = mock(DriverRepository.class);
    private final DriverMatcher driverMatcher = mock(DriverMatcher.class);

    /** PR-D 2-1 — RegionClassifier mock (단위 테스트는 분류 비대상). */
    private final RegionClassifier regionClassifier = mock(RegionClassifier.class);

    private final DispatchManualService service = new DispatchManualService(
            dispatchRepository, vehicleRepository, stopRepository, driverRepository, driverMatcher,
            regionClassifier);

    private static void setId(Object entity, String fieldName, UUID id) throws Exception {
        Field f = entity.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(entity, id);
    }

    private static ManualDispatchRequest sampleRequest(String driverCode) {
        return new ManualDispatchRequest(
                LocalDate.of(2026, 5, 8),
                DispatchType.NIGHT,
                driverCode,
                List.of(new ManualDispatchRequest.ManualVehicle(
                        1, VehicleTonnage.TONNAGE_1, "본사창고 → 강남",
                        List.of(
                                new ManualDispatchRequest.ManualStop(
                                        1, "현대공조", "서울 강남구 역삼동", 501L, "오전 10시 도착"),
                                new ManualDispatchRequest.ManualStop(
                                        2, "에스엠하나", "서울 송파구", 214L, null)))));
    }

    @Test
    @DisplayName("manualCreate — driverCode 미지정 → Mock matcher 자동 매칭 후 ASSIGNED")
    void manualCreate_autoMatch_when_driverCode_null() throws Exception {
        ManualDispatchRequest req = sampleRequest(null);

        UUID dispatchId = UUID.randomUUID();
        Dispatch dispatch = Dispatch.of(req.dispatchDate(), req.dispatchType(), "(수동입력)");
        setId(dispatch, "id", dispatchId);
        when(dispatchRepository.save(any(Dispatch.class))).thenReturn(dispatch);

        Vehicle vehicle = Vehicle.of(dispatchId, 1, VehicleTonnage.TONNAGE_1, "본사창고 → 강남");
        UUID vehicleId = UUID.randomUUID();
        setId(vehicle, "id", vehicleId);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);
        when(stopRepository.save(any(VehicleStop.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stopRepository.findAllByVehicleIdOrderBySequenceAsc(vehicleId)).thenReturn(List.of());

        Driver mockDriver = Driver.of("MOCK-001", "010-0000-0000", "1톤",
                DriverSource.INTERNAL, false, null);
        UUID driverId = UUID.randomUUID();
        setId(mockDriver, "id", driverId);
        when(driverMatcher.match(any(), any()))
                .thenReturn(DriverMatchResult.of(mockDriver, MatchSource.INTERNAL_APP, "MOCK-aaaa"));

        UUID returned = service.manualCreate(req);

        assertThat(returned).isEqualTo(dispatchId);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
        assertThat(vehicle.getAssignedDriverId()).isEqualTo(driverId);
        assertThat(vehicle.getMatchSource()).isEqualTo(MatchSource.INTERNAL_APP);
        verify(driverRepository, never()).findByDriverCode(any());
    }

    @Test
    @DisplayName("manualCreate — driverCode 지정 → MANUAL 배정 + matcher 호출 X")
    void manualCreate_manualAssign_when_driverCode_present() throws Exception {
        ManualDispatchRequest req = sampleRequest("D-100");

        Driver driver = Driver.of("D-100", "010-1111-2222", "1톤",
                DriverSource.MANUAL, false, null);
        UUID driverId = UUID.randomUUID();
        setId(driver, "id", driverId);
        when(driverRepository.findByDriverCode("D-100")).thenReturn(Optional.of(driver));

        UUID dispatchId = UUID.randomUUID();
        Dispatch dispatch = Dispatch.of(req.dispatchDate(), req.dispatchType(), "(수동입력)");
        setId(dispatch, "id", dispatchId);
        when(dispatchRepository.save(any(Dispatch.class))).thenReturn(dispatch);

        Vehicle vehicle = Vehicle.of(dispatchId, 1, VehicleTonnage.TONNAGE_1, "본사창고 → 강남");
        UUID vehicleId = UUID.randomUUID();
        setId(vehicle, "id", vehicleId);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);
        when(stopRepository.save(any(VehicleStop.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID returned = service.manualCreate(req);

        assertThat(returned).isEqualTo(dispatchId);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ASSIGNED);
        assertThat(vehicle.getMatchSource()).isEqualTo(MatchSource.MANUAL);
        assertThat(vehicle.getAssignedDriverId()).isEqualTo(driverId);
        verify(driverMatcher, never()).match(any(), any());
    }

    @Test
    @DisplayName("manualPreview — 저장 X + 합계 echo")
    void manualPreview_returns_summary_without_persisting() {
        ManualDispatchRequest req = sampleRequest(null);

        ManualDispatchPreviewResponse preview = service.manualPreview(req);

        assertThat(preview.dispatchDate()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(preview.dispatchType()).isEqualTo(DispatchType.NIGHT);
        assertThat(preview.totalVehicles()).isEqualTo(1);
        assertThat(preview.totalStops()).isEqualTo(2);
        assertThat(preview.driverCodeApplied()).isNull();
        assertThat(preview.vehicles()).hasSize(1);
        assertThat(preview.vehicles().get(0).stops()).hasSize(2);
        verify(dispatchRepository, never()).save(any());
        verify(vehicleRepository, never()).save(any());
        verify(stopRepository, never()).save(any());
    }

    @Test
    @DisplayName("manualPreview — driverCode 미존재 시 NOT_FOUND")
    void manualPreview_throws_when_driverCode_missing() {
        ManualDispatchRequest req = sampleRequest("UNKNOWN");
        when(driverRepository.findByDriverCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.manualPreview(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("driver 미존재");
    }

    @Test
    @DisplayName("manualCreate — vehicle sequence 중복 시 INVALID_INPUT")
    void manualCreate_throws_on_duplicate_vehicle_sequence() {
        ManualDispatchRequest req = new ManualDispatchRequest(
                LocalDate.of(2026, 5, 8), DispatchType.NIGHT, null,
                List.of(
                        new ManualDispatchRequest.ManualVehicle(
                                1, VehicleTonnage.TONNAGE_1, null,
                                List.of(new ManualDispatchRequest.ManualStop(
                                        1, null, "주소1", null, null))),
                        new ManualDispatchRequest.ManualVehicle(
                                1, VehicleTonnage.TONNAGE_1, null,
                                List.of(new ManualDispatchRequest.ManualStop(
                                        1, null, "주소2", null, null)))));

        assertThatThrownBy(() -> service.manualCreate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vehicle sequence 중복");
    }
}
