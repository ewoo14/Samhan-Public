package com.samhanair.logis.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/** Stateless HS256 JWT issuer/parser using the jjwt 0.12.x fluent API. */
public final class JwtTokenProvider {

    private JwtTokenProvider() {
    }

    public static String generate(String userId, String role, long ttlSeconds, byte[] secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret);
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public static Jws<Claims> parse(String token, byte[] secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret);
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    public static String getUserId(Jws<Claims> jws) {
        return jws.getPayload().getSubject();
    }

    public static String getRole(Jws<Claims> jws) {
        return jws.getPayload().get("role", String.class);
    }
}
