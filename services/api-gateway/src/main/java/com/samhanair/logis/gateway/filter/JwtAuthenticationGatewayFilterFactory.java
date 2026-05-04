package com.samhanair.logis.gateway.filter;

import com.samhanair.logis.common.security.JwtTokenProvider;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Reactive gateway filter that verifies an HS256 JWT and forwards identity
 * headers downstream.
 *
 * <h2>Configuration</h2>
 * Declared per-route in {@code application.yml}:
 * <pre>
 *   filters:
 *     - JwtAuthentication
 *     - name: JwtAuthentication
 *       args: { allowedRoles: [MASTER, MANAGER] }
 * </pre>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Missing {@code Authorization: Bearer ...} → {@code 401 UNAUTHORIZED}.</li>
 *   <li>Signature/expiry/parse failure → {@code 401 INVALID_TOKEN}.</li>
 *   <li>Authenticated but role not in allow-list → {@code 403 FORBIDDEN}.</li>
 *   <li>Otherwise: mutate request to add {@code X-User-Id} and
 *       {@code X-User-Role}, then continue.</li>
 * </ul>
 *
 * <p>{@link JwtTokenProvider} is a stateless utility (static methods) — it
 * is intentionally instantiated nowhere; we just call its static API.
 */
@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final JwtProperties props;

    public JwtAuthenticationGatewayFilterFactory(JwtProperties props) {
        super(Config.class);
        this.props = props;
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("required");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (header == null || !header.startsWith(BEARER_PREFIX)) {
                if (config.isRequired()) {
                    return writeError(exchange, HttpStatus.UNAUTHORIZED,
                            "UNAUTHORIZED", "인증 토큰이 없습니다");
                }
                return chain.filter(exchange);
            }

            String token = header.substring(BEARER_PREFIX.length()).trim();
            Jws<Claims> jws;
            try {
                jws = JwtTokenProvider.parse(token, props.getSecretBytes());
            } catch (Exception ex) {
                return writeError(exchange, HttpStatus.UNAUTHORIZED,
                        "INVALID_TOKEN", "유효하지 않은 토큰입니다");
            }

            String userId = JwtTokenProvider.getUserId(jws);
            String roleName = JwtTokenProvider.getRole(jws);

            if (!config.getAllowedRoles().isEmpty()) {
                Role role;
                try {
                    role = Role.valueOf(roleName);
                } catch (IllegalArgumentException ex) {
                    return writeError(exchange, HttpStatus.FORBIDDEN,
                            "FORBIDDEN", "권한이 없습니다");
                }
                if (!config.getAllowedRoles().contains(role)) {
                    return writeError(exchange, HttpStatus.FORBIDDEN,
                            "FORBIDDEN", "권한이 없습니다");
                }
            }

            ServerHttpRequest mutated = request.mutate()
                    .header(HEADER_USER_ID, userId)
                    .header(HEADER_USER_ROLE, roleName)
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        };
    }

    private static Mono<Void> writeError(ServerWebExchange exchange,
                                         HttpStatus status,
                                         String code,
                                         String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(
                MediaType.parseMediaType("application/json;charset=UTF-8"));

        String body = "{\"success\":false,\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}";
        DataBufferFactory factory = response.bufferFactory();
        DataBuffer buffer = factory.wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /** Per-route configuration knobs for the JWT filter. */
    @Getter
    @Setter
    public static class Config {
        /** When false, the filter is permissive on missing tokens. */
        private boolean required = true;
        /** When non-empty, role must be one of these (else 403). */
        private List<Role> allowedRoles = new ArrayList<>();
    }
}
