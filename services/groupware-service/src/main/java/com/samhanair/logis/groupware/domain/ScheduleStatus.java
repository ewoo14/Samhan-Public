package com.samhanair.logis.groupware.domain;

/**
 * 일정 상태.
 *
 * <ul>
 *   <li>{@link #DRAFT} — 임시 저장 (참여자 미확정 등). 알림 미발송.</li>
 *   <li>{@link #CONFIRMED} — 확정 일정. 참여자 알림 / 캘린더 노출 대상.</li>
 *   <li>{@link #CANCELLED} — 취소된 일정. 조회는 가능 (감사 목적), 신규 알림 X.</li>
 * </ul>
 */
public enum ScheduleStatus {

    DRAFT,
    CONFIRMED,
    CANCELLED
}
