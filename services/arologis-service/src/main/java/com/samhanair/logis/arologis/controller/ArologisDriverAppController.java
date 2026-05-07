package com.samhanair.logis.arologis.controller;

import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.DriverLocation;
import com.samhanair.logis.arologis.domain.Signature;
import com.samhanair.logis.arologis.domain.SignatureSource;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.repository.DriverLocationRepository;
import com.samhanair.logis.arologis.repository.DriverRepository;
import com.samhanair.logis.arologis.repository.SignatureRepository;
import com.samhanair.logis.arologis.repository.VehicleRepository;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driver-app endpoint — Phase 10 W10-1 arologis-service.
 *
 * <p>본 PR (W10-1) 은 endpoint 정의만 — 실제 RN Expo 어플 통합은 W10-3 시점.
 * 인증 = X-User-* 헤더 + ROLE_DRIVER (Gateway 가 주입).
 *
 * <p>UUID 비공개 가드 — 응답에 driverCode + 정차 식별자만 노출.
 */
@Slf4j
@RestController
@RequestMapping("/driver-app/arologis")
@RequiredArgsConstructor
public class ArologisDriverAppController {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleStopRepository stopRepository;
    private final SignatureRepository signatureRepository;
    private final DriverLocationRepository locationRepository;

    /**
     * 본인에게 배정된 dispatch 목록 — X-User-Id 헤더 기반.
     *
     * <p>본 PR (W10-1) 은 단순화 — 인증된 driver 의 vehicle 목록만 sequence + tonnage + status 응답.
     */
    @Operation(summary = "오늘의 배정된 dispatch 목록 조회 (Driver-app)")
    @GetMapping("/dispatches/today")
    @PreAuthorize("hasAnyRole('DRIVER','MASTER','MANAGER')")
    public ApiResponse<List<Map<String, Object>>> today(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "X-User-Id 헤더 필수");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "X-User-Id 형식 무효: " + userIdHeader);
        }
        // INTERNAL driver 인 본인 driver 찾기 (appUserId 일치)
        Driver self = driverRepository.findAll().stream()
                .filter(d -> userId.equals(d.getAppUserId()))
                .findFirst()
                .orElse(null);
        if (self == null) {
            return ApiResponse.ok(List.of());
        }
        List<Vehicle> vehicles = vehicleRepository.findAllByAssignedDriverIdOrderByCreatedAtDesc(self.getId());
        List<Map<String, Object>> response = vehicles.stream()
                .map(v -> Map.of(
                        "vehicleSequence", (Object) v.getSequence(),
                        "tonnage", v.getTonnage().name(),
                        "status", v.getStatus().name()))
                .toList();
        return ApiResponse.ok(response);
    }

    /**
     * GPS 위치 보고. body = {latitude, longitude, capturedAt} (capturedAt 은 ISO8601).
     *
     * <p>본 PR (W10-1) 은 INTERNAL driver (본 어플 사용자) 만 — appUserId = X-User-Id 매칭 필수.
     */
    @Operation(summary = "GPS 위치 보고 (Driver-app)")
    @PostMapping("/locations")
    @PreAuthorize("hasAnyRole('DRIVER','MASTER','MANAGER')")
    public ApiResponse<Map<String, Object>> reportLocation(
            HttpServletRequest request, @RequestBody Map<String, String> body) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "X-User-Id 헤더 필수");
        }
        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "X-User-Id 형식 무효: " + userIdHeader);
        }
        Driver self = driverRepository.findAll().stream()
                .filter(d -> userId.equals(d.getAppUserId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "본 어플 driver 미등록"));
        if (body == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "body 필수");
        }
        BigDecimal lat = new BigDecimal(body.getOrDefault("latitude", "0"));
        BigDecimal lng = new BigDecimal(body.getOrDefault("longitude", "0"));
        LocalDateTime now = LocalDateTime.now();
        DriverLocation saved = locationRepository.save(
                DriverLocation.of(self.getId(), lat, lng, now, "DRIVER_APP"));
        return ApiResponse.ok(Map.of("locationId", saved.getId().toString(), "capturedAt", now.toString()));
    }

    /**
     * 전자서명 등록. body = {imageRef, latitude, longitude}.
     */
    @Operation(summary = "전자서명 등록 (Driver-app)")
    @PostMapping("/dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/sign")
    @PreAuthorize("hasAnyRole('DRIVER','MASTER','MANAGER')")
    public ApiResponse<Map<String, String>> sign(
            @PathVariable UUID id, @PathVariable Integer seq, @PathVariable Integer stopSeq,
            @RequestBody Map<String, String> body) {
        Vehicle vehicle = vehicleRepository.findFirstByDispatchIdAndSequence(id, seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "vehicle 미존재 — dispatchId=" + id + " seq=" + seq));
        var stop = stopRepository.findFirstByVehicleIdAndSequence(vehicle.getId(), stopSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "stop 미존재 — vehicleId=" + vehicle.getId() + " seq=" + stopSeq));
        String imageRef = body == null ? null : body.get("imageRef");
        BigDecimal lat = body == null || body.get("latitude") == null
                ? null : new BigDecimal(body.get("latitude"));
        BigDecimal lng = body == null || body.get("longitude") == null
                ? null : new BigDecimal(body.get("longitude"));
        Signature saved = signatureRepository.save(
                Signature.of(stop.getId(), SignatureSource.APP, imageRef, LocalDateTime.now(), lat, lng));
        return ApiResponse.ok(Map.of("signatureId", saved.getId().toString()));
    }
}
