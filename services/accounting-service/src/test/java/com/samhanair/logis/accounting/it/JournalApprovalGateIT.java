package com.samhanair.logis.accounting.it;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.accounting.AccountingServiceApplication;
import com.samhanair.logis.accounting.client.ApprovalLineAuthorizeTestConfig;
import com.samhanair.logis.accounting.client.ChatRoomMappingClient;
import com.samhanair.logis.accounting.client.ETaxClient;
import com.samhanair.logis.accounting.client.KftcClient;
import com.samhanair.logis.accounting.client.PartnerLookupClient;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** 회계전표 게시 B-게이트 결재라인 enforcement IT. */
@SpringBootTest(classes = {
        AccountingServiceApplication.class,
        ApprovalLineAuthorizeTestConfig.class
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class JournalApprovalGateIT extends AbstractPostgresIT {

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String POST_USER = "00000000-0000-0000-0000-000000000101";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApprovalLineAuthorizeTestConfig.RestClientMockServerHolder holder;

    @MockBean private ETaxClient eTaxClient;
    @MockBean private KftcClient kftcClient;
    @MockBean private PartnerLookupClient partnerLookupClient;
    @MockBean private ChatRoomMappingClient chatRoomMappingClient;
    @MockBean(classes = com.samhanair.logis.security.permission.DynamicPermissionClient.class)
    private DynamicPermissionClient dynamicPermissionClient;

    @BeforeEach
    void setUpExternalStubs() {
        holder.server().reset();
        lenient().when(partnerLookupClient.findByPartnerId(any())).thenReturn(Optional.empty());
        lenient().when(partnerLookupClient.findByPartnerCode(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("configured=true allowed=false — 결재자가 아니면 회계전표 게시 403")
    void postDeniedWhenApprovalLineConfiguredAndActorNotAllowed() throws Exception {
        String id = createJournalAsAccountant("70000");
        expectAuthorize(holder.server(), POST_USER, true, false);

        mockMvc.perform(post("/accounting/journals/" + id + "/post")
                        .header("X-User-Id", POST_USER)
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isForbidden());

        holder.server().verify();
    }

    @Test
    @DisplayName("configured=false — 결재자 미설정(opt-in 전) 회계전표 게시 200")
    void postAllowedWhenApprovalLineNotConfigured() throws Exception {
        String id = createJournalAsAccountant("80000");
        expectAuthorize(holder.server(), POST_USER, false, false);

        mockMvc.perform(post("/accounting/journals/" + id + "/post")
                        .header("X-User-Id", POST_USER)
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk());

        holder.server().verify();
    }

    @Test
    @DisplayName("configured=true allowed=true — 결재자는 회계전표 게시 200")
    void postAllowedWhenApprovalLineConfiguredAndActorAllowed() throws Exception {
        String id = createJournalAsAccountant("90000");
        expectAuthorize(holder.server(), POST_USER, true, true);

        mockMvc.perform(post("/accounting/journals/" + id + "/post")
                        .header("X-User-Id", POST_USER)
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk());

        holder.server().verify();
    }

    private void expectAuthorize(MockRestServiceServer server, String userId, boolean configured, boolean allowed) {
        server.expect(once(), requestTo("http://auth-service/auth/internal/approval-line/authorize"))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"documentType":"ACCOUNTING_JOURNAL","actionKey":"JOURNAL_POST","userId":"%s"}
                        """.formatted(userId)))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"configured":%s,"allowed":%s}}
                        """.formatted(configured, allowed), MediaType.APPLICATION_JSON));
    }

    private String createJournalAsAccountant(String amount) throws Exception {
        MvcResult res = mockMvc.perform(post("/accounting/journals")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ACCOUNTANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(balancedJournalBody(amount))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private Map<String, Object> balancedJournalBody(String amount) {
        Map<String, Object> debitLine = new HashMap<>();
        debitLine.put("accountCode", "101");
        debitLine.put("debitAmount", new BigDecimal(amount));
        debitLine.put("creditAmount", BigDecimal.ZERO);
        debitLine.put("memo", "현금 입금");

        Map<String, Object> creditLine = new HashMap<>();
        creditLine.put("accountCode", "401");
        creditLine.put("debitAmount", BigDecimal.ZERO);
        creditLine.put("creditAmount", new BigDecimal(amount));
        creditLine.put("memo", "상품매출");

        Map<String, Object> body = new HashMap<>();
        body.put("journalDate", "2026-05-04");
        body.put("description", "테스트 분개");
        body.put("lines", List.of(debitLine, creditLine));
        return body;
    }
}
