package com.samhanair.logis.slip.delivery.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SMS 게이트웨이 환경 설정 — Aligo (한국 SMS 게이트웨이) 매핑.
 *
 * <p>Aligo 인증 모델 (Solapi HMAC-SHA256 vs 단순 form 인증으로 교체):
 * <ul>
 *   <li>apiKey — Aligo console 에서 발급 (key 파라미터)</li>
 *   <li>userId — Aligo 계정 ID (user_id 파라미터)</li>
 *   <li>senderPhone — 사전 등록된 발신번호 (sender 파라미터)</li>
 *   <li>baseUrl — {@code https://apis.aligo.in} (기본값)</li>
 * </ul>
 *
 * @param vendor 게이트웨이 벤더 (현재 "aligo" 만)
 * @param apiKey Aligo API key
 * @param userId Aligo 계정 ID
 * @param senderPhone 발신 번호 (Aligo 사전 등록 발신번호 의무)
 * @param baseUrl Aligo base URL (기본 {@code https://apis.aligo.in})
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        String vendor,
        String apiKey,
        String userId,
        String senderPhone,
        String baseUrl) {
}
