package com.samhanair.logis.approval;

/**
 * 결재 단계의 결재자 식별 방식.
 *
 * <ul>
 *   <li>{@link #USER} — 특정 사원 1명 직접 지정(approverUserId). 그룹웨어 자유형 결재.</li>
 *   <li>{@link #GROUP} — 권한 그룹(approverGroupId 표시 + requiredPageCode enforce).
 *       그룹의 결재 page-code 를 계승한 사원이면 누구나 승인(A2 배선).</li>
 *   <li>{@link #CREATOR} — 전표 작성자(createdBy) 본인 단계(A4 배선).</li>
 * </ul>
 *
 * <p>A1 은 {@link #USER} 만 실배선하고 GROUP/CREATOR 는 컬럼만 선반영한다.
 */
public enum StepType {
    CREATOR,
    GROUP,
    USER
}
