package com.samhanair.logis.shared.realtime.audit;

/**
 * audit overlay 변경 1건의 record 컨테이너 — PR-H4a (Phase 12 Step 4a).
 *
 * <p>다중 필드 batch 입력 + SSE payload 일관 schema 의 단위.
 *
 * @param fieldName 필드 식별자 (≤50자)
 * @param oldValue 이전 값 (null 가능)
 * @param newValue 새 값 (null 가능, 둘 다 null 은 audit factory 가 거부)
 */
public record ChangeEntry(String fieldName, String oldValue, String newValue) {
}
