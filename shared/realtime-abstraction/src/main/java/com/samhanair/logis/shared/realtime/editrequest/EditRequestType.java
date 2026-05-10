package com.samhanair.logis.shared.realtime.editrequest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 수정/삭제 요청 종류 — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>14 service 공통:
 * <ul>
 *   <li>{@link #EDIT} — 헤더/라인/audit overlay 필드 수정 요청. APPROVED 후 작성자가 1회 수정 가능.</li>
 *   <li>{@link #DELETE} — entity 자체 soft-delete 요청. APPROVED 후 작성자가 삭제 가능.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum EditRequestType {

    EDIT("수정"),
    DELETE("삭제");

    private final String displayName;
}
