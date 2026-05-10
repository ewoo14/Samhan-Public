package com.samhanair.logis.shared.realtime.lock;

/**
 * 도메인 잠금 정책 가드 — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>14 service 의 도메인 service 가 mutation 직전 본 가드로 status 별 분기. 위반 시
 * {@link LockedException} 던짐 (HTTP 409 CONFLICT 자동 매핑).
 *
 * <p><b>단순 정책 결정 책임만</b> — APPROVED 요청 lookup 과 consume 은 도메인별
 * {@link com.samhanair.logis.shared.realtime.editrequest.EditRequestService} 가 담당.
 *
 * <p><b>표준 사용 패턴</b>:
 * <pre>
 *   Slip slip = ...;
 *   editLockGuard.guardCanEdit(slip.getStatus(), policy);     // 자유 → 그냥 진행
 *                                                            // FULLY_LOCKED → throw
 *                                                            // LOCKED_REQUIRES_APPROVAL → throw if no APPROVED
 *   slip.applyOverlayPatch(...);
 *   editRequestService.consumeApprovalIfAny(slip.getId());
 * </pre>
 */
public interface EditLockGuard {

    /**
     * mutation (수정) 가능 여부 가드 — status 별 분기.
     *
     * @param status 도메인 entity 의 현재 status
     * @param policy 도메인별 정책
     * @param hasActiveApproval LOCKED_REQUIRES_APPROVAL 시점 호출자가 lookup 한 APPROVED 요청 존재 여부
     * @param <T> 도메인 status enum type
     * @throws LockedException 위반 시
     */
    <T> void guardCanEdit(T status, EditLockPolicy<T> policy, boolean hasActiveApproval);

    /**
     * mutation (삭제) 가능 여부 가드 — 정책 분기는 edit 와 동일 (DELETE 도 동일 정책).
     *
     * @param status 도메인 entity 의 현재 status
     * @param policy 도메인별 정책
     * @param hasActiveApproval LOCKED_REQUIRES_APPROVAL 시점 호출자가 lookup 한 APPROVED 요청 존재 여부
     * @param <T> 도메인 status enum type
     * @throws LockedException 위반 시
     */
    <T> void guardCanDelete(T status, EditLockPolicy<T> policy, boolean hasActiveApproval);
}
