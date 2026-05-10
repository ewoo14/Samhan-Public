package com.samhanair.logis.shared.realtime.audit;

import java.util.UUID;

/**
 * audit overlay 기록 interface — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>14 service 의 도메인 service 가 본 interface 를 통해 자체 도메인 audit overlay 1행 INSERT
 * + SSE broadcast 를 일관 패턴으로 호출. 도메인별 service ({@code SlipAuditLogService} 등) 가
 * 본 interface 를 구현하거나, 또는 thin facade 로 활용.
 *
 * <p><b>SSE event 표준 형식</b>: {@link AuditEventPayloadBuilder#build} 가 발행. event name
 * = {@code "<domain>:edit"} (예: "slip:edit", "lot:edit", "dispatch:edit") — 도메인 service 가
 * 결정.
 */
public interface AuditLogRecorder {

    /** 표준 SSE event name suffix — 단일 필드 / batch patch 공통. */
    String EVENT_SUFFIX_EDIT = ":edit";

    /** 표준 SSE event name suffix — revert (특정 revision 복원). */
    String EVENT_SUFFIX_REVERTED = ":reverted";

    /**
     * 단일 필드 변경 audit 기록 + SSE broadcast.
     *
     * @param entityId 대상 도메인 entity UUID
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택)
     */
    void recordOverlayPatch(UUID entityId, UUID actorId, String actorName,
                            String actorColor, String fieldName,
                            String oldValue, String newValue);
}
