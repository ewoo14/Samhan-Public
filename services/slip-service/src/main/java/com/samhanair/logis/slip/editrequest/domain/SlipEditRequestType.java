package com.samhanair.logis.slip.editrequest.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 슬립 수정/삭제 요청 종류 — PR-H3 (Phase 12 Step 3).
 *
 * <p>사용자 명시 잠금 정책 분기:
 * <ul>
 *   <li>{@link #EDIT} — 헤더/라인/audit overlay 필드 수정 요청. APPROVED 후 작성자가 1회 수정 가능.</li>
 *   <li>{@link #DELETE} — 슬립 자체 soft-delete 요청. APPROVED 후 작성자가 삭제 가능.</li>
 * </ul>
 *
 * <p>본 enum 은 도메인 + DB 컬럼 매핑용. UUID 비공개 가드와 무관 — 사용자 화면에 displayName 노출.
 */
@Getter
@RequiredArgsConstructor
public enum SlipEditRequestType {

    EDIT("수정"),
    DELETE("삭제");

    private final String displayName;
}
