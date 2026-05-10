package com.samhanair.logis.partnerorder.editrequest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 거래처 주문 수정 요청 라이프사이클 config — PR-H4b (Phase 12 Step 4b BE-C).
 *
 * <p>application.yml prefix = {@code app.partner-order.edit-request}.
 *
 * <p>{@code expires-hours} (default 24h) — 거래처/영업이 confirmed 주문 수정 요청 발송 후 관리자
 * 무응답 시 본 임계 초과 후 자동 EXPIRED 전환.
 */
@Component
@ConfigurationProperties(prefix = "app.partner-order.edit-request")
@Getter
@Setter
public class PartnerOrderEditRequestProperties {

    /** 자동 만료 시간 (시간 단위, default 24h). */
    private int expiresHours = 24;
}
