package com.samhanair.logis.arologis.parser;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 운송사 엑셀 (.xlsx) 파서 — Phase 10 PR-F1 BE-2 (legacy GAS 11번).
 *
 * <p>legacy GAS 11번 ("운송사-실배차내역 비교") 의 vendor 별 양식 차이를 흡수하기 위해 헤더 매처를
 * 다층화한다. 헤더 row 는 1행 (그룹) + 2행 (실제 컬럼명) 의 2-층 구조 또는 단일 헤더 row 두
 * 패턴 모두 허용:
 *
 * <ol>
 *   <li>row 0 = 헤더 group ("접수정보" / "출고정보" 등) → row 1 이 실제 컬럼명이라고 판단</li>
 *   <li>row 0 자체에 매칭 keyword 포함 → row 0 = 헤더 row</li>
 * </ol>
 *
 * <h2>vendor 별 헤더 매처 다층화 (legacy GAS findIndex(h =&gt; String(h).includes('접수시간')) 패턴)</h2>
 * <ul>
 *   <li>슬립번호: "슬립번호" / "운송장번호" / "송장번호" / "주문번호" / "예약번호"</li>
 *   <li>접수일자: "접수일자" / "접수일" / "발송일자" / "출고일" / "출고일자" / "픽업일"</li>
 *   <li>접수시간: "접수시간" / "발송시간" / "출고시간" / "픽업시간"</li>
 *   <li>업체명: "업체명" / "거래처명" / "송하인" / "수하인" / "고객명"</li>
 * </ul>
 *
 * <p>TODO (사용자 미첨부 sample): vendor 별 sample 첨부 후 다음 vendor 별 매처 추가 권장:
 * <ul>
 *   <li>CJ대한통운 — "운송장 번호" (공백 포함) / "집화일자"</li>
 *   <li>롯데글로벌로지스 — "예약번호" / "픽업예정일"</li>
 *   <li>한진택배 — "송장번호" / "접수일자"</li>
 * </ul>
 * 추가 시 {@link #SLIP_KEYWORDS} 등 keyword 리스트에 항목만 append (정규화 후 contains 매칭).
 */
@Component
public class VendorExcelParser {

    /** 슬립/운송장 번호 컬럼 keyword (정규화 후 contains 매칭). */
    public static final List<String> SLIP_KEYWORDS = List.of(
            "슬립번호", "운송장번호", "송장번호", "주문번호", "예약번호");

    /** 접수/발송/출고 일자 컬럼 keyword. */
    public static final List<String> DATE_KEYWORDS = List.of(
            "접수일자", "접수일", "발송일자", "발송일", "출고일자", "출고일", "픽업일", "집화일자");

    /** 접수/발송/출고 시각 컬럼 keyword. */
    public static final List<String> TIME_KEYWORDS = List.of(
            "접수시간", "발송시간", "출고시간", "픽업시간");

    /** 업체명/거래처 컬럼 keyword. */
    public static final List<String> PARTNER_KEYWORDS = List.of(
            "업체명", "거래처명", "거래처", "송하인", "수하인", "고객명");

    private static final String FIELD_SLIP = "SLIP";
    private static final String FIELD_DATE = "DATE";
    private static final String FIELD_TIME = "TIME";
    private static final String FIELD_PARTNER = "PARTNER";

    /**
     * .xlsx 입력 stream → vendor row 목록.
     *
     * <p>vendorName 은 호출자가 multipart 파일명에서 추출하여 주입 (parser 자체는 파일명 미인지).
     * 헤더 매칭 실패 시 (필수 = SLIP + DATE) 빈 list 반환 — 다중 vendor 통합 시 partial parse 허용
     * (사용자가 잘못된 파일을 섞어 업로드해도 다른 vendor 결과는 살림).
     *
     * @param input      .xlsx 바이너리 stream (호출자가 close 책임)
     * @param vendorName 응답에 노출할 vendor 식별자 (예: "CJ대한통운" 또는 파일명)
     * @return vendor row 목록 (헤더/빈 row 제외)
     * @throws BusinessException(INVALID_INPUT) .xlsx 형식 자체가 깨진 경우
     */
    public List<VendorExcelRow> parse(InputStream input, String vendorName) {
        if (input == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "vendor 엑셀 stream 이 비어있습니다");
        }

        try (Workbook workbook = new XSSFWorkbook(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "vendor 엑셀에 시트가 없습니다");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            // 2-층 헤더 (row 0 = group, row 1 = column) 또는 단일 헤더 (row 0) 자동 판별
            HeaderResolution header = resolveHeader(sheet, firstRowNum);
            if (header == null) {
                // 헤더 인식 실패 — 빈 list (partial parse — 다른 vendor 결과는 살림)
                return List.of();
            }

            List<VendorExcelRow> rows = new ArrayList<>();
            for (int r = header.dataStartRow; r <= lastRowNum; r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) {
                    continue;
                }
                String slipNo = readString(dataRow, header.index.get(FIELD_SLIP));
                if (slipNo == null || slipNo.isBlank()) {
                    continue; // 슬립번호 없는 row 스킵
                }
                LocalDate date = readDate(dataRow, header.index.get(FIELD_DATE));
                LocalTime time = readTime(dataRow, header.index.get(FIELD_TIME));
                String partner = readString(dataRow, header.index.get(FIELD_PARTNER));
                rows.add(new VendorExcelRow(
                        vendorName,
                        slipNo.trim(),
                        date,
                        time,
                        partner == null ? null : partner.trim()));
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "vendor 엑셀 파싱 실패: " + ex.getMessage(), ex);
        }
    }

    /**
     * 헤더 row 위치 + 컬럼 인덱스 자동 판별.
     *
     * <p>전략 — 2-층 헤더 시도 우선:
     * <ol>
     *   <li>row 0 + row 1 매칭 시도 (group + column)</li>
     *   <li>row 0 단독 매칭 시도</li>
     *   <li>둘 다 실패 → null (호출자가 빈 list 반환)</li>
     * </ol>
     *
     * <p>최소 매칭 조건 = SLIP keyword + DATE keyword 중 하나라도 매칭 (시간/업체명은 옵션).
     */
    private HeaderResolution resolveHeader(Sheet sheet, int firstRowNum) {
        Row row0 = sheet.getRow(firstRowNum);
        if (row0 == null) {
            return null;
        }
        Row row1 = sheet.getRow(firstRowNum + 1);

        // 1) row 1 (2-층 헤더) 시도 — 일반적으로 vendor 양식 = row 0 (그룹) + row 1 (컬럼명)
        if (row1 != null) {
            Map<String, Integer> idx = mapHeaders(row1);
            if (idx.containsKey(FIELD_SLIP) || idx.containsKey(FIELD_DATE)) {
                return new HeaderResolution(idx, firstRowNum + 2);
            }
        }
        // 2) row 0 단독 시도
        Map<String, Integer> idx0 = mapHeaders(row0);
        if (idx0.containsKey(FIELD_SLIP) || idx0.containsKey(FIELD_DATE)) {
            return new HeaderResolution(idx0, firstRowNum + 1);
        }
        return null;
    }

    /**
     * row 의 각 cell 을 한국어 keyword 매처에 통과시켜 컬럼 인덱스 매핑 생성.
     *
     * <p>매칭 우선순위 — 이미 매칭된 필드는 putIfAbsent 로 첫 매칭만 보존 (vendor 양식이 동일
     * keyword 를 두 번 가질 때 첫 컬럼 우선).
     */
    private Map<String, Integer> mapHeaders(Row row) {
        Map<String, Integer> index = new HashMap<>();
        if (row == null) {
            return index;
        }
        short last = row.getLastCellNum();
        for (int c = row.getFirstCellNum(); c < last; c++) {
            Cell cell = row.getCell(c);
            if (cell == null) {
                continue;
            }
            String name = readCellAsString(cell);
            if (name == null || name.isBlank()) {
                continue;
            }
            String normalized = name.trim().replaceAll("\\s+", "");
            if (matchesAny(normalized, SLIP_KEYWORDS)) {
                index.putIfAbsent(FIELD_SLIP, c);
            } else if (matchesAny(normalized, TIME_KEYWORDS)) {
                // TIME 우선 검사 — "접수시간" 이 "접수일자" 보다 더 구체적
                index.putIfAbsent(FIELD_TIME, c);
            } else if (matchesAny(normalized, DATE_KEYWORDS)) {
                index.putIfAbsent(FIELD_DATE, c);
            } else if (matchesAny(normalized, PARTNER_KEYWORDS)) {
                index.putIfAbsent(FIELD_PARTNER, c);
            }
        }
        return index;
    }

    private boolean matchesAny(String normalized, List<String> keywords) {
        for (String kw : keywords) {
            if (normalized.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String readString(Row row, Integer col) {
        if (col == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        return cell == null ? null : readCellAsString(cell);
    }

    private LocalDate readDate(Row row, Integer col) {
        if (col == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String s = readCellAsString(cell);
        if (s == null || s.isBlank()) {
            return null;
        }
        // ISO yyyy-MM-dd / yyyy/MM/dd / yyyy.MM.dd 시도, 실패 시 null
        String normalized = s.trim().replace("/", "-").replace(".", "-");
        try {
            return LocalDate.parse(normalized);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalTime readTime(Row row, Integer col) {
        if (col == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
        }
        String s = readCellAsString(cell);
        if (s == null || s.isBlank()) {
            return null;
        }
        // "HH:mm" / "HH:mm:ss" / "0930" 시도
        String trimmed = s.trim();
        try {
            if (trimmed.matches("\\d{4}")) {
                return LocalTime.of(Integer.parseInt(trimmed.substring(0, 2)),
                        Integer.parseInt(trimmed.substring(2, 4)));
            }
            if (trimmed.length() == 5) {
                return LocalTime.parse(trimmed);
            }
            if (trimmed.length() == 8) {
                return LocalTime.parse(trimmed);
            }
            return LocalTime.parse(trimmed);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String readCellAsString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK, _NONE, ERROR -> null;
        };
    }

    /** 헤더 인식 결과 — 컬럼 매핑 + 데이터 시작 row. */
    private record HeaderResolution(Map<String, Integer> index, int dataStartRow) {
    }
}
