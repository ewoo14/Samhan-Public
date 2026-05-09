package com.samhanair.logis.slip.attachment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 슬립 첨부 유형 — P1-8 (Stage 4) 모바일 사진 첨부.
 *
 * <p>매뉴얼 출처: {@code docs/manual/04-모바일/04-사진-첨부.md} §2.
 *
 * <ul>
 *   <li>{@link #DELIVERY} — 배송 사진 (mobile-staff driver mode 정차 도착 시 화물 / 인수 현장 촬영)</li>
 *   <li>{@link #INSPECTION} — 검수 사진 (창고 INSPECTING 단계 picking 결과 증빙)</li>
 *   <li>{@link #ESTIMATE} — 견적 현장 사진 (mobile-staff estimate mode 견적 답사 현장)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum SlipAttachmentType {

    DELIVERY("배송사진"),
    INSPECTION("검수사진"),
    ESTIMATE("견적현장사진");

    private final String displayName;
}
