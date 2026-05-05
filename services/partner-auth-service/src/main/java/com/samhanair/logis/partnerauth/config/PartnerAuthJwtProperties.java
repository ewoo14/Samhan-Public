package com.samhanair.logis.partnerauth.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파트너 JWT 발급 설정 ({@code samhan.jwt.*}).
 *
 * <p>shared:common 의 {@link com.samhanair.logis.common.security.JwtTokenProvider} 와
 * 동일 HS256 시크릿 / TTL 모델을 사용한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "samhan.jwt")
public class PartnerAuthJwtProperties {

    /** HS256 시크릿. dev-only 기본값은 운영 배포 시 반드시 override. */
    private String secret = "dev-only-secret-replace-32bytes-min!!";

    /** 토큰 만료(시간). 설계서 W3 정정 예정 — 현 구현은 8시간. */
    private int expirationHours = 8;

    public byte[] getSecretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public int getExpirationSeconds() {
        return expirationHours * 3600;
    }

    @PostConstruct
    public void validate() {
        if (getSecretBytes().length < 32) {
            // HS256 권장 최소 길이. dev-only 시크릿은 32 bytes 이상 의무.
            throw new IllegalStateException(
                    "samhan.jwt.secret 는 최소 32 bytes 여야 합니다 (현재 " + getSecretBytes().length + ")");
        }
    }
}
