package com.samhanair.logis.partnerauth.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 파트너 로그인 시도 audit.
 *
 * <p>FK 형식이 아닌 {@code auth_id} (UUID) reference 만 보관 (M3 owner 변경에
 * 영향 받지 않음). bizNo / IP / User-Agent / 모바일 여부 / 결과를 기록한다.
 */
@Entity
@Getter
@Table(name = "partner_login_attempt")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerLoginAttempt extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** {@link PartnerAuth#getId()} 참조 (DB FK 미생성 — service 영역 분리). */
    @Column(name = "auth_id")
    private UUID authId;

    @Column(name = "biz_no", nullable = false, length = 12)
    private String bizNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private LoginAttemptResult result;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "is_mobile", nullable = false)
    private boolean mobile = false;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    private PartnerLoginAttempt(UUID authId, String bizNo, LoginAttemptResult result,
                                String clientIp, String userAgent, boolean mobile) {
        this.authId = authId;
        this.bizNo = bizNo;
        this.result = result;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.mobile = mobile;
        this.attemptedAt = LocalDateTime.now();
    }

    public static PartnerLoginAttempt of(UUID authId, String bizNo, LoginAttemptResult result,
                                         String clientIp, String userAgent, boolean mobile) {
        return new PartnerLoginAttempt(authId, bizNo, result, clientIp, userAgent, mobile);
    }
}
