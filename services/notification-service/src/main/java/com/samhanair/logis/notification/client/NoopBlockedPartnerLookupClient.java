package com.samhanair.logis.notification.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * {@link BlockedPartnerLookupClient} 의 빈 구현체 — partner-service 미연결 환경 / IT 격리용.
 *
 * <p>본 placeholder 는 항상 false 반환 (= 모든 거래처 발송 허용). 실제 구현체 (RestClient 기반) 는
 * 후속 슬라이스에서 추가하며 {@link ConditionalOnMissingBean} 으로 자동 비활성화.
 *
 * <p>운영 환경에서는 본 placeholder 를 비활성하고 RestClient 구현체로 대체할 것.
 */
@Component
@ConditionalOnMissingBean(value = BlockedPartnerLookupClient.class,
        ignored = NoopBlockedPartnerLookupClient.class)
public class NoopBlockedPartnerLookupClient implements BlockedPartnerLookupClient {

    private static final Logger log = LoggerFactory.getLogger(NoopBlockedPartnerLookupClient.class);

    @Override
    public boolean isBlocked(String partnerCode) {
        log.debug("NoopBlockedPartnerLookupClient.isBlocked — partnerCode={} (placeholder, false)", partnerCode);
        return false;
    }
}
