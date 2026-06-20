package com.samhanair.logis.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.InternalAuthProperties;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies that {@link AuthClient} sends the {@code X-Internal-Token} header on every request
 * and that error mapping (409 → CONFLICT, 5xx → INTERNAL_ERROR) behaves as documented.
 *
 * <p>We bypass Spring Cloud LoadBalancer here by handing AuthClient a plain
 * {@link RestClient.Builder} — the load-balanced behaviour is wired in
 * {@code RestClientConfig} and out of scope for this unit test.
 */
class AuthClientTest {

    private static final String TOKEN = "test-token-xyz";

    private MockRestServiceServer server;
    private AuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // Bind a MockRestServiceServer to the underlying RestTemplate equivalent. RestClient
        // doesn't expose its internal request executor directly, so we use the bind-to-builder
        // form which intercepts at the requestFactory level.
        server = MockRestServiceServer.bindTo(builder).build();

        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        client = new AuthClient(builder, props);
    }

    @Test
    void createAccount_sendsInternalTokenHeader_andSucceedsOn201() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withCreatedEntity(URI.create("/auth/internal/accounts/" + id)));

        client.createAccount(id, "alice", "password123", "Alice", Role.SALES);

        server.verify();
    }

    @Test
    void createAccount_409_mapsToConflict() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.createAccount(id, "alice", "password123", "Alice", Role.SALES))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
        server.verify();
    }

    @Test
    void createAccount_500_mapsToInternalError() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.createAccount(id, "alice", "password123", "Alice", Role.SALES))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    @Test
    void updateRole_sendsInternalTokenHeader() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/role"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        client.updateRole(id, Role.MANAGER);
        server.verify();
    }

    @Test
    void unlock_sendsInternalTokenHeader_andSucceedsOn200() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/unlock"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.OK));

        client.unlock(id);
        server.verify();
    }

    @Test
    void unlock_error_mapsToInternalError() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/unlock"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.unlock(id))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    @Test
    void updateDisplayName_sendsInternalTokenHeader() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/display-name"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        client.updateDisplayName(id, "새이름");
        server.verify();
    }

    @Test
    void updateDepartmentName_sendsInternalTokenHeaderAndBody() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/department-name"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(jsonPath("$.departmentName").value("물류운영팀"))
                .andRespond(withStatus(HttpStatus.OK));

        client.updateDepartmentName(id, "물류운영팀");
        server.verify();
    }

    @Test
    void updateDepartmentName_allowsNullDepartmentName() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/department-name"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(jsonPath("$.departmentName").value(nullValue()))
                .andRespond(withStatus(HttpStatus.OK));

        client.updateDepartmentName(id, null);
        server.verify();
    }

    @Test
    void updateDepartmentName_error_mapsToInternalError() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/department-name"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(jsonPath("$.departmentName").value("물류운영팀"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.updateDepartmentName(id, "물류운영팀"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    @Test
    void disable_sendsInternalTokenHeader() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id + "/disable"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        client.disable(id);
        server.verify();
    }

    @Test
    void delete_sendsInternalTokenHeader() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://auth-service/auth/internal/accounts/" + id))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withNoContent());

        client.delete(id);
        server.verify();
    }
}
