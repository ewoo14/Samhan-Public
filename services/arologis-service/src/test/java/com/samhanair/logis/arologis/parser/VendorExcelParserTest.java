package com.samhanair.logis.arologis.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * {@link VendorExcelParser} 단위 테스트 — Phase 10 PR-F1 BE-2.
 *
 * <p>헤더 매처 5 case:
 * <ol>
 *   <li>"접수시간" / "접수일자" — default (CJ대한통운 가상 양식)</li>
 *   <li>"발송일자" / "발송시간" — 롯데글로벌로지스 가상 양식</li>
 *   <li>"출고일" — 한진택배 가상 양식</li>
 *   <li>2-층 헤더 (row 0 = 그룹 / row 1 = 컬럼) — legacy GAS findIndex 패턴 호환</li>
 *   <li>매칭 실패 (vendor 양식 미인지) → 빈 list (partial parse 허용)</li>
 * </ol>
 */
class VendorExcelParserTest {

    private final VendorExcelParser parser = new VendorExcelParser();

    @Test
    void 헤더매처_접수시간_접수일자_정상_parse() throws IOException {
        byte[] xlsx = singleHeaderXlsx(
                new String[]{"운송장번호", "접수일자", "접수시간", "업체명"},
                new Object[][]{
                        {"S-001", "2026-05-09", "09:30", "삼한공조"},
                        {"S-002", "2026-05-09", "10:00", "ABC"}});

        List<VendorExcelRow> rows = parser.parse(new ByteArrayInputStream(xlsx), "CJ대한통운");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).vendorName()).isEqualTo("CJ대한통운");
        assertThat(rows.get(0).slipNo()).isEqualTo("S-001");
        assertThat(rows.get(0).dispatchDate().toString()).isEqualTo("2026-05-09");
        assertThat(rows.get(0).expectedTime().toString()).isEqualTo("09:30");
        assertThat(rows.get(0).partnerName()).isEqualTo("삼한공조");
    }

    @Test
    void 헤더매처_발송일자_발송시간_정상_parse() throws IOException {
        byte[] xlsx = singleHeaderXlsx(
                new String[]{"예약번호", "발송일자", "발송시간", "송하인"},
                new Object[][]{{"R-100", "2026-05-09", "14:00", "테스트"}});

        List<VendorExcelRow> rows = parser.parse(new ByteArrayInputStream(xlsx), "롯데");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).slipNo()).isEqualTo("R-100");
        assertThat(rows.get(0).expectedTime().toString()).isEqualTo("14:00");
        assertThat(rows.get(0).partnerName()).isEqualTo("테스트");
    }

    @Test
    void 헤더매처_출고일_정상_parse() throws IOException {
        byte[] xlsx = singleHeaderXlsx(
                new String[]{"송장번호", "출고일", "거래처명"},
                new Object[][]{{"H-200", "2026-05-09", "한진수령업체"}});

        List<VendorExcelRow> rows = parser.parse(new ByteArrayInputStream(xlsx), "한진");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).slipNo()).isEqualTo("H-200");
        assertThat(rows.get(0).dispatchDate().toString()).isEqualTo("2026-05-09");
        assertThat(rows.get(0).expectedTime()).isNull(); // 시간 헤더 없음
        assertThat(rows.get(0).partnerName()).isEqualTo("한진수령업체");
    }

    @Test
    void 헤더매처_2층_헤더_row0_그룹_row1_컬럼_정상_parse() throws IOException {
        // legacy GAS 11번 패턴 — row 0 = "접수정보"/"고객정보" 등 그룹, row 1 = 실제 컬럼명
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Vendor");
            Row groupRow = sheet.createRow(0);
            groupRow.createCell(0).setCellValue("접수정보");
            groupRow.createCell(1).setCellValue("접수정보");
            groupRow.createCell(2).setCellValue("고객정보");
            Row colRow = sheet.createRow(1);
            colRow.createCell(0).setCellValue("운송장번호");
            colRow.createCell(1).setCellValue("접수일자");
            colRow.createCell(2).setCellValue("업체명");
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("S-LAYER-2");
            data.createCell(1).setCellValue("2026-05-09");
            data.createCell(2).setCellValue("2층업체");
            wb.write(out);

            List<VendorExcelRow> rows = parser.parse(new ByteArrayInputStream(out.toByteArray()), "vendor");
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).slipNo()).isEqualTo("S-LAYER-2");
            assertThat(rows.get(0).partnerName()).isEqualTo("2층업체");
        }
    }

    @Test
    void 헤더매처_미인식_vendor_양식_빈리스트_partial_parse_허용() throws IOException {
        // 매처 keyword 모두 미매칭 (영문 양식)
        byte[] xlsx = singleHeaderXlsx(
                new String[]{"WaybillNo", "PickupDate", "PickupTime", "ConsigneeName"},
                new Object[][]{{"X-1", "2026-05-09", "09:30", "name"}});

        List<VendorExcelRow> rows = parser.parse(new ByteArrayInputStream(xlsx), "unknown-vendor");
        assertThat(rows).isEmpty(); // partial parse — 헤더 인식 실패 시 빈 list
    }

    @Test
    void parse_엑셀_형식_오류_BusinessException_INVALID_INPUT() {
        byte[] notXlsx = "이것은 엑셀이 아닙니다".getBytes();
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(notXlsx), "v"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    private byte[] singleHeaderXlsx(String[] headers, Object[][] dataRows) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Vendor");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] data = dataRows[r];
                for (int c = 0; c < data.length; c++) {
                    Object v = data[c];
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(v));
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
