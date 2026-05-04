package com.samhanair.logis.slip.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서명 채널 — Slice C (signature-slice-C Plan §1.1).
 *
 * <ul>
 *   <li>{@link #MOBILE_CANVAS} — 모바일 인수자가 Canvas 로 직접 서명 (현 슬라이스 기본)</li>
 *   <li>{@link #PAPER_SCAN} — 종이 서명 후 스캔 업로드 (Phase 5+ 확장 슬롯)</li>
 * </ul>
 *
 * <p>VARCHAR(20) 컬럼 매핑 — 신규 채널 추가 시 enum + Slip.recordSignature 가드만 갱신.
 */
@Getter
@RequiredArgsConstructor
public enum SignatureChannel {
    MOBILE_CANVAS("모바일 캔버스"),
    PAPER_SCAN("종이 스캔");

    private final String displayName;
}
