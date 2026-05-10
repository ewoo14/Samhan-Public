package com.samhanair.logis.slip.editrequest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 슬립 수정 요청 라이프사이클 config — PR-H3 (Phase 12 Step 3).
 *
 * <p>application.yml prefix = {@code app.slip.edit-request}.
 *
 * <p>{@code expires-hours} (default 24h) — 영업직원이 발행 후 슬립 수정 요청 발송 → 관리자 무응답
 * 시 본 임계 초과 후 자동 EXPIRED 전환. 스케줄러가 PENDING + expires_at &lt; now 인 row 를 일괄
 * 만료. 운영 알림 가이드: docs/devops/slip-edit-request-notification.md.
 */
@Component
@ConfigurationProperties(prefix = "app.slip.edit-request")
@Getter
@Setter
public class SlipEditRequestProperties {

    /** 자동 만료 시간 (시간 단위, default 24h). */
    private int expiresHours = 24;
}
