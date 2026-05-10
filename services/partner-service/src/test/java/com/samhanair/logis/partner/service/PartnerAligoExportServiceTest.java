package com.samhanair.logis.partner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.samhanair.logis.partner.domain.BlockedPartner;
import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import com.samhanair.logis.partner.repository.BlockedPartnerRepository;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Phase 10 PR-F1 BE-1 — {@link PartnerAligoExportService} 단위 테스트.
 *
 * <p>커버 5 case:
 * <ol>
 *   <li>정상 export (활성 거래처 → CSV row + UTF-8 BOM)</li>
 *   <li>차단 거래처 제외 (BlockedPartner partner_code 매칭 row skip)</li>
 *   <li>전화번호 정규화 (mobile 우선, +82 → 0, 비숫자 제거, 휴대폰 prefix 검증)</li>
 *   <li>신용정보/전자소송/폐업 필터 미적용 — 사용자 명시 strikethrough (status filter 만 적용)</li>
 *   <li>CSV BOM 검증 (응답 byte 첫 3 바이트 = 0xEF 0xBB 0xBF)</li>
 * </ol>
 *
 * <p>JPA / Spring 부팅 없음 (한글 경로 + JDK 17 호환). Partner 의 mobile / phone / partnerGroup1
 * 은 reflection 으로 직접 set — 도메인 factory 가 일부 필드만 노출하기 때문.
 */
@ExtendWith(MockitoExtension.class)
class PartnerAligoExportServiceTest {

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private BlockedPartnerRepository blockedPartnerRepository;

    @InjectMocks
    private PartnerAligoExportService service;

    private static Partner partner(String code, String name, String mobile, String phone,
                                   String group1) {
        Partner p = Partner.register(code, "999-88-77777", name, "주소", phone, BigDecimal.ZERO);
        try {
            if (mobile != null) {
                Field mobileField = Partner.class.getDeclaredField("mobile");
                mobileField.setAccessible(true);
                mobileField.set(p, mobile);
            }
            if (group1 != null) {
                Field groupField = Partner.class.getDeclaredField("partnerGroup1");
                groupField.setAccessible(true);
                groupField.set(p, group1);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return p;
    }

    private void stubPartners(List<Partner> active) {
        Page<Partner> page = new PageImpl<>(active);
        when(partnerRepository.findAllByStatus(any(PartnerStatus.class), any(Pageable.class)))
                .thenReturn(page);
    }

    private void stubNoBlocked() {
        when(blockedPartnerRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void exportAligoCsv_normal_emitsRowsWithUtf8BomAndHeader() {
        // 정상 활성 거래처 2건 — group1 / mobile 정상.
        Partner p1 = partner("P-2026-0001", "(주)에어뱅크", "010-1234-5678", null, "VIP거래처");
        Partner p2 = partner("P-2026-0002", "주식회사 삼성이엔지", "01098765432", null, "일반거래처");
        stubPartners(List.of(p1, p2));
        stubNoBlocked();

        byte[] csv = service.exportAligoCsv();
        String body = stripBom(csv);

        assertThat(body).startsWith("그룹명,이름,휴대폰,비고\r\n");
        assertThat(body).contains("VIP거래처,(주)에어뱅크,01012345678,[P-2026-0001]");
        assertThat(body).contains("일반거래처,주식회사 삼성이엔지,01098765432,[P-2026-0002]");
    }

    @Test
    void exportAligoCsv_blockedPartner_excludedFromOutput() {
        Partner p1 = partner("P-2026-0001", "(주)에어뱅크", "01011112222", null, null);
        Partner p2 = partner("P-2026-0002", "차단대상거래처", "01033334444", null, null);
        stubPartners(List.of(p1, p2));

        BlockedPartner blocked = BlockedPartner.create("P-2026-0002", "차단대상거래처",
                null, LocalDateTime.now(), "MANUAL");
        when(blockedPartnerRepository.findAll()).thenReturn(List.of(blocked));

        byte[] csv = service.exportAligoCsv();
        String body = stripBom(csv);

        assertThat(body).contains("(주)에어뱅크");
        assertThat(body).doesNotContain("차단대상거래처");
        assertThat(body).doesNotContain("[P-2026-0002]");
    }

    @Test
    void normalizeMobilePhone_variousFormats_returnsCanonical() {
        assertThat(service.normalizeMobilePhone("010-1234-5678")).isEqualTo("01012345678");
        assertThat(service.normalizeMobilePhone("010 1234 5678")).isEqualTo("01012345678");
        assertThat(service.normalizeMobilePhone("(010)1234.5678")).isEqualTo("01012345678");
        assertThat(service.normalizeMobilePhone("+82-10-1234-5678")).isEqualTo("01012345678");
        assertThat(service.normalizeMobilePhone("00821012345678")).isEqualTo("01012345678");

        // 휴대폰 prefix 미적합 (지역번호 / 인터넷전화) — empty 반환
        assertThat(service.normalizeMobilePhone("02-1234-5678")).isEmpty();
        assertThat(service.normalizeMobilePhone("070-1234-5678")).isEmpty();
        assertThat(service.normalizeMobilePhone("050-1234-5678")).isEmpty();

        // 길이 부적합
        assertThat(service.normalizeMobilePhone("010-12-3")).isEmpty();
        assertThat(service.normalizeMobilePhone("01012345678901")).isEmpty();

        // null / blank
        assertThat(service.normalizeMobilePhone(null)).isEmpty();
        assertThat(service.normalizeMobilePhone("   ")).isEmpty();
    }

    @Test
    void exportAligoCsv_phoneFallback_usedWhenMobileBlank() {
        // mobile 미공급 → phone fallback. phone 도 휴대폰 형식이면 사용, 지역번호면 row 제외.
        Partner mobileless = partner("P-2026-0010", "휴대폰만보유", null, "010-9999-8888", null);
        Partner localOnly = partner("P-2026-0011", "지역번호만", null, "02-1234-5678", null);
        stubPartners(List.of(mobileless, localOnly));
        stubNoBlocked();

        byte[] csv = service.exportAligoCsv();
        String body = stripBom(csv);

        assertThat(body).contains("기본,휴대폰만보유,01099998888,[P-2026-0010]");
        // 지역번호만 보유 → 휴대폰 정규화 결과 empty → row skip
        assertThat(body).doesNotContain("지역번호만");
    }

    @Test
    void exportAligoCsv_userNotedStrikethroughFilters_areNotApplied() {
        // 사용자 명시 strikethrough — 신용정보/전자소송/폐업 필터는 본 단계 적용 X.
        // partnerGroup1 / partnerGroup2 가 어떤 값이든 ACTIVE 면 모두 export.
        Partner credit = partner("P-2026-0020", "신용정보보유거래처", "01011112222", null, "신용정보");
        Partner lawsuit = partner("P-2026-0021", "전자소송중거래처", "01033334444", null, "전자소송");
        Partner closed = partner("P-2026-0022", "폐업의심거래처", "01055556666", null, "폐업의심");
        stubPartners(List.of(credit, lawsuit, closed));
        stubNoBlocked();

        byte[] csv = service.exportAligoCsv();
        String body = stripBom(csv);

        // 3건 모두 export 되어야 함 (본 단계는 status + blocked 만 필터)
        assertThat(body).contains("신용정보보유거래처");
        assertThat(body).contains("전자소송중거래처");
        assertThat(body).contains("폐업의심거래처");
    }

    @Test
    void exportAligoCsv_utf8BomPresent_atFirstThreeBytes() {
        // status filter 가 활성만 가져오므로 빈 결과여도 BOM + header 는 항상 emit.
        lenient().when(partnerRepository.findAllByStatus(any(PartnerStatus.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        stubNoBlocked();

        byte[] csv = service.exportAligoCsv();

        assertThat(csv.length).isGreaterThanOrEqualTo(3);
        assertThat(csv[0] & 0xFF).isEqualTo(0xEF);
        assertThat(csv[1] & 0xFF).isEqualTo(0xBB);
        assertThat(csv[2] & 0xFF).isEqualTo(0xBF);
        // 헤더가 BOM 직후에 위치해야 함
        String afterBom = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(afterBom).startsWith("그룹명,이름,휴대폰,비고\r\n");
    }

    @Test
    void csvField_specialCharacters_quotedAndEscaped() {
        assertThat(service.csvField("일반텍스트")).isEqualTo("일반텍스트");
        assertThat(service.csvField("콤마,있음")).isEqualTo("\"콤마,있음\"");
        assertThat(service.csvField("따옴표\"있음")).isEqualTo("\"따옴표\"\"있음\"");
        assertThat(service.csvField("개행\n있음")).isEqualTo("\"개행\n있음\"");
        assertThat(service.csvField(null)).isEmpty();
    }

    /** 응답 byte 의 BOM 제거 후 string 반환 (UTF-8). */
    private static String stripBom(byte[] csv) {
        if (csv.length >= 3 && csv[0] == (byte) 0xEF && csv[1] == (byte) 0xBB && csv[2] == (byte) 0xBF) {
            return new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        }
        return new String(csv, StandardCharsets.UTF_8);
    }
}
