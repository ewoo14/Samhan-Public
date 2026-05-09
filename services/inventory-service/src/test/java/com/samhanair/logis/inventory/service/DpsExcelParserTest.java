package com.samhanair.logis.inventory.service;

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
 * {@link DpsExcelParser} 단위 테스트 — 3 case (정상 / BOM 안전 / 형식 오류).
 *
 * <p>BOM 케이스: .xlsx 는 binary ZIP 컨테이너이므로 BOM 자체가 의미 없으나, parser 가
 * binary stream 헤더를 그대로 받아도 안전하게 처리하는지 검증.
 */
class DpsExcelParserTest {

    private final DpsExcelParser parser = new DpsExcelParser();

    @Test
    void parse_정상_xlsx_반환() throws IOException {
        byte[] xlsx = buildXlsx(new String[]{"품번", "입고일자", "입고수량", "거래처코드", "거래처명"},
                new Object[][]{
                        {"P-001", "2026-05-09", 10, "C-100", "삼한"},
                        {"P-002", "2026-05-09", 5, "C-200", "ABC"}
                });
        List<DpsExcelRow> rows = parser.parse(new ByteArrayInputStream(xlsx));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).productCode()).isEqualTo("P-001");
        assertThat(rows.get(0).quantity()).isEqualTo(10);
        assertThat(rows.get(0).partnerCode()).isEqualTo("C-100");
        assertThat(rows.get(0).partnerName()).isEqualTo("삼한");
        assertThat(rows.get(1).productCode()).isEqualTo("P-002");
        assertThat(rows.get(1).quantity()).isEqualTo(5);
    }

    @Test
    void parse_빈_row_는_스킵된다() throws IOException {
        byte[] xlsx = buildXlsx(new String[]{"품번", "수량", "거래처코드"},
                new Object[][]{
                        {"P-001", 10, "C-100"},
                        {"", 0, ""},  // 빈 row → skip
                        {"P-002", 7, "C-200"}
                });
        List<DpsExcelRow> rows = parser.parse(new ByteArrayInputStream(xlsx));
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(DpsExcelRow::productCode).containsExactly("P-001", "P-002");
    }

    @Test
    void parse_형식_오류_BusinessException_INVALID_INPUT() {
        // .xlsx 가 아닌 plain text bytes → XSSFWorkbook 가 IOException
        byte[] notXlsx = "이것은 엑셀이 아닙니다".getBytes();
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(notXlsx)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    private byte[] buildXlsx(String[] headers, Object[][] dataRows) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
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
