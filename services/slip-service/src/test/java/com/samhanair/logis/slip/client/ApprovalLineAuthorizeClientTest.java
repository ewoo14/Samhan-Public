package com.samhanair.logis.slip.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.security.InternalAuthProperties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** ApprovalLineAuthorizeClient RestClient 계약 테스트. */
class ApprovalLineAuthorizeClientTest {

    private MockRestServiceServer server;
    private ApprovalLineAuthorizeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken("test-internal-token");
        client = new ApprovalLineAuthorizeClient(
                builder.baseUrl("http://auth-service").build(), props, new ObjectMapper());
    }

    @Test
    void authorize_posts_internal_token_and_parses_api_response() {
        UUID userId = UUID.randomUUID();
        server.expect(once(), requestTo("http://auth-service/auth/internal/approval-line/authorize"))
                .andExpect(header("X-Internal-Token", "test-internal-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"documentType":"SLIP_OUTBOUND","actionKey":"OUTBOUND_DISPATCH","userId":"%s"}
                        """.formatted(userId)))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"configured":true,"allowed":true}}
                        """, MediaType.APPLICATION_JSON));

        ApprovalLineAuthorizeResult result = client.authorize(
                "SLIP_OUTBOUND", "OUTBOUND_DISPATCH", userId);

        assertThat(result.configured()).isTrue();
        assertThat(result.allowed()).isTrue();
        server.verify();
    }

    @Test
    void authorize_dataMissing_failClosed() {
        UUID userId = UUID.randomUUID();
        server.expect(once(), requestTo("http://auth-service/auth/internal/approval-line/authorize"))
                .andRespond(withSuccess("""
                        {"success":true}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.authorize(
                "SLIP_OUTBOUND", "OUTBOUND_INSPECT", userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("결재라인 인가 응답 형식 오류");
        server.verify();
    }

    @Test
    void authorize_successFalse_failClosed() {
        UUID userId = UUID.randomUUID();
        server.expect(once(), requestTo("http://auth-service/auth/internal/approval-line/authorize"))
                .andRespond(withSuccess("""
                        {"success":false,"data":{"configured":false,"allowed":false}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.authorize(
                "SLIP_OUTBOUND", "OUTBOUND_INSPECT", userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("결재라인 인가 응답 형식 오류");
        server.verify();
    }
}
