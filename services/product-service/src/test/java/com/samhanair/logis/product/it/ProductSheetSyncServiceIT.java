package com.samhanair.logis.product.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.samhanair.logis.product.client.GoogleSheetsClient;
import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductCategory;
import com.samhanair.logis.product.repository.ProductRepository;
import com.samhanair.logis.product.service.ProductSheetSyncService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

/**
 * ProductSheetSyncService IT — 외부 GoogleSheetsClient {@code @MockBean} 격리
 * (memory feedback_it_mockbean_external_clients.md 가드).
 *
 * <p>테스트 시나리오:
 * <ul>
 *     <li>1) 첫 sync: insert 만 발생, DB row 수 = 시트 row 수</li>
 *     <li>2) 동일 시트 재 sync: rowHash 일치 → unchanged 만 (update X)</li>
 *     <li>3) 시트 row 가격 변경 → update 발생 (releasePrice 갱신)</li>
 *     <li>4) 시트에서 row 사라짐 → soft-delete (isDeleted=true)</li>
 * </ul>
 *
 * <p>본 IT 는 Testcontainers PostgreSQL + ddl-auto=validate + Flyway V1~V4 적용 환경.
 * SchedulerEnabled=false 로 cron 자동 실행 차단.
 */
@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "google.sheets.sheet-id=test-sheet-id",
        "google.sheets.endpoint-override=http://localhost:0"
})
@DirtiesContext
@WithMockUser(username = "test-sync")
class ProductSheetSyncServiceIT extends AbstractPostgresIT {

    @MockBean
    private GoogleSheetsClient sheetsClient;

    @Autowired
    private ProductSheetSyncService syncService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void resetMocks() {
        // 캐시 invalidate (mock 이라 noop 이지만 호출 자체는 검증 가능)
        lenient().doNothing().when(sheetsClient).invalidateCache();
    }

    @Test
    void sync_첫실행_insert_only() throws Exception {
        // given: 홈멀티 시트 1 row 만 mock 응답
        when(sheetsClient.readSheet(anyString(), anyString())).thenReturn(List.of());
        when(sheetsClient.readSheet("test-sheet-id", "홈멀티!A1:Z")).thenReturn(homeMultiRows(
                row("Hi-Multi 4-Way", "AJ040RXH4BC1", "", "1500000", "", "1200000")
        ));

        // when
        ProductSheetSyncService.SyncSummary summary = syncService.syncAll();

        // then
        assertThat(summary.totalInserted).isEqualTo(1);
        assertThat(summary.totalUpdated).isZero();
        assertThat(summary.totalSoftDeleted).isZero();

        Optional<Product> p = productRepository.findByModelCodeAndIsDeletedFalse("AJ040RXH4BC1");
        assertThat(p).isPresent();
        assertThat(p.get().getProductCategory()).isEqualTo(ProductCategory.HOME_MULTI);
        assertThat(p.get().getReleasePrice().toPlainString()).isEqualTo("1500000");
    }

    @Test
    void sync_재실행_rowHash_동일이면_update_없음() throws Exception {
        when(sheetsClient.readSheet(anyString(), anyString())).thenReturn(List.of());
        List<List<Object>> homeMulti = homeMultiRows(
                row("Hi-Multi", "MODEL_HASH_TEST", "", "1000000", "", "900000")
        );
        when(sheetsClient.readSheet("test-sheet-id", "홈멀티!A1:Z")).thenReturn(homeMulti);

        // 1차 sync — insert
        syncService.syncAll();
        // 2차 sync — 동일 데이터
        ProductSheetSyncService.SyncSummary second = syncService.syncAll();

        // hash 일치 → updated=0 (해당 tab 만)
        ProductSheetSyncService.TabSyncResult homeTab = second.byTab.get("홈멀티");
        assertThat(homeTab).isNotNull();
        assertThat(homeTab.updated).isZero();
        assertThat(homeTab.unchanged).isEqualTo(1);
    }

    @Test
    void sync_가격변경시_update_발생() throws Exception {
        when(sheetsClient.readSheet(anyString(), anyString())).thenReturn(List.of());
        when(sheetsClient.readSheet("test-sheet-id", "홈멀티!A1:Z")).thenReturn(homeMultiRows(
                row("Hi-Multi", "PRICE_CHANGE_MODEL", "", "1000000", "", "900000")
        ));
        syncService.syncAll();

        // 가격 변경 시트 응답으로 swap
        when(sheetsClient.readSheet("test-sheet-id", "홈멀티!A1:Z")).thenReturn(homeMultiRows(
                row("Hi-Multi", "PRICE_CHANGE_MODEL", "", "1100000", "", "950000")
        ));
        ProductSheetSyncService.SyncSummary summary = syncService.syncAll();

        ProductSheetSyncService.TabSyncResult homeTab = summary.byTab.get("홈멀티");
        assertThat(homeTab.updated).isEqualTo(1);
        Optional<Product> p = productRepository.findByModelCodeAndIsDeletedFalse("PRICE_CHANGE_MODEL");
        assertThat(p).isPresent();
        assertThat(p.get().getReleasePrice().toPlainString()).isEqualTo("1100000");
    }

    @Test
    void sync_시트에서_사라진_row_softDelete() throws Exception {
        when(sheetsClient.readSheet(anyString(), anyString())).thenReturn(List.of());
        when(sheetsClient.readSheet("test-sheet-id", "홈멀티!A1:Z")).thenReturn(homeMultiRows(
                row("Hi-Multi", "WILL_VANISH", "", "1000000", "", "900000")
        ));
        syncService.syncAll();
        assertThat(productRepository.findByModelCodeAndIsDeletedFalse("WILL_VANISH")).isPresent();

        // 시트에서 해당 row 제거 — 빈 응답
        when(sheetsClient.readSheet("test-sheet-id", "홈멀티!A1:Z")).thenReturn(homeMultiRows());
        ProductSheetSyncService.SyncSummary summary = syncService.syncAll();

        ProductSheetSyncService.TabSyncResult homeTab = summary.byTab.get("홈멀티");
        assertThat(homeTab.softDeleted).isEqualTo(1);
        // soft delete 후 active 조회 X
        assertThat(productRepository.findByModelCodeAndIsDeletedFalse("WILL_VANISH")).isEmpty();
    }

    /** 홈멀티 시트 헤더 + data row 를 ValueRange.values() 형태로 생성. */
    @SafeVarargs
    private static List<List<Object>> homeMultiRows(List<Object>... dataRows) {
        java.util.List<java.util.List<Object>> all = new java.util.ArrayList<>();
        // 헤더 row — col0 에 "품" + "명" 포함 (findHeaderRow 가 인식)
        all.add(List.of("품 명", "모델명", "비고", "출고가", "비고", "납품가"));
        for (List<Object> r : dataRows) all.add(r);
        return all;
    }

    private static List<Object> row(Object... vals) {
        return List.of(vals);
    }
}
