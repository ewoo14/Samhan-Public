package com.samhanair.logis.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Cloud Messaging (FCM) 환경 설정 — Phase 9 W3 placeholder.
 *
 * <ul>
 *   <li>{@code projectId} — Firebase 프로젝트 ID</li>
 *   <li>{@code credentialsPath} — service-account credentials JSON 파일 경로</li>
 * </ul>
 *
 * <p>본 슬라이스 시점에는 credentials 가 placeholder 인 경우 stub-success 반환 (외부 호출 X).
 * Phase 10 cutover 또는 모바일 staff app 활성 시점에 본격 SDK 통합.
 */
@Data
@ConfigurationProperties(prefix = "samhan.notification.fcm")
public class FcmProperties {

    private String projectId;
    private String credentialsPath;
}
