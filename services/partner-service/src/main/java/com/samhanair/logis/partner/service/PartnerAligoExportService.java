package com.samhanair.logis.partner.service;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import com.samhanair.logis.partner.repository.BlockedPartnerRepository;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 10 PR-F1 BE-1 — partner-service 의 활성 거래처를 알리고 SF벤더 그룹 CSV (UTF-8 BOM)
 * 형식으로 export.
 *
 * <p><b>Samhan Public 이식 — legacy GAS 9번 "알리고 자동 업로드" 의 자체 구현.</b> Legacy 흐름은
 * <i>이카운트 거래처리스트 + KT 공유주소록 → Notion 거래처마스터 → 알리고 SF벤더 그룹 CSV (수동
 * 업로드)</i> 였으나, 본 BE-1 은 우리 자체 partner-service 의 거래처 마스터를 진실의 원천으로 삼아
 * 알리고 주소록 업로드용 CSV 를 즉시 발급한다 (수동 다운로드 → 후속 BE-2 는 native API sync 로 격상).
 *
 * <h2>CSV 컬럼 (legacy aligo SF벤더 그룹 호환)</h2>
 * <ol>
 *   <li>{@code 그룹명} — {@link Partner#getPartnerGroup1()} (없으면 {@code "기본"})</li>
 *   <li>{@code 이름} — {@link Partner#getName()} (사업자상호)</li>
 *   <li>{@code 휴대폰} — {@link #pickPhone(Partner)} 우선순위 (mobile → phone), 한국 표준 정규화</li>
 *   <li>{@code 비고} — {@code "[partnerCode]"} (운영자 추적용 — UUID 비공개 가드 일관)</li>
 * </ol>
 *
 * <h2>필터 정책 — 사용자 명시 (strikethrough)</h2>
 * <ul>
 *   <li>활성 거래처만 ({@link PartnerStatus#ACTIVE}) — SUSPENDED / TERMINATED 제외</li>
 *   <li>차단 거래처 ({@link com.samhanair.logis.partner.domain.BlockedPartner}) 제외</li>
 *   <li>휴대폰 정규화 결과가 비어있는 row 제외 (알리고 발송 무의미)</li>
 *   <li><b>신용정보 / 전자소송 / 폐업 관련 필터는 본 단계에서 적용 X</b> — 사용자 명시 strikethrough.
 *       (legacy GAS 의 별도 검증 단계는 partner status 가 사실상 동일 역할 수행)</li>
 * </ul>
 *
 * <h2>전화번호 정규화</h2>
 * <ul>
 *   <li>국제전화 prefix ({@code +82}) → {@code 0} 로 치환</li>
 *   <li>모든 비숫자 문자 제거 (하이픈 / 공백 / 괄호 / dot)</li>
 *   <li>{@code 010 / 011 / 016 / 017 / 018 / 019} 로 시작 + 길이 10~11 → 정상</li>
 *   <li>외 형식 (지역번호 02-, 070-, 050X 등) → empty 반환 (휴대폰 컬럼 부적합 → row 제외)</li>
 * </ul>
 *
 * <h2>UTF-8 BOM</h2>
 * <p>Microsoft Excel / 알리고 업로드 양쪽이 한국어 인식을 위해 BOM 요구. CSV header 앞에
 * {@code 0xEF 0xBB 0xBF} 3 바이트 prepend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerAligoExportService {

    /** UTF-8 BOM — 알리고 / Excel 한국어 헤더 인식. */
    private static final byte[] UTF8_BOM = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    /** CSV 헤더 — legacy aligo SF벤더 그룹 4 컬럼. */
    static final String CSV_HEADER = "그룹명,이름,휴대폰,비고";

    /** 그룹명 미공급 시 default. */
    static final String DEFAULT_GROUP = "기본";

    /** 한국 휴대폰 prefix. */
    private static final Set<String> MOBILE_PREFIXES = Set.of(
            "010", "011", "016", "017", "018", "019");

    private final PartnerRepository partnerRepository;
    private final BlockedPartnerRepository blockedPartnerRepository;

    /**
     * 알리고 SF벤더 그룹 CSV (UTF-8 BOM) 생성.
     *
     * <p>filter chain: ACTIVE → BLOCKED 제외 → 휴대폰 정규화 OK. 전체 partner 모집단을 한 번 로드
     * 한 후 BLOCKED set 으로 제외 (N+1 회피). Samhan Public 규모 (~100 거래처) 에서 메모리 부담 X.
     *
     * @return UTF-8 BOM + CSV body 의 byte array (controller 가 binary 응답으로 그대로 송신)
     */
    @Transactional(readOnly = true)
    public byte[] exportAligoCsv() {
        // 1) 활성 거래처 전체 + 차단 set (N+1 회피)
        List<Partner> activePartners = partnerRepository.findAllByStatus(
                PartnerStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        Set<String> blockedCodes = blockedPartnerRepository.findAll().stream()
                .map(b -> b.getPartnerCode())
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

        // 2) CSV body 생성
        StringBuilder body = new StringBuilder();
        body.append(CSV_HEADER).append("\r\n");

        int emitted = 0;
        int skippedBlocked = 0;
        int skippedNoPhone = 0;
        for (Partner p : activePartners) {
            if (blockedCodes.contains(p.getPartnerCode())) {
                skippedBlocked++;
                continue;
            }
            String phone = normalizeMobilePhone(pickPhone(p));
            if (phone.isEmpty()) {
                skippedNoPhone++;
                continue;
            }
            body.append(csvField(resolveGroup(p))).append(',')
                    .append(csvField(p.getName())).append(',')
                    .append(csvField(phone)).append(',')
                    .append(csvField("[" + p.getPartnerCode() + "]"))
                    .append("\r\n");
            emitted++;
        }

        log.info("AligoCsvExport — emitted={} skippedBlocked={} skippedNoPhone={} totalActive={}",
                emitted, skippedBlocked, skippedNoPhone, activePartners.size());

        // 3) UTF-8 BOM + body
        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[UTF8_BOM.length + bodyBytes.length];
        System.arraycopy(UTF8_BOM, 0, full, 0, UTF8_BOM.length);
        System.arraycopy(bodyBytes, 0, full, UTF8_BOM.length, bodyBytes.length);
        return full;
    }

    /**
     * 휴대폰 우선순위 — {@link Partner#getMobile()} → {@link Partner#getPhone()}.
     *
     * <p>이카운트 27 필드 보강 후 mobile 컬럼이 정확하지만 legacy seed 일부는 phone 만 보유 — fallback.
     */
    String pickPhone(Partner p) {
        if (p.getMobile() != null && !p.getMobile().isBlank()) {
            return p.getMobile();
        }
        return p.getPhone();
    }

    /** 그룹명 — partnerGroup1 우선, 미공급 시 {@link #DEFAULT_GROUP}. */
    String resolveGroup(Partner p) {
        if (p.getPartnerGroup1() != null && !p.getPartnerGroup1().isBlank()) {
            return p.getPartnerGroup1().trim();
        }
        return DEFAULT_GROUP;
    }

    /**
     * 한국 휴대폰 정규화 — 비숫자 제거 + 국제 prefix 치환 + 휴대폰 prefix 검증.
     *
     * <p>visibility = package-private — 단위 테스트 직접 호출 의도.
     *
     * @param raw 원시 전화번호 (nullable / "+82-10-1234-5678" / "010 1234 5678" 등)
     * @return 정규화된 휴대폰 (예: "01012345678") 또는 휴대폰 prefix 미적합 시 empty
     */
    String normalizeMobilePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        // 국제 prefix +82 → 0 (예: "+82-10-1234-5678" → "0-10-1234-5678" → "01012345678")
        if (trimmed.startsWith("+82")) {
            trimmed = "0" + trimmed.substring(3);
        } else if (trimmed.startsWith("0082")) {
            trimmed = "0" + trimmed.substring(4);
        }
        // 비숫자 제거
        StringBuilder digits = new StringBuilder(trimmed.length());
        for (char c : trimmed.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        String normalized = digits.toString();
        if (normalized.length() < 10 || normalized.length() > 11) {
            return "";
        }
        String prefix = normalized.substring(0, 3);
        if (!MOBILE_PREFIXES.contains(prefix)) {
            return "";
        }
        return normalized;
    }

    /**
     * CSV 필드 escaping — 콤마 / 따옴표 / 개행 포함 시 따옴표 wrap + 내부 따옴표 두 번 escape (RFC 4180).
     */
    String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
