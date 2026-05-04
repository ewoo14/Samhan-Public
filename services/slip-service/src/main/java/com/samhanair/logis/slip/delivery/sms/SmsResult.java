package com.samhanair.logis.slip.delivery.sms;

/**
 * SMS 발송 결과 — Plan §6. 성공/실패 여부 + Solapi messageId + 에러 메시지.
 *
 * @param ok 발송 성공 여부 — false 이면 errorMessage 참조
 * @param messageId Solapi 응답 messageId (성공 시), 실패 시 null 가능
 * @param errorMessage 실패 사유 (성공 시 null)
 */
public record SmsResult(boolean ok, String messageId, String errorMessage) {

    public static SmsResult success(String messageId) {
        return new SmsResult(true, messageId, null);
    }

    public static SmsResult failure(String errorMessage) {
        return new SmsResult(false, null, errorMessage);
    }
}
