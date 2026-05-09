package com.samhanair.logis.arologis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencsv.exceptions.CsvValidationException;
import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import com.samhanair.logis.arologis.repository.RegionDispatchClassificationRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

/**
 * RegionImportService 단위 테스트 — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>4 case — 신규 19 row insert / upsert / reject / BOM 처리.
 */
class RegionImportServiceTest {

    private final RegionDispatchClassificationRepository repository =
            mock(RegionDispatchClassificationRepository.class);

    private final RegionImportService service = new RegionImportService(repository);

    /** UTF-8 BOM prefix (EF BB BF). */
    private static final byte[] BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    @DisplayName("case 1 — 19 row 신규 insert (BOM 없음)")
    void insertAllNew() throws IOException, CsvValidationException {
        when(repository.findByGroupName(any())).thenReturn(Optional.empty());
        when(repository.save(any(RegionDispatchClassification.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        String csv = """
                분류 그룹,검색어
                서울특별시,"송파구, 강남구, 서초구"
                경기동부,"광주, 하남, 이천"
                경기남부,"수원, 성남, 용인"
                """;

        RegionImportService.ImportResult result = service.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.inserted()).isEqualTo(3);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.rejected()).isEmpty();
        verify(repository, times(3)).save(any(RegionDispatchClassification.class));
    }

    @Test
    @DisplayName("case 2 — 기존 행 upsert (group_name 매칭 → updated 카운트)")
    void upsertExisting() throws IOException, CsvValidationException {
        // "서울특별시" 활성 행 존재 시뮬레이션
        RegionDispatchClassification existing = RegionDispatchClassification.of(
                "서울특별시", "기존 키워드", 1);
        when(repository.findByGroupName("서울특별시")).thenReturn(Optional.of(existing));
        when(repository.findByGroupName("경기동부")).thenReturn(Optional.empty());
        when(repository.save(any(RegionDispatchClassification.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        String csv = """
                분류 그룹,검색어
                서울특별시,"송파구, 강남구"
                경기동부,"광주, 하남"
                """;

        RegionImportService.ImportResult result = service.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.inserted()).isEqualTo(1); // 경기동부
        assertThat(result.updated()).isEqualTo(1); // 서울특별시
        assertThat(result.rejected()).isEmpty();
        // 기존 keyword 가 갱신되었는지
        assertThat(existing.getKeywords()).contains("송파구");
    }

    @Test
    @DisplayName("case 3 — reject 시나리오 (분류 그룹 비어있음 / 검색어 비어있음 / 컬럼 부족)")
    void rejectInvalidRows() throws IOException, CsvValidationException {
        when(repository.findByGroupName(any())).thenReturn(Optional.empty());
        when(repository.save(any(RegionDispatchClassification.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        // 1) 정상 — 서울특별시 / 2) 그룹명 비어있음 / 3) 검색어 비어있음 / 4) 정상 — 경기동부
        String csv = "분류 그룹,검색어\n"
                + "서울특별시,\"송파구, 강남구\"\n"
                + ",\"수원, 성남\"\n"
                + "충청북도,\n"
                + "경기동부,\"광주, 하남\"\n";

        RegionImportService.ImportResult result = service.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.rejected()).hasSize(2);
        assertThat(result.rejected().get(0).reason()).contains("분류 그룹");
        assertThat(result.rejected().get(1).reason()).contains("검색어");
    }

    @Test
    @DisplayName("case 4 — UTF-8 BOM 자동 처리 (헤더 첫 컬럼 정상 인식)")
    void handleUtf8Bom() throws IOException, CsvValidationException {
        when(repository.findByGroupName(any())).thenReturn(Optional.empty());
        when(repository.save(any(RegionDispatchClassification.class)))
                .thenAnswer((InvocationOnMock inv) -> inv.getArgument(0));

        String csv = "분류 그룹,검색어\n서울특별시,\"송파구\"\n";
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
        // BOM + CSV 결합
        byte[] withBom = new byte[BOM.length + csvBytes.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(csvBytes, 0, withBom, BOM.length, csvBytes.length);

        InputStream input = new ByteArrayInputStream(withBom);
        RegionImportService.ImportResult result = service.importCsv(input);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.rejected()).isEmpty();
        verify(repository, atLeastOnce()).save(any(RegionDispatchClassification.class));
    }
}
