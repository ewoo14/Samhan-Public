package com.samhanair.logis.accounting.service;

import com.samhanair.logis.accounting.domain.TaxInvoice;
import com.samhanair.logis.accounting.domain.TaxInvoiceLine;
import com.samhanair.logis.accounting.domain.TaxInvoiceStatus;
import com.samhanair.logis.accounting.repository.TaxInvoiceRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈택스 일괄업로드 양식 export service (PR-E2 BE-A11).
 *
 * <p>legacy GAS 5번 "계산서일괄등록양식 생성" — 자체 발행 ISSUED 세금계산서를 홈택스 표준
 * 컬럼 (작성일/공급자등록번호/공급받는자등록번호/품목/공급가액/세액/합계/비고) 로 변환.
 *
 * <p>실제 홈택스 spec snapshot 은 사용자 첨부 의무 (Plan R7) — 본 단계는 표준 컬럼 placeholder.
 * 100건 초과 시 sheet 분할 ("Sheet1", "Sheet2", ...).
 *
 * <p>read-only — 외부 client 의존 없음. POI XSSFWorkbook 사용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HometaxExportService {

    /** 홈택스 sheet 1개당 행 수 제한 (헤더 제외 100건). */
    public static final int ROWS_PER_SHEET = 100;

    private static final String[] HEADER_COLUMNS = {
            "작성일",
            "공급자등록번호",
            "공급받는자등록번호",
            "공급받는자상호",
            "품목",
            "규격",
            "수량",
            "단가",
            "공급가액",
            "세액",
            "합계",
            "비고"
    };

    private final TaxInvoiceRepository taxInvoiceRepository;

    /**
     * 기간 ISSUED 세금계산서 → 홈택스 일괄 업로드 xlsx binary 반환.
     *
     * @param from supplyDate 시작 (inclusive)
     * @param to supplyDate 종료 (inclusive)
     * @return xlsx binary (workbook bytes)
     * @throws BusinessException(INTERNAL_ERROR) workbook 직렬화 실패
     */
    public byte[] export(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to 는 필수입니다");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to 는 from 이후여야 합니다");
        }
        List<TaxInvoice> issued = taxInvoiceRepository
                .findIssuedInRange(TaxInvoiceStatus.ISSUED, from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            // 라인 단위 row 로 직렬화 → 100 건마다 sheet 분할
            int totalRowCount = 0;
            for (TaxInvoice ti : issued) {
                totalRowCount += Math.max(1, ti.getLines().size());
            }

            if (totalRowCount == 0) {
                // 빈 결과여도 헤더 sheet 1장 (운영자 인지)
                writeHeaderRow(workbook.createSheet("Sheet1"), headerStyle);
            } else {
                int sheetIndex = 1;
                Sheet currentSheet = workbook.createSheet("Sheet" + sheetIndex);
                writeHeaderRow(currentSheet, headerStyle);
                int rowInSheet = 0;
                for (TaxInvoice ti : issued) {
                    List<TaxInvoiceLine> lines = ti.getLines();
                    if (lines.isEmpty()) {
                        if (rowInSheet >= ROWS_PER_SHEET) {
                            sheetIndex++;
                            currentSheet = workbook.createSheet("Sheet" + sheetIndex);
                            writeHeaderRow(currentSheet, headerStyle);
                            rowInSheet = 0;
                        }
                        writeInvoiceHeaderOnly(currentSheet, rowInSheet + 1, ti);
                        rowInSheet++;
                    } else {
                        for (TaxInvoiceLine line : lines) {
                            if (rowInSheet >= ROWS_PER_SHEET) {
                                sheetIndex++;
                                currentSheet = workbook.createSheet("Sheet" + sheetIndex);
                                writeHeaderRow(currentSheet, headerStyle);
                                rowInSheet = 0;
                            }
                            writeInvoiceLine(currentSheet, rowInSheet + 1, ti, line);
                            rowInSheet++;
                        }
                    }
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "홈택스 양식 workbook 직렬화 실패: " + ex.getMessage(), ex);
        }
    }

    /** 첫 row 에 표준 컬럼 헤더 기록. */
    private void writeHeaderRow(Sheet sheet, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADER_COLUMNS.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(HEADER_COLUMNS[i]);
            c.setCellStyle(style);
        }
    }

    /** 라인이 없는 세금계산서 — 헤더 정보만 한 row 기록 (수량/단가/공급/세액 = 0). */
    private void writeInvoiceHeaderOnly(Sheet sheet, int rowIdx, TaxInvoice ti) {
        Row r = sheet.createRow(rowIdx);
        r.createCell(0).setCellValue(ti.getSupplyDate().toString());
        r.createCell(1).setCellValue("");
        r.createCell(2).setCellValue(safeText(ti.getPartnerBusinessNo()));
        r.createCell(3).setCellValue(safeText(ti.getPartnerName()));
        r.createCell(4).setCellValue("");
        r.createCell(5).setCellValue("");
        r.createCell(6).setCellValue(0d);
        r.createCell(7).setCellValue(0d);
        r.createCell(8).setCellValue(ti.getSupplyAmount().doubleValue());
        r.createCell(9).setCellValue(ti.getVatAmount().doubleValue());
        r.createCell(10).setCellValue(ti.getTotalAmount().doubleValue());
        r.createCell(11).setCellValue(safeText(ti.getDescription()));
    }

    /** 라인 1건을 row 1개로 기록. 헤더 정보는 매 row 반복 기록 (홈택스 양식 표준). */
    private void writeInvoiceLine(Sheet sheet, int rowIdx, TaxInvoice ti, TaxInvoiceLine line) {
        Row r = sheet.createRow(rowIdx);
        r.createCell(0).setCellValue(ti.getSupplyDate().toString());
        r.createCell(1).setCellValue("");  // 공급자등록번호 — 회사 고정 (별도 설정 시 주입)
        r.createCell(2).setCellValue(safeText(ti.getPartnerBusinessNo()));
        r.createCell(3).setCellValue(safeText(ti.getPartnerName()));
        r.createCell(4).setCellValue(safeText(line.getItemName()));
        r.createCell(5).setCellValue(safeText(line.getSpec()));
        r.createCell(6).setCellValue(line.getQuantity().doubleValue());
        r.createCell(7).setCellValue(line.getUnitPrice().doubleValue());
        r.createCell(8).setCellValue(line.getSupplyAmount().doubleValue());
        r.createCell(9).setCellValue(line.getVatAmount().doubleValue());
        r.createCell(10).setCellValue(line.getSupplyAmount().add(line.getVatAmount()).doubleValue());
        r.createCell(11).setCellValue(safeText(line.getMemo()));
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private static String safeText(String v) {
        return v == null ? "" : v;
    }
}
