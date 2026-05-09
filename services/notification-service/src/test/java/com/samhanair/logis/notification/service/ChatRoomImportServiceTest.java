package com.samhanair.logis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.notification.client.PartnerLookupClient;
import com.samhanair.logis.notification.domain.MappingSource;
import com.samhanair.logis.notification.domain.PartnerChatRoomMapping;
import com.samhanair.logis.notification.dto.ChatRoomImportResult;
import com.samhanair.logis.notification.repository.PartnerChatRoomMappingRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link ChatRoomImportService} 단위 테스트 — 5 시나리오.
 *
 * <ol>
 *   <li>정상 CSV 3 row 적재 (BOM 포함)</li>
 *   <li>lookup miss → reject 누적</li>
 *   <li>한국어 datetime 파싱 ("2026년 4월 26일 오전 7:34" → LocalDateTime)</li>
 *   <li>기존 매핑 재import → updated 카운트 + snapshot 갱신</li>
 *   <li>필수 컬럼 누락 → reject</li>
 * </ol>
 */
class ChatRoomImportServiceTest {

    private PartnerChatRoomMappingRepository repository;
    private PartnerLookupClient lookupClient;
    private ChatRoomImportService service;

    @BeforeEach
    void setUp() {
        repository = mock(PartnerChatRoomMappingRepository.class);
        lookupClient = mock(PartnerLookupClient.class);
        service = new ChatRoomImportService(repository, lookupClient);

        // 기본: 모든 매핑 미존재 (insert 분기)
        lenient().when(repository.findByPartnerCodeAndChatRoomName(anyString(), anyString()))
                .thenReturn(Optional.empty());
        // 저장은 입력 그대로 반환
        lenient().when(repository.save(any(PartnerChatRoomMapping.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("정상 CSV 3 row — BOM 포함, 모든 row insert")
    void importCsv_3rows_allInserted() throws IOException {
        when(lookupClient.findPartnerCodeByName("에어디자이너 주식회사")).thenReturn(Optional.of("P-001"));
        when(lookupClient.findPartnerCodeByName("주식회사 제이시스템")).thenReturn(Optional.of("P-002"));
        when(lookupClient.findPartnerCodeByName("공기를디자인하는사람들 주식회사")).thenReturn(Optional.of("P-003"));

        // UTF-8 BOM (EF BB BF) + header + 3 rows
        String csv = "﻿이카운트 사업자명,카톡방,생성 일시\n"
                + "에어디자이너 주식회사,에어디자이너(구 지에스) 발주방,2026년 4월 26일 오전 7:34\n"
                + "주식회사 제이시스템,제이시스템 발주방,2026년 4월 26일 오전 7:34\n"
                + "공기를디자인하는사람들 주식회사,에어디자이너(구 지에스) 발주방,2026년 4월 26일 오전 7:34\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ChatRoomImportResult result = service.importCsv(stream);

        assertThat(result.inserted()).isEqualTo(3);
        assertThat(result.updated()).isZero();
        assertThat(result.rejected()).isEmpty();
        verify(repository, times(3)).save(any(PartnerChatRoomMapping.class));
    }

    @Test
    @DisplayName("lookup miss → reject 누적 (정상 row 는 insert)")
    void importCsv_lookupMiss_rejected() throws IOException {
        when(lookupClient.findPartnerCodeByName("정상 주식회사")).thenReturn(Optional.of("P-OK"));
        when(lookupClient.findPartnerCodeByName("미등록 주식회사")).thenReturn(Optional.empty());

        String csv = "﻿이카운트 사업자명,카톡방,생성 일시\n"
                + "정상 주식회사,정상 발주방,2026년 4월 26일 오전 7:34\n"
                + "미등록 주식회사,미등록 발주방,2026년 4월 26일 오전 7:34\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ChatRoomImportResult result = service.importCsv(stream);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).rowNumber()).isEqualTo(2);
        assertThat(result.rejected().get(0).businessName()).isEqualTo("미등록 주식회사");
        assertThat(result.rejected().get(0).reason()).contains("partner_code lookup miss");
    }

    @Test
    @DisplayName("한국어 datetime 파싱 — 오전/오후 + 1자리 시간")
    void importCsv_koreanDateTime_parsed() throws IOException {
        when(lookupClient.findPartnerCodeByName(anyString())).thenReturn(Optional.of("P-001"));

        String csv = "﻿이카운트 사업자명,카톡방,생성 일시\n"
                + "테스트,테스트 발주방,2026년 4월 26일 오전 7:34\n"
                + "테스트2,테스트2 발주방,2026년 12월 31일 오후 11:59\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        service.importCsv(stream);

        ArgumentCaptor<PartnerChatRoomMapping> captor = ArgumentCaptor.forClass(PartnerChatRoomMapping.class);
        verify(repository, times(2)).save(captor.capture());

        // 첫 row — 오전 7:34
        LocalDateTime first = captor.getAllValues().get(0).getNotionCreatedAt();
        assertThat(first).isEqualTo(LocalDateTime.of(2026, Month.APRIL, 26, 7, 34));

        // 둘째 row — 오후 11:59
        LocalDateTime second = captor.getAllValues().get(1).getNotionCreatedAt();
        assertThat(second).isEqualTo(LocalDateTime.of(2026, Month.DECEMBER, 31, 23, 59));
    }

    @Test
    @DisplayName("기존 매핑 재import → updated + snapshot 갱신")
    void importCsv_existingMapping_updates() throws IOException {
        when(lookupClient.findPartnerCodeByName("거래처A")).thenReturn(Optional.of("P-A"));

        // 기존 매핑 존재 fixture
        PartnerChatRoomMapping existing = PartnerChatRoomMapping.fromNotionImport(
                "P-A", "거래처A 구이름", "공통 발주방", null);
        when(repository.findByPartnerCodeAndChatRoomName("P-A", "공통 발주방"))
                .thenReturn(Optional.of(existing));

        String csv = "﻿이카운트 사업자명,카톡방,생성 일시\n"
                + "거래처A,공통 발주방,2026년 4월 26일 오전 7:34\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ChatRoomImportResult result = service.importCsv(stream);

        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(existing.getPartnerBusinessNameSnapshot()).isEqualTo("거래처A");
        assertThat(existing.getSource()).isEqualTo(MappingSource.NOTION_IMPORT);
    }

    @Test
    @DisplayName("필수 컬럼 누락 → reject (사업자명 빈값)")
    void importCsv_missingRequiredColumn_rejected() throws IOException {
        String csv = "﻿이카운트 사업자명,카톡방,생성 일시\n"
                + ",빈사업자명 발주방,2026년 4월 26일 오전 7:34\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ChatRoomImportResult result = service.importCsv(stream);

        assertThat(result.inserted()).isZero();
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).reason()).contains("필수 컬럼 누락");
    }

    @Test
    @DisplayName("Notion datetime 파싱 실패 — null 처리 (매핑은 정상 적재)")
    void importCsv_invalidDateTime_storedAsNull() throws IOException {
        when(lookupClient.findPartnerCodeByName(anyString())).thenReturn(Optional.of("P-X"));

        String csv = "﻿이카운트 사업자명,카톡방,생성 일시\n"
                + "거래처X,X 발주방,쓰레기 datetime\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ChatRoomImportResult result = service.importCsv(stream);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.rejected()).isEmpty();

        ArgumentCaptor<PartnerChatRoomMapping> captor = ArgumentCaptor.forClass(PartnerChatRoomMapping.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNotionCreatedAt()).isNull();
    }

    @Test
    @DisplayName("BOM 없는 CSV — 정상 처리")
    void importCsv_noBom_works() throws IOException {
        when(lookupClient.findPartnerCodeByName(anyString())).thenReturn(Optional.of("P-Y"));

        // BOM 없음
        String csv = "이카운트 사업자명,카톡방,생성 일시\n"
                + "거래처Y,Y 발주방,2026년 4월 26일 오전 7:34\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ChatRoomImportResult result = service.importCsv(stream);

        assertThat(result.inserted()).isEqualTo(1);
    }

    @Test
    @DisplayName("Notion 컬럼 순서 무관 — header-aware (생성 일시 / 카톡방 swap)")
    void importCsv_columnOrder_swapped_works() throws IOException {
        when(lookupClient.findPartnerCodeByName(anyString())).thenReturn(Optional.of("P-Z"));

        // _all.csv 형식 — 카톡방 / 생성 일시 위치 swap
        String csv = "﻿이카운트 사업자명,생성 일시,카톡방\n"
                + "거래처Z,2026년 4월 26일 오전 7:34,Z 발주방\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ChatRoomImportResult result = service.importCsv(stream);

        assertThat(result.inserted()).isEqualTo(1);

        ArgumentCaptor<PartnerChatRoomMapping> captor = ArgumentCaptor.forClass(PartnerChatRoomMapping.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChatRoomName()).isEqualTo("Z 발주방");
        assertThat(captor.getValue().getNotionCreatedAt())
                .isEqualTo(LocalDateTime.of(2026, Month.APRIL, 26, 7, 34));
    }

}
