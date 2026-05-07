package com.samhanair.logis.slip.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서명 발급 source — Phase 10 W10-4 (PR #99) 신규.
 *
 * <p>기존 V5/V6 까지 slip-service 의 서명은 항상 SMS/Aligo 링크 기반 공개 모바일 endpoint 로
 * 등록되었으나, W10-3 부터 arologis 모바일 어플 driver 가 직접 정차 단위로 서명을 캡처할 수 있게 되어
 * source 구분이 필요해졌다. {@link SignatureChannel} 은 입력 매체(MOBILE_CANVAS / PAPER_SCAN) 인 반면,
 * 본 enum 은 발급 경로(LINK / APP) 를 식별한다.
 *
 * <ul>
 *   <li>{@link #LINK} — 기존 SMS/Aligo 링크 + 공개 모바일 endpoint POST /public/batches/.../signature
 *       발급. V5 이전 데이터의 backfill 기본값 (V10 DEFAULT 'LINK').</li>
 *   <li>{@link #APP} — arologis 모바일 어플 driver 가 정차 완료 시 캡처 + arologis-service 가
 *       slip-service /internal/slips/{slipId}/signatures 로 전파.</li>
 * </ul>
 *
 * <p>VARCHAR(20) 컬럼 매핑 (slips.signature_source / slips.driver_signature_source /
 * slip_signature_audit.signature_source 3개 컬럼).
 */
@Getter
@RequiredArgsConstructor
public enum SignatureSource {
    LINK("링크 발급"),
    APP("어플 캡처");

    private final String displayName;
}
