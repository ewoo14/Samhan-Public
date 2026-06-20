package com.samhanair.logis.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사원 서명 입력 채널 - C1a (사원 서명 등록 에픽 4.1).
 *
 * <ul>
 *   <li>{@link #MOBILE_CANVAS} - 모바일 핸드오프로 손그림 서명 (공개 웹앱 제출)</li>
 *   <li>{@link #UPLOAD} - 관리자 desktop 에서 이미지 업로드</li>
 * </ul>
 *
 * <p>VARCHAR(20) 컬럼 매핑 + DB CHECK 제약 IN 목록과 정확히 일치해야 한다
 * (CHECK(signature_channel IN ('MOBILE_CANVAS','UPLOAD'))). slip-service 의
 * 동명 enum(PAPER_SCAN 포함)과 도메인이 다르므로 혼용 금지.
 */
@Getter
@RequiredArgsConstructor
public enum SignatureChannel {
    MOBILE_CANVAS("모바일 캔버스"),
    UPLOAD("이미지 업로드");

    private final String displayName;
}
