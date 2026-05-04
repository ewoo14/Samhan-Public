package com.samhanair.logis.slip.delivery.sms;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로컬/테스트용 SMS 게이트웨이 — 실제 호출 대신 logging 만 (Plan §6).
 * Solapi 비용 회피 + Docker 미가용 IT 시나리오에서 활용.
 * 항상 성공 (가짜 messageId 반환).
 */
public class MockSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSmsGateway.class);

    @Override
    public SmsResult sendSms(String phone, String message) {
        String fakeId = "mock-" + UUID.randomUUID();
        log.info("[MockSmsGateway] phone={} length={} messageId={} body=[{}]",
                phone, message == null ? 0 : message.length(), fakeId, message);
        return SmsResult.success(fakeId);
    }
}
