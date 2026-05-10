package com.samhanair.logis.slip.publish;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 10 PR-G1 backlog #1 — 슬립 발행 정책 config.
 *
 * <p>옵션 C (hybrid) 채택 — TM/PM 권고. 운영 유연성 보존 + 정책 변경 가능.
 *
 * <p>strict 모드 (default = true):
 * <ul>
 *   <li>{@link SlipPublishService} 가 발행 직전 partner-service
 *       {@code GET /internal/partners/{partnerCode}} 호출.</li>
 *   <li>404 반환 (거래처 미존재) → {@code BusinessException(NOT_FOUND)} → 슬립 발행 reject.
 *       호출자에게 "거래처 {partnerCode} 미등록 — partner-service 에 먼저 등록하세요" 안내.</li>
 *   <li>200 반환 (거래처 존재) → 정상 발행 진행.</li>
 *   <li>5xx / 연결 실패 → fail-open (raw 저장 + warning log) — partner-service 장애가 전체
 *       발행 SLA 를 막지 않도록 (slip-service 가 회계 critical path).</li>
 * </ul>
 *
 * <p>non-strict 모드 (operations override):
 * <ul>
 *   <li>partner-service lookup 자체를 skip — partnerCode raw 저장만 수행.</li>
 *   <li>warning log "[strict OFF] partner verify skipped (code={})" 기록 (운영 audit).</li>
 *   <li>data-migration / disaster-recovery 시점에만 false 권장.</li>
 * </ul>
 *
 * <p>application.yml: {@code app.slip.partner-strict-validation: true} (기본).
 */
@Component
@ConfigurationProperties(prefix = "app.slip")
@Getter
@Setter
public class SlipPublishProperties {

    /**
     * partner-service 거래처 lookup strict 모드 on/off.
     *
     * <p>true (default) — 404 시 발행 reject + 사용자 안내.
     * <br>false — lookup skip + warning log (data-migration / disaster-recovery 시점만).
     */
    private boolean partnerStrictValidation = true;
}
