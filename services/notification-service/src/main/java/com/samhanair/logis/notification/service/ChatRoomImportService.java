package com.samhanair.logis.notification.service;

import com.opencsv.CSVReaderHeaderAware;
import com.samhanair.logis.notification.client.PartnerLookupClient;
import com.samhanair.logis.notification.domain.PartnerChatRoomMapping;
import com.samhanair.logis.notification.dto.ChatRoomImportResult;
import com.samhanair.logis.notification.dto.ChatRoomImportResult.RejectedRow;
import com.samhanair.logis.notification.repository.PartnerChatRoomMappingRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notion CSV import 서비스 (PR-D Part 2-3).
 *
 * <p>Notion DB "단톡방리스트" export CSV (111 row) 를 partner_chat_room_mappings 테이블로 적재.
 * 컬럼 헤더 = {@code "이카운트 사업자명"}, {@code "카톡방"}, {@code "생성 일시"} (순서 무관 — header-aware).
 *
 * <p>처리 절차:
 * <ol>
 *   <li>UTF-8 BOM 제거 ({@link BOMInputStream})</li>
 *   <li>{@link CSVReaderHeaderAware} 로 row 단위 Map 추출</li>
 *   <li>{@code 이카운트 사업자명} → {@link PartnerLookupClient#findPartnerCodeByName(String)} lookup</li>
 *   <li>match 시 활성 (partner_code, chat_room_name) 중복 체크 → insert 또는 snapshot 갱신</li>
 *   <li>miss 시 reject 누적 (row 번호 + 사업자명 + reason)</li>
 *   <li>{@code 생성 일시} 한국어 포맷 ("2026년 4월 26일 오전 7:34") 파싱 → {@link LocalDateTime}</li>
 * </ol>
 *
 * <p>본 서비스는 한 row 의 lookup 실패가 다른 row 처리를 방해하지 않도록 row 단위 try/catch.
 * 전체 트랜잭션 = 1 — 단, reject 가 있어도 정상 row 는 commit (운영자가 reject 보고서 보고 수기 처리).
 */
@Service
@RequiredArgsConstructor
public class ChatRoomImportService {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomImportService.class);

    private static final String COL_BUSINESS_NAME = "이카운트 사업자명";
    private static final String COL_CHAT_ROOM = "카톡방";
    private static final String COL_NOTION_CREATED_AT = "생성 일시";

    /**
     * Notion 한국어 datetime 포맷터 — "2026년 4월 26일 오전 7:34".
     *
     * <p>패턴 = {@code yyyy년 M월 d일 a h:mm} (Locale.KOREAN). M/d 는 1자리 허용, h 는 12-hour, a 는 오전/오후.
     * 분이 1자리인 경우 ("오전 7:4") 도 fallback 으로 처리.
     */
    private static final DateTimeFormatter NOTION_DATETIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy년 M월 d일 a h:mm")
            .toFormatter(Locale.KOREAN);

    private final PartnerChatRoomMappingRepository repository;
    private final PartnerLookupClient partnerLookupClient;

    /**
     * CSV InputStream 을 import.
     *
     * @param csvStream Notion export CSV (UTF-8, BOM 허용)
     * @return inserted / updated / rejected 종합 결과
     * @throws IOException CSV 읽기 실패 (전체 import 실패)
     */
    @Transactional
    public ChatRoomImportResult importCsv(InputStream csvStream) throws IOException {
        int inserted = 0;
        int updated = 0;
        List<RejectedRow> rejected = new ArrayList<>();

        try (InputStream bomFree = BOMInputStream.builder().setInputStream(csvStream).get();
             Reader reader = new BufferedReader(new InputStreamReader(bomFree, StandardCharsets.UTF_8));
             CSVReaderHeaderAware csv = new CSVReaderHeaderAware(reader)) {

            int rowNumber = 0;
            Map<String, String> values;
            while ((values = csv.readMap()) != null) {
                rowNumber++;
                String businessName = trimToNull(values.get(COL_BUSINESS_NAME));
                String chatRoomName = trimToNull(values.get(COL_CHAT_ROOM));
                String createdAtRaw = trimToNull(values.get(COL_NOTION_CREATED_AT));

                if (businessName == null || chatRoomName == null) {
                    rejected.add(new RejectedRow(rowNumber, businessName, chatRoomName,
                            "필수 컬럼 누락 (이카운트 사업자명 / 카톡방)"));
                    continue;
                }

                Optional<String> partnerCodeOpt;
                try {
                    partnerCodeOpt = partnerLookupClient.findPartnerCodeByName(businessName);
                } catch (Exception e) {
                    log.warn("partner-service lookup 실패 row={} name={} : {}",
                            rowNumber, businessName, e.getMessage());
                    rejected.add(new RejectedRow(rowNumber, businessName, chatRoomName,
                            "partner-service lookup 호출 실패: " + e.getMessage()));
                    continue;
                }

                if (partnerCodeOpt.isEmpty()) {
                    rejected.add(new RejectedRow(rowNumber, businessName, chatRoomName,
                            "partner_code lookup miss (신규 거래처 또는 사업자명 오타 추정)"));
                    continue;
                }
                String partnerCode = partnerCodeOpt.get();

                LocalDateTime notionCreatedAt = null;
                if (createdAtRaw != null) {
                    try {
                        notionCreatedAt = LocalDateTime.parse(createdAtRaw, NOTION_DATETIME);
                    } catch (Exception e) {
                        // 파싱 실패는 reject 가 아닌 null 처리 (감사 필드 손실은 허용, 매핑 자체는 유효)
                        log.warn("Notion 생성 일시 파싱 실패 row={} value='{}' : {}",
                                rowNumber, createdAtRaw, e.getMessage());
                    }
                }

                Optional<PartnerChatRoomMapping> existing =
                        repository.findByPartnerCodeAndChatRoomName(partnerCode, chatRoomName);
                if (existing.isPresent()) {
                    // snapshot 사업자명만 갱신 (재import 시 partner-service 측 리네임 반영)
                    existing.get().updateBusinessNameSnapshot(businessName);
                    repository.save(existing.get());
                    updated++;
                } else {
                    PartnerChatRoomMapping entity = PartnerChatRoomMapping.fromNotionImport(
                            partnerCode, businessName, chatRoomName, notionCreatedAt);
                    repository.save(entity);
                    inserted++;
                }
            }
        } catch (com.opencsv.exceptions.CsvValidationException ex) {
            throw new IOException("CSV 파싱 실패: " + ex.getMessage(), ex);
        }

        log.info("CHAT 단톡방 매핑 import 완료 — inserted={} updated={} rejected={}",
                inserted, updated, rejected.size());
        return new ChatRoomImportResult(inserted, updated, rejected);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
