package com.samhanair.logis.partnerauth.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * sms-service 클라이언트 (Phase 7+ 예정).
 *
 * <p><b>현 PR (M2 W2):</b> 발송 요청을 로그로 큐잉만 한다 (실 발송 X).
 * IT 에서는 본 클라이언트를 {@code @MockBean} 으로 격리한다
 * (memory feedback_it_mockbean_external_clients.md).
 */
@Component
public class SmsClient {

    private static final Logger log = LoggerFactory.getLogger(SmsClient.class);

    /**
     * 임시 비밀번호 SMS 발송 큐잉 — 202 Accepted 시점.
     *
     * @param mobileNo 마스킹 전 원본 휴대폰 번호
     * @param tempPassword 임시 평문 (SMS 본문에 포함)
     */
    public void enqueueTempPassword(String mobileNo, String tempPassword) {
        // Phase 7 sms-service 구축 후 RestClient 또는 Kafka publish 로 교체.
        log.info("SmsClient.enqueueTempPassword: mobileNo masked, length={} (queued, no real send)",
                tempPassword.length());
    }
}
