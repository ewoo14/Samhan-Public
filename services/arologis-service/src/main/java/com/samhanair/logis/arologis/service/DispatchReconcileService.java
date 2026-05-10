package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.dto.DispatchReconcileResponse;
import com.samhanair.logis.arologis.dto.MismatchedRow;
import com.samhanair.logis.arologis.parser.VendorExcelParser;
import com.samhanair.logis.arologis.parser.VendorExcelRow;
import com.samhanair.logis.arologis.repository.DispatchRepository;
import com.samhanair.logis.arologis.repository.VehicleRepository;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 운송사 실배차 비교 service — Phase 10 PR-F1 BE-2 (legacy GAS 11번).
 *
 * <p>Samhan Public 이식 패턴 — 자체 dispatch 자동 조회 + 운송사 엑셀 업로드 유지 (사용자 명시:
 * "운송사 엑셀은 그대로 업로드 유지, 자동 수집 X"). 우리 시스템 자체 dispatch 는 from/to 자동
 * 조회 (DispatchRepository.findByDispatchDateBetween).
 *
 * <p>흐름:
 * <ol>
 *   <li>자체 dispatch 자동 조회 + vehicle_stops 평탄화 → (dispatchDate + slipNo) 키 set 생성</li>
 *   <li>각 vendor 엑셀 parse (다중 vendor 통합)</li>
 *   <li>(dispatchDate + slipNo) 키로 left-join — TRUE / FALSE_LEFT / FALSE_RIGHT 분류</li>
 *   <li>{@link DispatchReconcileResponse} 반환</li>
 * </ol>
 *
 * <p>매칭 키 정의:
 * <ul>
 *   <li>우리 측 slipNo = {@link VehicleStop#getParsedKakaoSeq()} (카톡 슬립번호 Long)</li>
 *   <li>vendor 측 slipNo = {@link VendorExcelRow#slipNo()} (운송장 번호 String)</li>
 *   <li>String 변환 후 trim 비교 (vendor 가 leading zero 를 보존하는 경우 고려)</li>
 *   <li>날짜 = LocalDate exact 매칭 (timezone 없음)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchReconcileService {

    private final DispatchRepository dispatchRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleStopRepository stopRepository;
    private final VendorExcelParser vendorExcelParser;

    /**
     * 운송사 실배차 비교 실행 — multipart 다중 vendor 업로드 + 자체 dispatch 자동 조회 + left-join 분류.
     *
     * <p>partial parse 허용 — 일부 vendor 엑셀 파싱 실패 (헤더 인식 실패) 시 해당 vendor 만 빈 list
     * 로 처리하고 다른 vendor 결과는 살림. 단 .xlsx 형식 자체가 깨진 경우는 INVALID_INPUT.
     *
     * @param files multipart 다중 vendor 엑셀 (필수, 최소 1개, .xlsx 만 허용)
     * @param from  자체 dispatch 자동 조회 시작일
     * @param to    자체 dispatch 자동 조회 종료일 (from 이후)
     * @return 매칭 + mismatch 결과
     * @throws BusinessException(INVALID_INPUT) files null/empty, from/to 누락 또는 from &gt; to,
     *                                          엑셀 형식 오류
     */
    @Transactional(readOnly = true)
    public DispatchReconcileResponse reconcile(List<MultipartFile> files, LocalDate from, LocalDate to) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "운송사 엑셀 파일이 비어있습니다 (최소 1개)");
        }
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from / to 는 필수입니다");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from 은 to 이전이어야 합니다");
        }

        // 1) 자체 dispatch 자동 조회 + stop 평탄화
        List<Dispatch> dispatches = dispatchRepository
                .findAllByDispatchDateBetweenOrderByDispatchDateAsc(from, to);
        List<DispatchLine> dispatchLines = flattenDispatches(dispatches);

        // 2) vendor 엑셀 다중 parse (partial parse 허용)
        List<VendorExcelRow> vendorRows = new ArrayList<>();
        int validVendorCount = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String vendorName = extractVendorName(file.getOriginalFilename());
            try (InputStream in = file.getInputStream()) {
                List<VendorExcelRow> parsed = vendorExcelParser.parse(in, vendorName);
                if (!parsed.isEmpty()) {
                    validVendorCount++;
                    vendorRows.addAll(parsed);
                } else {
                    log.warn("vendor 엑셀 헤더 인식 실패 (partial parse) — vendor={}", vendorName);
                }
            } catch (IOException ex) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "vendor 엑셀 stream 읽기 실패: " + vendorName, ex);
            }
        }

        // 3) left-join — (date + slipNo) 키 매칭
        return joinAndClassify(from, to, validVendorCount, dispatchLines, vendorRows);
    }

    /**
     * dispatch + vehicle + stops 를 라인 단위로 평탄화.
     *
     * <p>dispatch 1건은 vehicle N개 + stop N*M개로 펼쳐진다. 매칭 키는 (dispatchDate +
     * stop.parsedKakaoSeq) 이므로 stop 단위 평탄화가 필요.
     */
    List<DispatchLine> flattenDispatches(List<Dispatch> dispatches) {
        List<DispatchLine> lines = new ArrayList<>();
        for (Dispatch d : dispatches) {
            List<Vehicle> vehicles = vehicleRepository.findAllByDispatchIdOrderBySequenceAsc(d.getId());
            for (Vehicle v : vehicles) {
                List<VehicleStop> stops = stopRepository.findAllByVehicleIdOrderBySequenceAsc(v.getId());
                for (VehicleStop s : stops) {
                    if (s.getParsedKakaoSeq() == null) {
                        continue; // 슬립번호 미파싱 stop 은 매칭 불가 — 스킵
                    }
                    LocalTime actualTime = null;
                    if (s.getActualArrivalTime() != null) {
                        actualTime = s.getActualArrivalTime().toLocalTime();
                    } else if (s.getActualDeliveryTime() != null) {
                        actualTime = s.getActualDeliveryTime().toLocalTime();
                    }
                    lines.add(new DispatchLine(
                            d.getDispatchDate(),
                            String.valueOf(s.getParsedKakaoSeq()),
                            s.getParsedPartnerName(),
                            actualTime));
                }
            }
        }
        return lines;
    }

    /**
     * left-join 로직 — 양쪽 키 set 비교 후 mismatch 분류.
     *
     * <p>같은 (date + slipNo) 키가 양쪽 모두 존재 → TRUE (matchedCount++).
     * 우리만 존재 → FALSE_LEFT (vendor 가 미접수). vendor 만 존재 → FALSE_RIGHT (자체 등록 누락).
     *
     * <p>중복 stops/vendor row 처리 — 키 중복은 첫 행만 매칭으로 인정 (나머지는 무시). 실제
     * 운영에서 같은 슬립번호가 같은 날 두 번 나타나는 경우는 드물고, 데이터 품질 이슈로 분류.
     */
    DispatchReconcileResponse joinAndClassify(
            LocalDate from, LocalDate to, int vendorCount,
            List<DispatchLine> dispatchLines, List<VendorExcelRow> vendorRows) {

        // dispatch 측 key → DispatchLine
        Map<String, DispatchLine> dispatchByKey = new HashMap<>();
        for (DispatchLine d : dispatchLines) {
            dispatchByKey.putIfAbsent(matchKey(d.dispatchDate(), d.slipNo()), d);
        }
        // vendor 측 key → VendorExcelRow
        Map<String, VendorExcelRow> vendorByKey = new HashMap<>();
        for (VendorExcelRow v : vendorRows) {
            if (v.dispatchDate() == null) {
                // 날짜 없는 vendor 행은 매칭 불가 → FALSE_RIGHT 로 직접 분류 (별도 처리 아래)
                continue;
            }
            vendorByKey.putIfAbsent(matchKey(v.dispatchDate(), v.slipNo()), v);
        }

        Set<String> matchedKeys = new HashSet<>();
        List<MismatchedRow> mismatched = new ArrayList<>();

        // dispatch 순회 — vendor 매칭 시도
        for (Map.Entry<String, DispatchLine> e : dispatchByKey.entrySet()) {
            DispatchLine d = e.getValue();
            VendorExcelRow v = vendorByKey.get(e.getKey());
            if (v != null) {
                matchedKeys.add(e.getKey());
            } else {
                // FALSE_LEFT — 우리만 존재
                mismatched.add(new MismatchedRow(
                        MismatchedRow.Status.FALSE_LEFT,
                        d.slipNo(),
                        d.dispatchDate(),
                        null,
                        null,
                        d.actualTime(),
                        d.partnerName(),
                        "운송사 엑셀 누락 (자체 dispatch 만 존재)"));
            }
        }

        // vendor 순회 — dispatch 미매칭 = FALSE_RIGHT
        for (Map.Entry<String, VendorExcelRow> e : vendorByKey.entrySet()) {
            if (matchedKeys.contains(e.getKey())) {
                continue;
            }
            VendorExcelRow v = e.getValue();
            mismatched.add(new MismatchedRow(
                    MismatchedRow.Status.FALSE_RIGHT,
                    v.slipNo(),
                    v.dispatchDate(),
                    v.vendorName(),
                    v.expectedTime(),
                    null,
                    v.partnerName(),
                    "자체 dispatch 누락 (운송사 엑셀만 존재)"));
        }

        // vendor 행 중 날짜 null 인 행도 FALSE_RIGHT 로 별도 추가 (데이터 품질 이슈)
        for (VendorExcelRow v : vendorRows) {
            if (v.dispatchDate() == null) {
                mismatched.add(new MismatchedRow(
                        MismatchedRow.Status.FALSE_RIGHT,
                        v.slipNo(),
                        null,
                        v.vendorName(),
                        v.expectedTime(),
                        null,
                        v.partnerName(),
                        "운송사 엑셀 날짜 인식 실패 (매칭 불가)"));
            }
        }

        return new DispatchReconcileResponse(
                from, to,
                vendorCount,
                dispatchLines.size(),
                vendorRows.size(),
                matchedKeys.size(),
                mismatched);
    }

    /** 매칭 키 = "yyyy-MM-dd|slipNo" 정규화. */
    private String matchKey(LocalDate date, String slipNo) {
        return (date == null ? "" : date.toString()) + "|" + (slipNo == null ? "" : slipNo.trim());
    }

    /**
     * multipart 파일명에서 vendor 식별자 추출 — 확장자 제거 + 한국어 vendor 부분 추출.
     *
     * <p>예: "CJ대한통운_2026-05-09.xlsx" → "CJ대한통운". 사용자가 자유 형식으로 업로드해도
     * 응답에 첫 토큰 정도만 표시. 미인식 시 파일명 전체.
     */
    String extractVendorName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "(unknown)";
        }
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        // 첫 underscore/space/hyphen 앞이 통상 vendor — 분리, 단 ascii hyphen 은 vendor 명에 포함 가능
        int sep = -1;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c == '_' || c == ' ' || c == '(') {
                sep = i;
                break;
            }
        }
        if (sep > 0) {
            return base.substring(0, sep);
        }
        return base;
    }

    /**
     * dispatch 평탄화 1행 — 매칭 source.
     *
     * @param dispatchDate 배차 도착 일자
     * @param slipNo       VehicleStop.parsedKakaoSeq String 변환
     * @param partnerName  VehicleStop.parsedPartnerName (옵션)
     * @param actualTime   actualArrivalTime / actualDeliveryTime 의 LocalTime (옵션)
     */
    record DispatchLine(
            LocalDate dispatchDate,
            String slipNo,
            String partnerName,
            LocalTime actualTime) {
    }
}
