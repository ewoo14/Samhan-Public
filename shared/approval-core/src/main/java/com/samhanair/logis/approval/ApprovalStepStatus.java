package com.samhanair.logis.approval;

/**
 * 결재 chain 의 단일 단계 상태.
 *
 * <ul>
 *   <li>{@link #PENDING} — 처리 대기.</li>
 *   <li>{@link #APPROVED} — 본 단계 승인 (chain 후속 단계로 전이).</li>
 *   <li>{@link #REJECTED} — 본 단계 반려 (chain 종료, 후속 단계 처리되지 않음).</li>
 * </ul>
 */
public enum ApprovalStepStatus {

    PENDING,
    APPROVED,
    REJECTED
}
