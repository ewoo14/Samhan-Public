package com.samhanair.logis.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * notification-service 메일 발송 NoOp stub — Phase 10 P0-2 (manual 06-트러블슈팅/01-로그인-실패.md §1-3).
 *
 * <p>실 SMTP 연결은 DevOps Phase A6 에서 별도 처리 예정. 현재는 logger.info 만 출력하여
 * 토큰 발급 흐름을 추적 가능하게 한다. 운영 환경에서 token 평문 로깅은 보안 이슈이므로
 * Phase A6 cutover 시 본 stub 을 삭제 + 실 NotificationClient 로 교체할 것.
 */
@Slf4j
@Component
public class NotificationStub {

    /**
     * 비밀번호 reset 토큰 메일 발송 (NoOp). Phase A6 에서 SMTP 발송으로 교체.
     *
     * @param email     수신자 이메일 (사용자 신원 확인용 — Account 테이블엔 미보관, request 에 포함)
     * @param loginId   사용자 아이디 (로그용)
     * @param token     단일 사용 reset 토큰 (URL 에 임베드 예정)
     * @param expiresAt 토큰 만료 시점 (사용자 안내 용)
     */
    public void sendPasswordResetEmail(String email, String loginId, String token, String expiresAt) {
        log.info(
                "[NotificationStub] password-reset email queued — loginId={} email={} expiresAt={} (token redacted)",
                loginId, email, expiresAt);
    }
}
