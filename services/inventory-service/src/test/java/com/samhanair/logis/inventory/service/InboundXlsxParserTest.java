package com.samhanair.logis.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** 가입고 레거시 XLSX 계약을 실제 XLSX 바이너리로 고정하는 RED-first 테스트. */
class InboundXlsxParserTest {

    @Test
    void 모든_시트의_6_7행_헤더와_8행_데이터를_읽고_창고_수량_품목명을_정제한다() throws Exception {
        byte[] xlsx = workbook(
                sheet("첫번째", List.of(
                        row("","","","","",""), row("","","","","",""),
                        row("","","","","",""), row("","","","","",""),
                        row("","","","","",""),
                        row("NO","고객명","모델","주문","물류출고","주문번호"),
                        row("","","","","",""),
                        row("1","삼성창고","ABC GHP [옵션] (메모)","2","3.6","O-1"),
                        row("2","삼성창고","1WAY-ABC","9","","O-2"),
                        row("3","상일창고","1WAY-XYZ","5","","O-3"))),
                sheet("두번째", List.of(
                        row("","","","","",""), row("","","","","",""),
                        row("","","","","",""), row("","","","","",""),
                        row("","","","","",""),
                        row("NO","고객명","모델","주문","물류출고","주문번호"),
                        row("","","","","",""),
                        row("3","이화창고","KNOWN","4","","O-3"))));

        InboundXlsxParser.ParseResult result = new InboundXlsxParser().parse(new ByteArrayInputStream(xlsx));

        assertThat(result.rows()).extracting(InboundXlsxParser.InboundRow::warehouseCode)
                .containsExactly("00003", "00003", "2", "2");
        assertThat(result.rows()).extracting(InboundXlsxParser.InboundRow::cleanModel)
                .containsExactly("ABC 가스히트펌프", "1WAY-ABC", "1WAY-XYZ", "KNOWN");
        assertThat(result.rows()).extracting(InboundXlsxParser.InboundRow::quantity)
                .containsExactly(4, 9, 5, 4);
    }

    @Test
    void 짧은_시트_헤더없는_시트_키워드불일치와_시트간_중복을_버린_이유를_보고한다() throws Exception {
        byte[] xlsx = workbook(
                sheet("짧음", List.of(row("a"), row("b"), row("c"), row("d"), row("e"))),
                sheet("헤더없음", List.of(row("","","","","",""), row("","","","",""),
                        row("","","","","",""), row("","","","",""), row("X","Y"),
                        row("","","","","",""), row("1","삼성창고","A","1","1","D-1"))),
                sheet("채택", List.of(row("","","","","",""), row("","","","","",""),
                        row("","","","","",""), row("","","","","",""),
                        row("","","","","",""),
                        row("NO","고객명","모델","주문","물류출고","주문번호"), row("","","","","",""),
                        row("1","외부고객","A","1","1","D-2"), row("2","삼성창고","A","1","1","D-3"))),
                sheet("중복", List.of(row("","","","","",""), row("","","","","",""),
                        row("","","","","",""), row("","","","","",""),
                        row("","","","","",""),
                        row("NO","고객명","모델","주문","물류출고","주문번호"), row("","","","","",""),
                        row("9","삼성창고","A","1","1","D-3"))));

        InboundXlsxParser.ParseResult result = new InboundXlsxParser().parse(new ByteArrayInputStream(xlsx));

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).orderNumber()).isEqualTo("D-3");
        assertThat(result.skippedShortSheets()).containsExactly("짧음");
        assertThat(result.skippedHeaderSheets()).containsExactly("헤더없음");
        assertThat(result.keywordFilteredRows()).isEqualTo(1);
        assertThat(result.deduplicatedRows()).isEqualTo(1);
    }

    private static byte[] workbook(SheetData... sheets) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (SheetData data : sheets) {
                var sheet = book.createSheet(data.name());
                for (int r = 0; r < data.rows().size(); r++) {
                    var row = sheet.createRow(r);
                    String[] values = data.rows().get(r);
                    for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
                }
            }
            book.write(out);
            return out.toByteArray();
        }
    }

    private static SheetData sheet(String name, List<String[]> rows) { return new SheetData(name, rows); }
    private static String[] row(String... values) { return values; }
    private record SheetData(String name, List<String[]> rows) {}
}
