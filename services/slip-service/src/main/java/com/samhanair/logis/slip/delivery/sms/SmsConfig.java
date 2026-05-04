package com.samhanair.logis.slip.delivery.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * SMS 게이트웨이 프로파일 분기 (Plan §6).
 *
 * <ul>
 *   <li>{@code pgsql} 프로파일 — {@link SolapiSmsGateway} 활성 (운영 / staging)</li>
 *   <li>나머지 (local, test, default) — {@link MockSmsGateway} 활성 (logging only)</li>
 * </ul>
 *
 * <p>IT 에서는 별도 {@code @MockBean SmsGateway} 로 격리 (memory
 * {@code feedback_it_mockbean_external_clients.md}).
 */
@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    /** PgSQL (운영/staging) 프로파일 — Solapi 실제 호출. */
    @Bean
    @Profile("pgsql")
    public SmsGateway solapiSmsGateway(SmsProperties props) {
        return new SolapiSmsGateway(props);
    }

    /** 그 외 프로파일 (local, test, default) — Mock 게이트웨이. */
    @Bean
    @Profile("!pgsql")
    public SmsGateway mockSmsGateway() {
        return new MockSmsGateway();
    }
}
