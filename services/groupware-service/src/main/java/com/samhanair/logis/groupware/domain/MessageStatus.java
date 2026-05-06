package com.samhanair.logis.groupware.domain;

/**
 * 메신저 단건 읽음 상태.
 *
 * <ul>
 *   <li>{@link #UNREAD} — 수신자가 아직 열람하지 않음. 미열람 카운트 / 알림 배지 대상.</li>
 *   <li>{@link #READ} — 수신자가 본문 열람 완료 (`PUT /admin/groupware/messages/{id}/read` 호출).</li>
 * </ul>
 */
public enum MessageStatus {

    UNREAD,
    READ
}
