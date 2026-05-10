package com.samhanair.logis.shared.realtime.editrequest;

import java.util.Optional;
import java.util.UUID;

/**
 * 수정/삭제 요청 service interface — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>14 service 가 자체 도메인의 edit-request 라이프사이클을 본 interface 로 외부 노출. 도메인별
 * 구현체 ({@code SlipEditRequestService} 등) 가 본 interface 를 implements.
 *
 * <p><b>SSE event 표준 형식</b>:
 * <ul>
 *   <li>{@code "<domain>:edit-request:created"} — 요청 생성 시 broadcast</li>
 *   <li>{@code "<domain>:edit-request:decided"} — 수락/거절/만료 시 broadcast</li>
 * </ul>
 *
 * <p><b>UUID 비공개</b>: 모든 응답/SSE payload 의 actorId/requesterId 는 FE 색상 hash 결정성 용도.
 * 사용자 화면 표시는 actorName/requesterName 만 사용.
 *
 * <p><b>도메인 service 책임 (interface 외 호출)</b>:
 * <ul>
 *   <li>request — 신규 PENDING 요청 생성 + SSE broadcast + notification 발송</li>
 *   <li>approve / reject — 결정 + SSE broadcast + 요청자 알림</li>
 *   <li>{@link #findActiveApproval} — slip mutation 가드</li>
 *   <li>{@link #consumeApproval} — APPROVED 1회 소진</li>
 *   <li>scheduled expire — 자동 만료</li>
 * </ul>
 */
public interface EditRequestService {

    /** SSE event name suffix — 요청 생성. */
    String EVENT_SUFFIX_CREATED = ":edit-request:created";

    /** SSE event name suffix — 수락/거절/만료. */
    String EVENT_SUFFIX_DECIDED = ":edit-request:decided";

    /**
     * 도메인 entity mutation 가드용 — 해당 entity 의 활성 APPROVED 요청 1건 lookup. 0건 → mutation
     * 차단, 1건 → mutation 진행 후 즉시 {@link #consumeApproval}.
     *
     * @param entityId 대상 도메인 entity UUID
     * @return APPROVED 요청 ID (있으면) 또는 empty
     */
    Optional<UUID> findActiveApproval(UUID entityId);

    /**
     * APPROVED 요청 1회 소진 — 도메인 mutation 직후 호출. soft-delete 패턴으로 다음 lookup 부터 0건 반환.
     *
     * @param requestId 대상 요청 (typically {@link #findActiveApproval} 결과)
     * @param consumerUserId mutation 수행자 user-id (audit)
     */
    void consumeApproval(UUID requestId, String consumerUserId);
}
