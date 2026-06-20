package com.samhanair.logis.user.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 사원 서명 모바일 핸드오프 토큰 (slice C1b · spec §4.4).
 *
 * <p>관리자 desktop 이 "모바일로 그리기" 발급 시 1개 생성된다. 사원 폰이 이 토큰으로
 * 공개 제출 endpoint({@code POST /api/public/employee-signatures/{token}}) 에 1회 접근한다.
 *
 * <p>보안 속성:
 * <ul>
 *   <li>token = {@link SecureRandom} 48바이트 → base64url 64자 (UUID 와 다른 형식 —
 *       UUID 비공개 가드 무관, slip {@code DeliveryBatch} 패턴 미러).</li>
 *   <li>TTL = {@value #TOKEN_TTL_MINUTES} 분 ({@link #expiresAt}).</li>
 *   <li>1회용 — {@link #usedAt} 소진 후 재사용 불가 (재제출 409).</li>
 *   <li>재발급 시 서비스가 동일 사원 미사용 토큰을 soft-delete 무효화 (1슬롯 경합 회피).</li>
 * </ul>
 */
@Entity
@Getter
@Table(name = "employee_signature_handoff_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class EmployeeSignatureHandoffToken extends BaseEntity {

    /** 토큰 TTL — 10분 (spec §4.4). */
    public static final int TOKEN_TTL_MINUTES = 10;

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 48; // base64url(48 bytes) = 64자

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "token", nullable = false, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "actor_user_id", length = 50)
    private String actorUserId;

    private EmployeeSignatureHandoffToken(UUID employeeId, String token,
                                          LocalDateTime expiresAt, String actorUserId) {
        this.employeeId = employeeId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.actorUserId = actorUserId;
    }

    /**
     * 신규 토큰 발급 — base64url 64자 + now+10분 만료.
     *
     * @param employeeId 서명 대상 사원 (Employee.id = canonical user UUID)
     * @param actorUserId 발급한 관리자 user-id (X-User-Id, 감사용, nullable)
     */
    public static EmployeeSignatureHandoffToken issue(UUID employeeId, String actorUserId) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId 는 필수입니다");
        }
        return new EmployeeSignatureHandoffToken(
                employeeId, generateToken(),
                LocalDateTime.now().plusMinutes(TOKEN_TTL_MINUTES), actorUserId);
    }

    /**
     * 토큰 소진 — 공개 제출 성공 직후 호출. 이미 사용된 토큰이면 409 CONFLICT.
     *
     * @throws BusinessException(CONFLICT) 이미 사용된 토큰 재소진 시도
     */
    public void markUsed() {
        if (this.usedAt != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용된 토큰입니다");
        }
        this.usedAt = LocalDateTime.now();
    }

    /** 만료 여부 — 공개 제출 진입 시 검증. */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    /** 사용 여부 — 공개 제출 진입 시 검증. */
    public boolean isUsed() {
        return this.usedAt != null;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
