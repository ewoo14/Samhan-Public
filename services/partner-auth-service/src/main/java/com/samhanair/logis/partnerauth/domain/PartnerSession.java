package com.samhanair.logis.partnerauth.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 파트너 JWT 세션.
 *
 * <p>JWT JTI (token id) UNIQUE + 만료/취소 시각 보관. revoke 시
 * {@link #revokedAt} 만 갱신 (soft delete 별개).
 */
@Entity
@Getter
@Table(name = "partner_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerSession extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** JWT JTI claim 값 — UNIQUE. */
    @Column(name = "jti", nullable = false, unique = true, length = 64)
    private String jti;

    /** 발급된 PartnerAuth.id reference. */
    @Column(name = "auth_id", nullable = false)
    private UUID authId;

    @Column(name = "biz_no", nullable = false, length = 12)
    private String bizNo;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    private PartnerSession(String jti, UUID authId, String bizNo,
                           LocalDateTime issuedAt, LocalDateTime expiresAt, String clientIp) {
        this.jti = jti;
        this.authId = authId;
        this.bizNo = bizNo;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.clientIp = clientIp;
    }

    public static PartnerSession issue(String jti, UUID authId, String bizNo,
                                       LocalDateTime issuedAt, LocalDateTime expiresAt, String clientIp) {
        return new PartnerSession(jti, authId, bizNo, issuedAt, expiresAt, clientIp);
    }

    public void revoke() {
        this.revokedAt = LocalDateTime.now();
    }

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
