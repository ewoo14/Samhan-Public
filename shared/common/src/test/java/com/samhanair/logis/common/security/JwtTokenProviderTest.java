package com.samhanair.logis.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final byte[] SECRET =
            "samhanlogis-test-secret-key-must-be-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTripPreservesUserIdAndRole() {
        String token = JwtTokenProvider.generate("user-001", Role.SALES.name(), 3600L, SECRET);

        Jws<Claims> parsed = JwtTokenProvider.parse(token, SECRET);

        assertEquals("user-001", JwtTokenProvider.getUserId(parsed));
        assertEquals("SALES", JwtTokenProvider.getRole(parsed));
    }
}
