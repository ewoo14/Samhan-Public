package com.samhanair.logis.auth.service;

import com.samhanair.logis.auth.domain.Account;
import com.samhanair.logis.auth.repository.AccountRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 / 변경 / 잠금 해제 use-case — Phase 10 P0-2.
 *
 * <p>출처: {@code docs/manual/06-트러블슈팅/01-로그인-실패.md §1-3}.
 *
 * <p>제공 흐름:
 * <ol>
 *     <li>{@link #requestReset(String, String)} — 사용자가 loginId + email 로 토큰 발급 요청
 *         (NotificationStub 으로 메일 발송 simulate). 사용자 존재 여부와 무관하게 동일 응답
 *         (enumeration 공격 방지).</li>
 *     <li>{@link #confirmReset(String, String)} — 토큰 + 신규 비밀번호 검증 후 변경.</li>
 *     <li>{@link #changePassword(UUID, String, String)} — 본인 인증 상태에서 기존 비밀번호 검증 후 변경.</li>
 *     <li>{@link #unlockAccount(UUID)} — MASTER 권한 잠금 해제.</li>
 * </ol>
 *
 * <p>공통 정책:
 * <ul>
 *     <li>{@link PasswordPolicy} 강도 검증</li>
 *     <li>history 5 reuse 금지 (현재 hash 포함)</li>
 *     <li>토큰 30 분 만료</li>
 *     <li>비밀번호 변경 시 reset 토큰 + 잠금 자동 무효화</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PasswordResetService {

    /** reset 토큰 만료 — 30 분. */
    public static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationStub notificationStub;

    /**
     * 비밀번호 reset 토큰 요청 — 사용자 존재 여부와 무관하게 동일 응답 (enumeration 방지).
     * 존재 + 활성 사용자만 토큰 발급 + 메일 발송.
     */
    public void requestReset(String loginId, String email) {
        if (loginId == null || loginId.isBlank() || email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디와 이메일을 입력해주세요");
        }
        Optional<Account> opt = accountRepository.findByLoginId(loginId);
        if (opt.isEmpty()) {
            // enumeration 방지 — 존재하지 않아도 정상 응답. 로그만 남김.
            log.info("[PasswordReset] requestReset — unknown loginId={} (silent ok)", loginId);
            return;
        }
        Account account = opt.get();
        if (!account.isEnabled()) {
            log.info("[PasswordReset] requestReset — disabled loginId={} (silent ok)", loginId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(RESET_TOKEN_TTL);
        String token = UUID.randomUUID().toString();
        account.issueResetToken(token, expiresAt);

        notificationStub.sendPasswordResetEmail(email, loginId, token, expiresAt.toString());
    }

    /**
     * reset 토큰 confirm — 토큰 검증 + 정책 검증 + history reuse 금지 + 비밀번호 교체.
     *
     * @throws BusinessException UNAUTHORIZED (토큰 무효/만료), INVALID_INPUT (정책 위반/reuse),
     *                           NOT_FOUND (계정 없음 — 이론상 발생 불가)
     */
    public void confirmReset(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비밀번호 재설정 토큰이 유효하지 않습니다");
        }
        PasswordPolicy.validate(newPassword);

        Account account = accountRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED, "비밀번호 재설정 토큰이 유효하지 않습니다"));

        LocalDateTime now = LocalDateTime.now();
        if (!account.isResetTokenValid(token, now)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비밀번호 재설정 토큰이 만료되었거나 유효하지 않습니다");
        }

        ensureNotReused(account, newPassword);

        String newHash = passwordEncoder.encode(newPassword);
        account.changePassword(newHash, now);
        log.info("[PasswordReset] confirmReset — userId={} (token consumed)", account.getId());
    }

    /**
     * 본인 비밀번호 변경 — 기존 비밀번호 검증 + 정책 + history 5 reuse 금지.
     *
     * @throws BusinessException UNAUTHORIZED (기존 비밀번호 불일치), INVALID_INPUT (정책/reuse), NOT_FOUND
     */
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기존 비밀번호를 입력해주세요");
        }
        PasswordPolicy.validate(newPassword);

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "계정을 찾을 수 없습니다"));

        if (!passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "기존 비밀번호가 올바르지 않습니다");
        }

        ensureNotReused(account, newPassword);

        String newHash = passwordEncoder.encode(newPassword);
        account.changePassword(newHash, LocalDateTime.now());
        log.info("[PasswordReset] changePassword — userId={} (self-service)", userId);
    }

    /** MASTER 잠금 해제 — 카운터 + lockedAt 초기화. enabled 는 별도 흐름. */
    public void unlockAccount(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용자 식별자가 필요합니다");
        }
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "계정을 찾을 수 없습니다"));
        if (!account.isLocked() && account.getFailedLoginAttempts() == 0) {
            // 잠금 상태가 아니어도 idempotent — 별도 예외 던지지 않음 (운영자 UX)
            log.info("[PasswordReset] unlockAccount — userId={} already unlocked (no-op)", userId);
            return;
        }
        account.unlock();
        log.info("[PasswordReset] unlockAccount — userId={} unlocked by MASTER", userId);
    }

    /**
     * 신규 비밀번호가 현재 hash 또는 history 5 개 중 하나와 일치하면 reuse 위반.
     */
    private void ensureNotReused(Account account, String newPassword) {
        if (account.getPasswordHash() != null
                && passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "최근 사용한 비밀번호는 재사용할 수 없습니다");
        }
        for (String previousHash : account.getPasswordHistorySnapshot()) {
            if (previousHash != null && passwordEncoder.matches(newPassword, previousHash)) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT, "최근 사용한 비밀번호는 재사용할 수 없습니다");
            }
        }
    }
}
