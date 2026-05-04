package com.samhanair.logis.slip.delivery.sms;

/**
 * SMS 발송 추상화 — Slice B (notification-slice-B Plan §6).
 * 운영 환경은 {@link SolapiSmsGateway}, 로컬/테스트 환경은 {@link MockSmsGateway} 가
 * 프로파일 분기로 자동 활성. 향후 Phase 5 Notification Service 슬라이스에서 분리 가능.
 */
public interface SmsGateway {

    /**
     * SMS 1건 발송.
     *
     * @param phone 수신 번호 (한국 휴대폰 패턴 {@code 010-XXXX-XXXX} 또는 {@code 01012345678})
     * @param message 본문 (한글 80자 / EUC-KR 90바이트 LMS 임계 검증은 Solapi 가 자동 처리)
     * @return 발송 결과 (성공 시 ok=true + messageId, 실패 시 ok=false + errorMessage)
     */
    SmsResult sendSms(String phone, String message);
}
