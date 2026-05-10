package com.samhanair.logis.arologis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.StopStatus;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.domain.VehicleTonnage;
import com.samhanair.logis.arologis.dto.DispatchReconcileResponse;
import com.samhanair.logis.arologis.dto.MismatchedRow;
import com.samhanair.logis.arologis.parser.VendorExcelParser;
import com.samhanair.logis.arologis.repository.DispatchRepository;
import com.samhanair.logis.arologis.repository.VehicleRepository;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * {@link DispatchReconcileService} 단위 테스트 — Phase 10 PR-F1 BE-2.
 *
 * <p>left-join 6 case:
 * <ol>
 *   <li>TRUE — 양쪽 매칭, mismatch 0</li>
 *   <li>FALSE_LEFT — 우리 dispatch 만 존재, vendor 누락</li>
 *   <li>FALSE_RIGHT — vendor 만 존재, 우리 dispatch 누락</li>
 *   <li>빈 결과 — dispatch 0 + vendor 0 → 빈 응답</li>
 *   <li>다중 vendor 통합 — 2 vendor 엑셀 모두 정상 parse + 통합 매칭</li>
 *   <li>partial parse — 1개 vendor 헤더 인식 실패해도 다른 vendor 결과는 유지</li>
 * </ol>
 *
 * <p>+ 인자 검증 case 2 (files null / from &gt; to).
 */
class DispatchReconcileServiceTest {

    private final DispatchRepository dispatchRepository = mock(DispatchRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleStopRepository stopRepository = mock(VehicleStopRepository.class);
    private final VendorExcelParser parser = new VendorExcelParser();

    private final DispatchReconcileService service = new DispatchReconcileService(
            dispatchRepository, vehicleRepository, stopRepository, parser);

    private static final LocalDate D = LocalDate.of(2026, 5, 9);
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO = LocalDate.of(2026, 5, 31);

    private static void setId(Object entity, UUID id) throws Exception {
        Field f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    /** dispatch + vehicle + 단일 stop (slipNo Long) seed. */
    private void seedDispatch(UUID dispatchId, UUID vehicleId, LocalDate date, Long slipNo,
                              String partnerName) throws Exception {
        Dispatch dispatch = Dispatch.of(date, DispatchType.NIGHT, "(test)");
        setId(dispatch, dispatchId);
        Vehicle vehicle = Vehicle.of(dispatchId, 1, VehicleTonnage.TONNAGE_1, "label");
        setId(vehicle, vehicleId);
        VehicleStop stop = VehicleStop.of(vehicleId, 1, "raw", "addr",
                partnerName, slipNo, null, StopStatus.PENDING);
        when(dispatchRepository.findAllByDispatchDateBetweenOrderByDispatchDateAsc(any(), any()))
                .thenReturn(List.of(dispatch));
        when(vehicleRepository.findAllByDispatchIdOrderBySequenceAsc(dispatchId))
                .thenReturn(List.of(vehicle));
        when(stopRepository.findAllByVehicleIdOrderBySequenceAsc(vehicleId))
                .thenReturn(List.of(stop));
    }

    @Test
    @DisplayName("TRUE — 양쪽 매칭 → mismatch 0, matchedCount 1")
    void left_join_TRUE_양쪽_매칭() throws Exception {
        seedDispatch(UUID.randomUUID(), UUID.randomUUID(), D, 214L, "삼한");
        MockMultipartFile file = excel("CJ대한통운.xlsx", new String[][]{
                {"운송장번호", "접수일자", "접수시간", "업체명"},
                {"214", "2026-05-09", "09:30", "삼한"}});

        DispatchReconcileResponse res = service.reconcile(List.of(file), FROM, TO);

        assertThat(res.matchedCount()).isEqualTo(1);
        assertThat(res.mismatchedRows()).isEmpty();
        assertThat(res.dispatchCount()).isEqualTo(1);
        assertThat(res.vendorRowCount()).isEqualTo(1);
        assertThat(res.vendorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FALSE_LEFT — 우리 dispatch 만 존재, vendor 누락")
    void left_join_FALSE_LEFT_vendor_누락() throws Exception {
        seedDispatch(UUID.randomUUID(), UUID.randomUUID(), D, 999L, "우리만");
        MockMultipartFile file = excel("CJ.xlsx", new String[][]{
                {"운송장번호", "접수일자", "업체명"},
                {"100", "2026-05-09", "다른슬립"}});

        DispatchReconcileResponse res = service.reconcile(List.of(file), FROM, TO);

        assertThat(res.matchedCount()).isZero();
        // FALSE_LEFT (우리 dispatch 999) + FALSE_RIGHT (vendor 100) 둘 다
        assertThat(res.mismatchedRows())
                .extracting(MismatchedRow::status)
                .containsExactlyInAnyOrder(
                        MismatchedRow.Status.FALSE_LEFT,
                        MismatchedRow.Status.FALSE_RIGHT);
        MismatchedRow falseLeft = res.mismatchedRows().stream()
                .filter(m -> m.status() == MismatchedRow.Status.FALSE_LEFT)
                .findFirst().orElseThrow();
        assertThat(falseLeft.slipNo()).isEqualTo("999");
        assertThat(falseLeft.dispatchDate()).isEqualTo(D);
        assertThat(falseLeft.reason()).contains("운송사 엑셀 누락");
    }

    @Test
    @DisplayName("FALSE_RIGHT — vendor 만 존재, 우리 dispatch 누락")
    void left_join_FALSE_RIGHT_dispatch_누락() throws IOException {
        // dispatch 0건
        when(dispatchRepository.findAllByDispatchDateBetweenOrderByDispatchDateAsc(any(), any()))
                .thenReturn(List.of());
        MockMultipartFile file = excel("CJ.xlsx", new String[][]{
                {"운송장번호", "접수일자", "업체명"},
                {"R-1", "2026-05-09", "vendor만"}});

        DispatchReconcileResponse res = service.reconcile(List.of(file), FROM, TO);

        assertThat(res.matchedCount()).isZero();
        assertThat(res.mismatchedRows()).hasSize(1);
        MismatchedRow only = res.mismatchedRows().get(0);
        assertThat(only.status()).isEqualTo(MismatchedRow.Status.FALSE_RIGHT);
        assertThat(only.slipNo()).isEqualTo("R-1");
        assertThat(only.vendorName()).isEqualTo("CJ");
        assertThat(only.reason()).contains("자체 dispatch 누락");
    }

    @Test
    @DisplayName("빈 결과 — dispatch 0 + vendor 0 → 빈 응답")
    void left_join_빈_결과() throws IOException {
        when(dispatchRepository.findAllByDispatchDateBetweenOrderByDispatchDateAsc(any(), any()))
                .thenReturn(List.of());
        // vendor 엑셀은 헤더만 있고 데이터 0
        MockMultipartFile file = excel("vendor.xlsx", new String[][]{
                {"운송장번호", "접수일자"}});

        DispatchReconcileResponse res = service.reconcile(List.of(file), FROM, TO);

        assertThat(res.matchedCount()).isZero();
        assertThat(res.mismatchedRows()).isEmpty();
        assertThat(res.dispatchCount()).isZero();
        assertThat(res.vendorRowCount()).isZero();
    }

    @Test
    @DisplayName("다중 vendor 통합 — 2 vendor 엑셀 모두 매칭 + 분류")
    void left_join_다중_vendor_통합() throws Exception {
        seedDispatch(UUID.randomUUID(), UUID.randomUUID(), D, 214L, "삼한");
        MockMultipartFile cj = excel("CJ대한통운.xlsx", new String[][]{
                {"운송장번호", "접수일자", "접수시간"},
                {"214", "2026-05-09", "09:30"}});
        MockMultipartFile lotte = excel("롯데.xlsx", new String[][]{
                {"예약번호", "발송일자", "발송시간"},
                {"L-100", "2026-05-09", "14:00"}});

        DispatchReconcileResponse res = service.reconcile(List.of(cj, lotte), FROM, TO);

        assertThat(res.vendorCount()).isEqualTo(2);
        assertThat(res.vendorRowCount()).isEqualTo(2);
        assertThat(res.matchedCount()).isEqualTo(1); // 214 매칭
        // L-100 = FALSE_RIGHT (vendor 만)
        assertThat(res.mismatchedRows()).hasSize(1);
        assertThat(res.mismatchedRows().get(0).status())
                .isEqualTo(MismatchedRow.Status.FALSE_RIGHT);
        assertThat(res.mismatchedRows().get(0).vendorName()).isEqualTo("롯데");
    }

    @Test
    @DisplayName("partial parse — 1 vendor 헤더 미인식 시 다른 vendor 결과 유지")
    void left_join_partial_parse() throws Exception {
        seedDispatch(UUID.randomUUID(), UUID.randomUUID(), D, 214L, "삼한");
        MockMultipartFile validVendor = excel("CJ.xlsx", new String[][]{
                {"운송장번호", "접수일자"},
                {"214", "2026-05-09"}});
        // 영문 양식 → 헤더 미매칭 → parse 결과 빈 list (예외 X)
        MockMultipartFile unknownVendor = excel("Unknown.xlsx", new String[][]{
                {"WaybillNo", "PickupDate"},
                {"X-1", "2026-05-09"}});

        DispatchReconcileResponse res = service.reconcile(
                List.of(validVendor, unknownVendor), FROM, TO);

        assertThat(res.vendorCount()).isEqualTo(1); // valid 1, unknown 은 빈 list 라 미카운트
        assertThat(res.vendorRowCount()).isEqualTo(1);
        assertThat(res.matchedCount()).isEqualTo(1);
        assertThat(res.mismatchedRows()).isEmpty();
    }

    // ---------- 인자 검증 ----------

    @Test
    @DisplayName("files null/empty → BusinessException INVALID_INPUT")
    void reconcile_files_empty() {
        assertThatThrownBy(() -> service.reconcile(List.of(), FROM, TO))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("from > to → BusinessException INVALID_INPUT")
    void reconcile_from_after_to() throws IOException {
        MockMultipartFile file = excel("v.xlsx", new String[][]{{"운송장번호", "접수일자"}});
        assertThatThrownBy(() -> service.reconcile(List.of(file), TO, FROM))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("extractVendorName — 파일명 → vendor 식별자")
    void extractVendorName_split() {
        assertThat(service.extractVendorName("CJ대한통운_2026-05.xlsx")).isEqualTo("CJ대한통운");
        assertThat(service.extractVendorName("롯데.xlsx")).isEqualTo("롯데");
        assertThat(service.extractVendorName("한진 (5월).xlsx")).isEqualTo("한진");
        assertThat(service.extractVendorName(null)).isEqualTo("(unknown)");
    }

    // ---------- helper ----------

    private MockMultipartFile excel(String fileName, String[][] rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("vendor");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                String[] cells = rows[r];
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c]);
                }
            }
            wb.write(out);
            return new MockMultipartFile("files", fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}
