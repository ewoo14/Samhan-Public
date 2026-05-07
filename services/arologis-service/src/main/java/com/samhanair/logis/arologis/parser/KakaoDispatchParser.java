package com.samhanair.logis.arologis.parser;

import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.VehicleTonnage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카톡 배차 메시지 파서 — Phase 10 W10-1.
 *
 * <p>정규표현식 + heuristic 조합으로 사용자 제공 카톡 예시 (13 차량 / 약 50 정차) 를 80% 이상 정확도로
 * 파싱. 미해석 라인 ("상일상차" / "초월상차") 은 status=UNPARSED + group label 로 보존.
 *
 * <p>본 단계는 parser + 미리보기 응답 only. 수동 보정 UI 는 W10-3 모바일/어드민 화면에서 구현.
 *
 * <p>파싱 단계:
 * <ol>
 *   <li>헤더 라인 — "8일착 야상입니다" → dispatchDate (월 추정 — 입력 시점 기준 가까운 월/년) + dispatchType</li>
 *   <li>차량 그룹 — "1." / "2." / "13." 시작 라인 → 신규 vehicle</li>
 *   <li>톤수 라인 — 그룹 끝 "1톤" / "2.5톤" → VehicleTonnage</li>
 *   <li>정차 라인 — "-인천 남동구 구월동(에스엠하나공조-214)아침8시" → parsedAddress / partnerName / partnerCode / notes</li>
 *   <li>미해석 라인 — "상일상차" 등 정차 패턴 미매칭 라인 → unparsed group label 로 보존</li>
 * </ol>
 */
@Slf4j
@Component
public class KakaoDispatchParser {

    /** "8일착 야상입니다" / "10일착 주간입니다" 헤더. */
    private static final Pattern HEADER = Pattern.compile("^(\\d+)일착\\s*(.+?)입니다\\s*$");

    /** "1. 상일+초월" / "2." / "13." 차량 헤더. */
    private static final Pattern VEHICLE_HEADER = Pattern.compile("^(\\d+)\\.\\s*(.*)$");

    /** "1톤" / "2.5톤" / "1.4톤" 톤수 라인. */
    private static final Pattern TONNAGE = Pattern.compile("^(\\d+(?:\\.\\d+)?)톤\\s*$");

    /**
     * 정차 라인 — "-인천 남동구 구월동(에스엠하나공조-214)아침8시"
     *               "-인천남동구논현동755-1(하늘시스템-218)9시하차"
     *               "-경기 김포시 고촌읍(삼공주에어컨-17)오전일찍"
     */
    private static final Pattern STOP = Pattern.compile("^[-–]\\s*([^()]+)\\(([^()]+?)-(\\d+)\\)(.*)$");

    /**
     * 카톡 메시지 전체를 파싱하여 {@link ParsedDispatch} 반환.
     *
     * <p>날짜 추정 — 입력 시점 ({@code referenceDate}) 기준 day 가 가장 가까운 월/년 (현재 월 또는 다음 월).
     *
     * @param kakaoText 카톡 원본 메시지
     * @param referenceDate 날짜 추정 기준 일자 (보통 LocalDate.now())
     * @return 파싱 결과 ; 헤더 미매칭 시 IllegalArgumentException
     */
    public ParsedDispatch parse(String kakaoText, LocalDate referenceDate) {
        if (kakaoText == null || kakaoText.isBlank()) {
            throw new IllegalArgumentException("카톡 메시지가 비어있습니다");
        }
        if (referenceDate == null) {
            referenceDate = LocalDate.now();
        }

        String[] lines = kakaoText.split("\\R", -1);
        int totalLines = 0;
        int parsedLines = 0;

        LocalDate dispatchDate = null;
        DispatchType dispatchType = null;

        List<ParsedDispatch.ParsedVehicle> vehicles = new ArrayList<>();

        // 진행 중 차량 상태
        Integer currentVehicleSeq = null;
        String currentVehicleLabel = null;
        VehicleTonnage currentVehicleTonnage = null;
        List<ParsedDispatch.ParsedStop> currentStops = new ArrayList<>();
        int currentStopSeq = 0;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            totalLines++;

            // 1) 헤더 라인 (한 번만)
            if (dispatchDate == null) {
                Matcher m = HEADER.matcher(line);
                if (m.matches()) {
                    int day = Integer.parseInt(m.group(1));
                    dispatchDate = inferDate(day, referenceDate);
                    dispatchType = inferDispatchType(m.group(2).trim());
                    parsedLines++;
                    continue;
                }
            }

            // 2) 톤수 라인 우선 매칭 (VEHICLE_HEADER 보다 먼저 — "1.4톤" 이 차량 헤더로 오인되는 경로 차단)
            //    톤수 라인은 "1톤" / "2.5톤" / "1.4톤" 형식 — 차량 컨텍스트가 시작된 후만 의미.
            if (currentVehicleSeq != null) {
                Matcher tmFirst = TONNAGE.matcher(line);
                if (tmFirst.matches()) {
                    currentVehicleTonnage = VehicleTonnage.fromRaw(tmFirst.group(1));
                    parsedLines++;
                    continue;
                }
            }

            // 3) 차량 헤더 라인 ("1." / "2." / "13.")
            Matcher vh = VEHICLE_HEADER.matcher(line);
            if (vh.matches()) {
                int seq = Integer.parseInt(vh.group(1));
                String labelOrFirstStop = vh.group(2);

                // 직전 차량 flush
                if (currentVehicleSeq != null) {
                    flushVehicle(vehicles, currentVehicleSeq, currentVehicleTonnage,
                            currentVehicleLabel, currentStops);
                }

                currentVehicleSeq = seq;
                currentVehicleTonnage = null;
                currentStops = new ArrayList<>();
                currentStopSeq = 0;

                // 차량 헤더 옆 텍스트가 정차 패턴 ("-주소(파트너-전표)...") 인 경우 → 첫 정차로 처리
                // 예: "2. -경기 김포시 고촌읍(삼공주에어컨-17)오전일찍"
                if (!labelOrFirstStop.isBlank()) {
                    Matcher stopOnHeader = STOP.matcher(labelOrFirstStop);
                    if (stopOnHeader.matches()) {
                        currentVehicleLabel = null;
                        currentStopSeq++;
                        currentStops.add(toParsedStop(currentStopSeq, labelOrFirstStop, stopOnHeader));
                    } else {
                        currentVehicleLabel = labelOrFirstStop;
                    }
                } else {
                    currentVehicleLabel = null;
                }
                parsedLines++;
                continue;
            }

            // 차량 미시작 상태에서 다른 라인이 나오면 skip (헤더 외 안전망)
            if (currentVehicleSeq == null) {
                log.debug("차량 시작 전 라인 skip: {}", line);
                continue;
            }

            // 4) 정차 라인 (주소 + (사업자명-전표번호) + notes)
            Matcher sm = STOP.matcher(line);
            if (sm.matches()) {
                currentStopSeq++;
                currentStops.add(toParsedStop(currentStopSeq, line, sm));
                parsedLines++;
                continue;
            }

            // 5) 미해석 라인 ("상일상차" / "초월상차" 등 group label)
            currentStopSeq++;
            currentStops.add(new ParsedDispatch.ParsedStop(
                    currentStopSeq, line, null, null, null, line, true));
            // unparsed 도 일종의 보존 처리 — 정확도 계산에서는 미포함 (parsedLines 미증가)
        }

        // 마지막 차량 flush
        if (currentVehicleSeq != null) {
            flushVehicle(vehicles, currentVehicleSeq, currentVehicleTonnage,
                    currentVehicleLabel, currentStops);
        }

        if (dispatchDate == null) {
            throw new IllegalArgumentException("헤더 라인 파싱 실패 — '8일착 야상입니다' 형식 필요");
        }

        return new ParsedDispatch(dispatchDate, dispatchType, vehicles, totalLines, parsedLines);
    }

    /**
     * 차량 flush — tonnage 가 null 이면 default {@link VehicleTonnage#TONNAGE_1} 사용 (skeleton 단계
     * — 수동 보정 의무).
     */
    private void flushVehicle(List<ParsedDispatch.ParsedVehicle> vehicles, int seq, VehicleTonnage tonnage,
                              String label, List<ParsedDispatch.ParsedStop> stops) {
        VehicleTonnage finalTonnage = tonnage == null ? VehicleTonnage.TONNAGE_1 : tonnage;
        vehicles.add(new ParsedDispatch.ParsedVehicle(seq, finalTonnage, label, stops));
    }

    /** 정차 라인 정규표현식 매칭 결과 → {@link ParsedDispatch.ParsedStop}. */
    private ParsedDispatch.ParsedStop toParsedStop(int seq, String rawLine, Matcher m) {
        String address = m.group(1) == null ? null : m.group(1).trim();
        String partnerName = m.group(2) == null ? null : m.group(2).trim();
        Long partnerCode = parsePartnerCode(m.group(3));
        String notes = m.group(4) == null ? null : m.group(4).trim();
        if (notes != null && notes.isBlank()) {
            notes = null;
        }
        return new ParsedDispatch.ParsedStop(seq, rawLine, address, partnerName, partnerCode, notes, false);
    }

    private Long parsePartnerCode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * day (1~31) → 입력 시점 기준 가장 가까운 LocalDate (현재 월 또는 다음 월).
     *
     * <p>예: referenceDate = 2026-05-07, day = 8 → 2026-05-08.
     *      referenceDate = 2026-05-25, day = 1 → 2026-06-01 (다음 월).
     */
    LocalDate inferDate(int day, LocalDate reference) {
        // 1차 — reference 의 월에 day 적용
        try {
            LocalDate sameMonth = reference.withDayOfMonth(day);
            if (!sameMonth.isBefore(reference.minusDays(7))) {
                // 같은 월 day 가 7일 이상 과거가 아니면 그대로 사용
                return sameMonth;
            }
        } catch (Exception ignored) {
            // day 가 해당 월에 존재하지 않는 경우 (예: 2월 31일)
        }
        // 2차 — 다음 월 day
        LocalDate nextMonth = reference.plusMonths(1);
        try {
            return nextMonth.withDayOfMonth(day);
        } catch (Exception ex) {
            return nextMonth.withDayOfMonth(Math.min(day, nextMonth.lengthOfMonth()));
        }
    }

    /** "야상" / "주간" / "특급" 등 → DispatchType. fallback DAY. */
    DispatchType inferDispatchType(String text) {
        if (text == null) {
            return DispatchType.DAY;
        }
        if (text.contains("야상") || text.contains("야간")) {
            return DispatchType.NIGHT;
        }
        if (text.contains("특급") || text.contains("긴급") || text.contains("EXPRESS")) {
            return DispatchType.EXPRESS;
        }
        return DispatchType.DAY;
    }
}
