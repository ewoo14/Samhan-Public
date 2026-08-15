package com.samhanair.logis.inventory.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 가입고처리 레거시 GAS의 XLSX 읽기/정제 계약만 담당한다.
 *
 * <p>모든 시트의 6·7행을 헤더로 사용하고 8행부터 읽는다. 레거시가 조용히 버리던
 * 시트/행은 미리보기에서 설명할 수 있도록 {@link ParseResult}에 집계한다.
 */
@Component
public class InboundXlsxParser {

    private static final List<String> HEADERS = List.of(
            "NO", "고객명", "모델", "주문", "배달예정", "물류출고", "진행상태",
            "차량번호", "기사명", "주문일자", "주문번호");
    private static final List<String> FILTER_KEYWORDS = List.of("삼성", "초월", "이화", "상일", "신인호", "삼한");
    private static final Map<String, String> WAREHOUSE_BY_CUSTOMER = Map.of(
            "삼성창고", "00003", "초월창고", "00003", "이화창고", "2", "상일물류", "2", "상일창고", "2");
    private static final List<Integer> DEDUP_COLUMNS = List.of(1, 2, 3, 7, 8, 9, 10);

    /** 실제 xlsx stream을 읽어 레거시 정제 결과와 누락 사유를 반환한다. */
    public ParseResult parse(InputStream input) {
        if (input == null) throw new IllegalArgumentException("가입고 XLSX가 비어있습니다");
        try (Workbook workbook = new XSSFWorkbook(input)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<InboundRow> rows = new ArrayList<>();
            List<String> shortSheets = new ArrayList<>();
            List<String> headerSheets = new ArrayList<>();
            int keywordFiltered = 0;
            int deduplicated = 0;
            Map<String, Integer> globalUnpaired = new HashMap<>();

            for (Sheet sheet : workbook) {
                if (sheet.getLastRowNum() + 1 < 6) {
                    shortSheets.add(sheet.getSheetName());
                    continue;
                }
                Map<String, Integer> columns = headers(sheet, formatter);
                if (!columns.containsKey("고객명") || !columns.containsKey("주문번호")) {
                    headerSheets.add(sheet.getSheetName());
                    continue;
                }
                Map<String, Integer> localCounts = new HashMap<>();
                for (int rowIndex = 7; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    String[] values = new String[HEADERS.size()];
                    for (int i = 0; i < HEADERS.size(); i++) {
                        values[i] = cell(row, columns.get(HEADERS.get(i)), formatter);
                    }
                    String customer = values[1];
                    if (FILTER_KEYWORDS.stream().noneMatch(customer::contains)) {
                        keywordFiltered++;
                        continue;
                    }
                    String dedupKey = DEDUP_COLUMNS.stream().map(i -> values[i]).reduce((a, b) -> a + "|" + b).orElse("");
                    int available = globalUnpaired.getOrDefault(dedupKey, 0);
                    if (available > 0) {
                        globalUnpaired.put(dedupKey, available - 1);
                        deduplicated++;
                        continue;
                    }
                    String rawModel = values[2];
                    String cleanModel = cleanModel(rawModel);
                    String warehouse = WAREHOUSE_BY_CUSTOMER.getOrDefault(customer, "2");
                    rows.add(new InboundRow(
                            sheet.getSheetName(), rowIndex + 1, values[0], customer, rawModel, cleanModel,
                            values[3], values[4], values[5], values[6], values[7], values[8],
                            values[9].isBlank() ? "" : values[9].split(" ", 2)[0], values[10], warehouse,
                            quantity(values[5], values[3])));
                    localCounts.merge(dedupKey, 1, Integer::sum);
                }
                localCounts.forEach((key, count) -> globalUnpaired.merge(key, count, Integer::sum));
            }
            return new ParseResult(rows, shortSheets, headerSheets, keywordFiltered, deduplicated);
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) throw (IllegalArgumentException) ex;
            throw new IllegalArgumentException("가입고 XLSX 파싱 실패: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Integer> headers(Sheet sheet, DataFormatter formatter) {
        Map<String, Integer> result = new HashMap<>();
        Row first = sheet.getRow(5);
        Row second = sheet.getRow(6);
        int max = Math.max(lastCell(first), lastCell(second));
        for (int c = 0; c < max; c++) {
            String h2 = normalize(cell(second, c, formatter));
            String h1 = normalize(cell(first, c, formatter));
            String header = h2.isBlank() ? h1 : h2;
            if (!header.isBlank() && HEADERS.contains(header)) result.put(header, c);
        }
        return result;
    }

    private int lastCell(Row row) { return row == null ? 0 : Math.max(0, row.getLastCellNum()); }

    private String cell(Row row, Integer column, DataFormatter formatter) {
        if (row == null || column == null || row.getCell(column) == null) return "";
        return formatter.formatCellValue(row.getCell(column)).trim();
    }

    private String normalize(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\s]+", ""); }

    static String cleanModel(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("\\[.*?\\]|\\(.*?\\)", "").trim();
        return cleaned.replaceAll("(?i)GHP", "가스히트펌프");
    }

    static int quantity(String outbound, String order) {
        Double selected = number(outbound);
        if (selected == null) selected = number(order);
        return selected == null ? 0 : (int) Math.round(selected);
    }

    private static Double number(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Double.valueOf(value.replace(",", "").trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    public record ParseResult(
            List<InboundRow> rows,
            List<String> skippedShortSheets,
            List<String> skippedHeaderSheets,
            int keywordFilteredRows,
            int deduplicatedRows) {}

    public record InboundRow(
            String sourceSheet, int sourceRow, String no, String customerName, String rawModel, String cleanModel,
            String orderQuantityRaw, String deliveryExpected, String outboundQuantityRaw, String progressStatus,
            String vehicleNumber, String driverName, String orderDate, String orderNumber,
            String warehouseCode, int quantity) {}
}
