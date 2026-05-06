package com.samhanair.logis.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Aligo SMS 게이트웨이 환경 설정 — 한국 SMS 게이트웨이 통합.
 *
 * <p>인증 모델:
 * <ul>
 *   <li>{@code key} — Aligo console 발급 API key</li>
 *   <li>{@code userid} — Aligo 계정 ID</li>
 *   <li>{@code sender} — 사전 등록 발신번호</li>
 *   <li>{@code apiUrl} — {@code https://apis.aligo.in/send/} (기본)</li>
 * </ul>
 *
 * <p>본 슬라이스에서 모든 secret 은 placeholder ({@code CHANGE_ME_LOCAL_ONLY}) 시 외부 호출 skip
 * + stub-success — local dev / dev-default 호환.
 */
@Data
@ConfigurationProperties(prefix = "samhan.notification.aligo")
public class AligoProperties {

    /** Aligo API base URL (기본 {@code https://apis.aligo.in/send/}). */
    private String apiUrl;

    /** Aligo API key. */
    private String key;

    /** Aligo 계정 ID. */
    private String userid;

    /** 사전 등록된 발신번호. */
    private String sender;
}
