package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.samhanair.logis.auth.AuthServiceApplication;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 결재라인 내부 인가 endpoint X-Internal-Token 계약 IT. */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "app.security.jwt.secret=test-secret-key-32-chars-min-aaaaaa",
        "app.security.internal.token=test-internal-token"
})
class ApprovalLineAuthorizeControllerIT extends AbstractPostgresIT {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /auth/internal/approval-line/authorize — X-Internal-Token 일치 시 200")
    void authorize_withInternalToken_returns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/internal/approval-line/authorize")
                        .header(INTERNAL_TOKEN_HEADER, "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"SLIP_OUTBOUND","actionKey":"OUTBOUND_DISPATCH","userId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"configured\"")
                .contains("\"allowed\"");
    }

    @Test
    @DisplayName("POST /auth/internal/approval-line/authorize — X-Internal-Token 없으면 4xx")
    void authorize_withoutInternalToken_returns4xx() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/internal/approval-line/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"SLIP_OUTBOUND","actionKey":"OUTBOUND_DISPATCH","userId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
    }
}
