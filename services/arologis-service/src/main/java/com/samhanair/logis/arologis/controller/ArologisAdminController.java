package com.samhanair.logis.arologis.controller;

import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.StopStatus;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.dto.DispatchDetailResponse;
import com.samhanair.logis.arologis.dto.DispatchResponse;
import com.samhanair.logis.arologis.dto.DriverResponse;
import com.samhanair.logis.arologis.dto.ParsedDispatchResponse;
import com.samhanair.logis.arologis.parser.KakaoDispatchParser;
import com.samhanair.logis.arologis.parser.ParsedDispatch;
import com.samhanair.logis.arologis.repository.DriverRepository;
import com.samhanair.logis.arologis.service.DispatchService;
import com.samhanair.logis.arologis.service.DriverService;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint — Phase 10 W10-1 arologis-service.
 *
 * <p>인증 = X-User-* 헤더 + {@code @PreAuthorize("hasAnyRole('MASTER','MANAGER')")}.
 *
 * <p>UUID 비공개 가드 — driverCode / partnerCode / vehicle sequence / stop sequence 응답에만 사용.
 * dispatchId 만 admin 화면 routing 용 노출.
 */
@Slf4j
@RestController
@RequestMapping("/admin/arologis")
@RequiredArgsConstructor
public class ArologisAdminController {

    private final KakaoDispatchParser parser;
    private final DispatchService dispatchService;
    private final DriverService driverService;
    private final DriverRepository driverRepository;

    /**
     * 카톡 메시지 파싱 미리보기 — 저장 X.
     *
     * <p>request body = {@code {"kakaoText": "8일착 야상입니다\\n1. 상일+초월\\n..."}}
     */
    @Operation(summary = "카톡 배차 메시지 파싱 미리보기 (Admin)")
    @PostMapping("/dispatches/parse-kakao")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<ParsedDispatchResponse> parseKakao(@RequestBody Map<String, String> body) {
        String kakaoText = body == null ? null : body.get("kakaoText");
        if (kakaoText == null || kakaoText.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "kakaoText 필수");
        }
        ParsedDispatch parsed = parser.parse(kakaoText, LocalDate.now());
        return ApiResponse.ok(ParsedDispatchResponse.from(parsed));
    }

    /**
     * Dispatch 저장 — 수동 보정 후 저장. body = {kakaoText} 또는 parser 결과 자체.
     * 본 endpoint 는 단순화 — kakaoText 재파싱 후 저장.
     */
    @Operation(summary = "Dispatch 저장 (Admin)")
    @PostMapping("/dispatches")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<Map<String, String>> create(@RequestBody Map<String, String> body) {
        String kakaoText = body == null ? null : body.get("kakaoText");
        if (kakaoText == null || kakaoText.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "kakaoText 필수");
        }
        ParsedDispatch parsed = parser.parse(kakaoText, LocalDate.now());
        UUID id = dispatchService.create(parsed, kakaoText);
        return ApiResponse.ok(Map.of("dispatchId", id.toString()));
    }

    /**
     * Dispatch 목록 조회 — 날짜 + 유형 + 상태 필터.
     */
    @Operation(summary = "Dispatch 목록 조회 (Admin)")
    @GetMapping("/dispatches")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<List<DispatchResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) DispatchType type) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        List<Dispatch> result = dispatchService.findByDateAndType(effectiveDate, type);
        return ApiResponse.ok(result.stream().map(DispatchResponse::from).toList());
    }

    /**
     * Dispatch 단건 조회 — vehicles + stops + 매칭된 driverCode 포함.
     */
    @Operation(summary = "Dispatch 상세 조회 (Admin)")
    @GetMapping("/dispatches/{id}")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<DispatchDetailResponse> findById(@PathVariable UUID id) {
        DispatchService.DispatchAggregate agg = dispatchService.findById(id);
        // QA-1 채택 fix — N round-trip → batch findAllById (N+1 → 1 query).
        List<UUID> driverIds = agg.vehicles().stream()
                .map(Vehicle::getAssignedDriverId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, String> driverIdToCode = driverIds.isEmpty()
                ? new HashMap<>()
                : driverRepository.findAllById(driverIds).stream()
                        .collect(Collectors.toMap(d -> d.getId().toString(), Driver::getDriverCode));
        return ApiResponse.ok(DispatchDetailResponse.from(
                agg.dispatch(), agg.vehicles(), agg.stops(), driverIdToCode));
    }

    /** 자동 매칭 — 모든 vehicle 에 대해 활성 DriverMatcher 호출. */
    @Operation(summary = "Dispatch 자동 매칭 (Admin)")
    @PostMapping("/dispatches/{id}/auto-match")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<DispatchService.AutoMatchResult> autoMatch(@PathVariable UUID id) {
        return ApiResponse.ok(dispatchService.autoMatch(id));
    }

    /**
     * 특정 차량 외부 매칭 trigger — W10-2 시점 활성. 본 PR 은 자동 매칭의 단건 변형.
     */
    @Operation(summary = "특정 차량 외부 매칭 trigger (Admin)")
    @PostMapping("/dispatches/{id}/vehicles/{seq}/match-external")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<DispatchService.AutoMatchResult> matchExternal(
            @PathVariable UUID id, @PathVariable Integer seq) {
        // 단순화 — 전체 auto-match 호출 후 결과 반환 (W10-2 시점에 단건 매칭으로 분리)
        log.info("matchExternal — dispatchId={} vehicleSeq={} (W10-2 시점 단건 매칭 분리 예정)", id, seq);
        return ApiResponse.ok(dispatchService.autoMatch(id));
    }

    /** 수동 기사 배정. */
    @Operation(summary = "수동 기사 배정 (Admin)")
    @PostMapping("/dispatches/{id}/vehicles/{seq}/assign-driver")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<Map<String, String>> assignDriver(
            @PathVariable UUID id, @PathVariable Integer seq,
            @RequestBody Map<String, String> body) {
        String driverCode = body == null ? null : body.get("driverCode");
        dispatchService.assignDriverManual(id, seq, driverCode);
        return ApiResponse.ok(Map.of("dispatchId", id.toString(), "driverCode", driverCode));
    }

    /** 정차 상태 갱신. */
    @Operation(summary = "정차 상태 갱신 (Admin)")
    @PutMapping("/dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/status")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<Map<String, String>> updateStopStatus(
            @PathVariable UUID id, @PathVariable Integer seq, @PathVariable Integer stopSeq,
            @RequestBody Map<String, String> body) {
        String statusRaw = body == null ? null : body.get("status");
        if (statusRaw == null || statusRaw.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "status 필수");
        }
        StopStatus status;
        try {
            status = StopStatus.valueOf(statusRaw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 status: " + statusRaw);
        }
        dispatchService.updateStopStatus(id, seq, stopSeq, status);
        return ApiResponse.ok(Map.of("status", status.name()));
    }

    /** Driver 목록 조회 — source / phoneNumber / appInstalled 필터. */
    @Operation(summary = "기사 목록 조회 (Admin)")
    @GetMapping("/drivers")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<List<DriverResponse>> listDrivers(
            @RequestParam(required = false) com.samhanair.logis.arologis.domain.DriverSource source,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Boolean appInstalled) {
        List<Driver> drivers = driverService.findDrivers(source, phoneNumber, appInstalled);
        return ApiResponse.ok(drivers.stream().map(DriverResponse::from).toList());
    }

    /** Soft Delete — admin 전용. */
    @Operation(summary = "Dispatch Soft Delete (Admin)")
    @PutMapping("/dispatches/{id}/delete")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER')")
    public ApiResponse<Map<String, String>> softDelete(@PathVariable UUID id, HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        dispatchService.softDelete(id, userId == null ? "system" : userId);
        return ApiResponse.ok(Map.of("dispatchId", id.toString(), "deleted", "true"));
    }
}
