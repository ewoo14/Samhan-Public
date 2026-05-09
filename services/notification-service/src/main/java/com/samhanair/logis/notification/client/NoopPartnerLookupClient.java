package com.samhanair.logis.notification.client;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * {@link PartnerLookupClient} 의 fail-soft 기본 구현 — BE-E 의 by-name endpoint 머지 전까지의 placeholder.
 *
 * <p>{@link ConditionalOnMissingBean} 가드로 실제 RestClient 기반 구현체가 등록되면 본 빈은 자동 비활성화.
 * 본 placeholder 는 항상 {@link Optional#empty()} 반환 — import 시도 시 모든 row 가 reject 로 누적되어
 * 운영자가 BE-E 의존성 미충족을 즉시 인지할 수 있도록 한다 (silent success 방지).
 *
 * <p>본 PR-D Part 2-3 단독 build/test 통과를 위해 존재. 실 production 운영 시점에는 RestClient impl 필수.
 */
@Configuration
public class NoopPartnerLookupClient {

    private static final Logger log = LoggerFactory.getLogger(NoopPartnerLookupClient.class);

    @Bean
    @ConditionalOnMissingBean(PartnerLookupClient.class)
    public PartnerLookupClient noopPartnerLookupClient() {
        log.warn("PartnerLookupClient 실 구현체 미등록 — Noop placeholder 활성. "
                + "BE-E 의 GET /api/v1/partners/by-name endpoint 머지 후 RestClient impl 등록 필요.");
        return businessName -> Optional.empty();
    }
}
