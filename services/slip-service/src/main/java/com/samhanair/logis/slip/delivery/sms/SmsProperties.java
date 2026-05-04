package com.samhanair.logis.slip.delivery.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS 게이트웨이 환경 설정 — Plan §6 의 {@code app.sms.*} 키 매핑.
 *
 * @param vendor 게이트웨이 벤더 (현재 "solapi" 만)
 * @param apiKey Solapi API key
 * @param apiSecret Solapi API secret
 * @param senderPhone 발신 번호 (Solapi 사전 등록 발신번호 의무)
 * @param baseUrl Solapi base URL (기본 {@code https://api.solapi.com})
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        String vendor,
        String apiKey,
        String apiSecret,
        String senderPhone,
        String baseUrl) {
}
