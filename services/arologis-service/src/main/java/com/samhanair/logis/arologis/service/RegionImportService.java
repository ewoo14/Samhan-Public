package com.samhanair.logis.arologis.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import com.samhanair.logis.arologis.repository.RegionDispatchClassificationRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가배차 지역 분류 CSV import service — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>Samhan Public 프로그램 native 이식 — 노션 직접 통신 X. 기존 노션 export CSV
 * (UTF-8 BOM, RFC4180 quoted) 를 우리 DB 에 native upsert.
 *
 * <p>입력 컬럼 (BOM prefix 제거 후): {@code 분류 그룹}, {@code 검색어}.
 *
 * <p>Upsert 규칙 — group_name 기준:
 * <ul>
 *   <li>활성 행 존재 → keywords + sortOrder 갱신 (UPDATED)</li>
 *   <li>미존재 → 신규 insert (INSERTED)</li>
 *   <li>group_name 또는 검색어 비어있음 → REJECTED (사유 보고)</li>
 * </ul>
 *
 * <p>BOM 처리: 입력 stream 첫 3 byte 가 EF BB BF 일 시 skip.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionImportService {

    private final RegionDispatchClassificationRepository repository;

    /**
     * CSV 일괄 import. UTF-8 BOM 자동 처리 + RFC4180 quoted field 지원.
     *
     * @param input CSV InputStream (multipart 업로드)
     * @return import 결과 (inserted / updated / rejected 카운트 + reject 사유 목록)
     * @throws IOException 입력 stream IO 실패
     * @throws CsvValidationException CSV 포맷 오류
     */
    @Transactional
    public ImportResult importCsv(InputStream input) throws IOException, CsvValidationException {
        if (input == null) {
            throw new IllegalArgumentException("CSV InputStream 필수");
        }
        InputStream bomStripped = stripUtf8Bom(input);
        BufferedReader reader = new BufferedReader(new InputStreamReader(bomStripped, StandardCharsets.UTF_8));

        int inserted = 0;
        int updated = 0;
        List<RejectedRow> rejected = new ArrayList<>();
        int sortOrderCounter = 0;

        try (CSVReader csv = new CSVReader(reader)) {
            String[] header = csv.readNext();
            if (header == null || header.length < 2) {
                throw new IllegalArgumentException("CSV 헤더 누락 — 최소 2개 컬럼 필요 (분류 그룹, 검색어)");
            }
            // 헤더 검증 — BOM 제거 후 trim
            String col1 = stripBom(header[0]).trim();
            String col2 = header[1].trim();
            if (!col1.contains("분류") && !col1.equalsIgnoreCase("group_name")) {
                throw new IllegalArgumentException("CSV 헤더 오류 — 첫 컬럼 '분류 그룹' 필요, 실제: " + col1);
            }
            if (!col2.contains("검색") && !col2.equalsIgnoreCase("keywords")) {
                throw new IllegalArgumentException("CSV 헤더 오류 — 둘째 컬럼 '검색어' 필요, 실제: " + col2);
            }

            int rowNumber = 1; // 헤더 = 1
            String[] row;
            while ((row = csv.readNext()) != null) {
                rowNumber++;
                if (row.length < 2) {
                    rejected.add(new RejectedRow(rowNumber, String.join(",", row),
                            "컬럼 부족 — 2개 미만"));
                    continue;
                }
                String groupName = row[0] == null ? "" : row[0].trim();
                String keywords = row[1] == null ? "" : row[1].trim();
                if (groupName.isBlank()) {
                    rejected.add(new RejectedRow(rowNumber, String.join(",", row),
                            "분류 그룹 비어있음"));
                    continue;
                }
                if (keywords.isBlank()) {
                    rejected.add(new RejectedRow(rowNumber, String.join(",", row),
                            "검색어 비어있음"));
                    continue;
                }

                sortOrderCounter++;
                final int currentSort = sortOrderCounter;
                boolean isUpdate = repository.findByGroupName(groupName).map(existing -> {
                    existing.updateKeywords(keywords);
                    existing.updateSortOrder(currentSort);
                    return true;
                }).orElseGet(() -> {
                    repository.save(RegionDispatchClassification.of(groupName, keywords, currentSort));
                    return false;
                });
                if (isUpdate) {
                    updated++;
                } else {
                    inserted++;
                }
            }
        }

        log.info("RegionImportService — inserted={}, updated={}, rejected={}",
                inserted, updated, rejected.size());
        return new ImportResult(inserted, updated, rejected);
    }

    /** UTF-8 BOM (EF BB BF) 자동 skip. */
    private InputStream stripUtf8Bom(InputStream input) throws IOException {
        java.io.PushbackInputStream pb = new java.io.PushbackInputStream(input, 3);
        byte[] bom = new byte[3];
        int read = pb.read(bom, 0, 3);
        if (read >= 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF) {
            // BOM 존재 → drop. 이후 byte 만 noticeable
            return pb;
        }
        // BOM 없음 → 모두 unread
        if (read > 0) {
            pb.unread(bom, 0, read);
        }
        return pb;
    }

    /** 단일 문자열 BOM prefix (U+FEFF) 제거 — 헤더 첫 컬럼 안전망. */
    private String stripBom(String s) {
        if (s == null) {
            return "";
        }
        if (!s.isEmpty() && s.charAt(0) == '﻿') {
            return s.substring(1);
        }
        return s;
    }

    /**
     * CSV import 결과 응답 DTO.
     *
     * @param inserted 신규 insert 행수
     * @param updated upsert (group_name 매칭) 행수
     * @param rejected 거부된 행 (사유 + 원본 데이터)
     */
    public record ImportResult(int inserted, int updated, List<RejectedRow> rejected) {

        public int totalProcessed() {
            return inserted + updated + rejected.size();
        }
    }

    /**
     * 거부된 CSV 행 보고.
     *
     * @param rowNumber CSV 내 행 번호 (헤더 = 1, 데이터 = 2부터)
     * @param rawData 원본 행 텍스트 (콤마 join)
     * @param reason 거부 사유 (한국어)
     */
    public record RejectedRow(int rowNumber, String rawData, String reason) {}
}
