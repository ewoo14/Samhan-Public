package com.samhanair.logis.accounting.it;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.accounting.AccountingServiceApplication;
import com.samhanair.logis.accounting.client.ChatRoomMappingClient;
import com.samhanair.logis.accounting.client.ETaxClient;
import com.samhanair.logis.accounting.client.KftcClient;
import com.samhanair.logis.accounting.client.PartnerLookupClient;
import com.samhanair.logis.accounting.client.ProductClient;
import com.samhanair.logis.accounting.client.SlipQueryClient;
import com.samhanair.logis.accounting.client.SlipServiceClient;
import com.samhanair.logis.security.permission.DefaultDynamicPermissionClient;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

/** H-1 accounting.bank-matching @RequirePermission 실 DPC 호출 enforcement 검증. */
@SpringBootTest(classes = {
        AccountingServiceApplication.class,
        BankTransactionPermissionEnforcementIT.RestClientMockConfig.class
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@ExtendWith(AbstractPostgresIT.DockerAvailableCondition.class)
class BankTransactionPermissionEnforcementIT {

    private static final String BASE_URL = "/accounting/bank-transactions";
    private static final String PAGE_CODE = "accounting.bank-matching";
    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final UUID MANAGER_ACCOUNT = UUID.fromString("a0000000-0000-0000-0000-000000000003");
    private static final UUID ACCOUNTANT_ACCOUNT = UUID.fromString("a0000000-0000-0000-0000-000000000005");
    private static final UUID SALES_ACCOUNT = UUID.fromString("a0000000-0000-0000-0000-000000000004");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RestClientMockServerHolder restClientMockServerHolder;

    @MockBean private SlipServiceClient slipServiceClient;
    @MockBean private SlipQueryClient slipQueryClient;
    @MockBean private PartnerLookupClient partnerLookupClient;
    @MockBean private ProductClient productClient;
    @MockBean private ChatRoomMappingClient chatRoomMappingClient;
    @MockBean private ETaxClient eTaxClient;
    @MockBean private KftcClient kftcClient;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractPostgresIT.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractPostgresIT.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractPostgresIT.POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
        registry.add("app.security.internal.token", () -> INTERNAL_TOKEN);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    }

    @BeforeEach
    void setUp() {
        restClientMockServerHolder.server.reset();
        jdbcTemplate.update("DELETE FROM bank_transaction");
    }

    @Test
    @DisplayName("BankTransaction import: MANAGER/ACCOUNTANT CREATE 200, 미허용 계정 403")
    void importCsvWritePermissions_useDirectDynamicPermissionClient() throws Exception {
        expectPermission(MANAGER_ACCOUNT, true);
        expectPermission(ACCOUNTANT_ACCOUNT, true);
        expectPermission(SALES_ACCOUNT, false);

        importCsv(MANAGER_ACCOUNT, "PERM-MANAGER-001")
                .andExpect(status().isOk());
        importCsv(ACCOUNTANT_ACCOUNT, "PERM-ACCOUNTANT-001")
                .andExpect(status().isOk());
        importCsv(SALES_ACCOUNT, "PERM-SALES-001")
                .andExpect(status().isForbidden());

        restClientMockServerHolder.server.verify();
    }

    private void expectPermission(UUID accountId, boolean allowed) {
        restClientMockServerHolder.server.expect(once(), requestTo("http://auth-service/auth/internal/permissions/check"
                        + "?accountId=" + accountId
                        + "&pageCode=" + PAGE_CODE
                        + "&action=CREATE"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(header("X-User-Id", "system-internal:accounting-service"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"allowed\":" + allowed + "}}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.servlet.ResultActions importCsv(UUID accountId, String externalRef)
            throws Exception {
        return mockMvc.perform(multipart(BASE_URL + "/import")
                .file(csv(externalRef))
                .param("bankAccountLabel", "국민 권한테스트")
                .param("dateColumn", "거래일시")
                .param("depositColumn", "입금액")
                .param("withdrawalColumn", "출금액")
                .param("balanceColumn", "잔액")
                .param("descriptionColumn", "적요")
                .param("counterpartyColumn", "상대")
                .param("externalRefColumn", "참조")
                .param("headerRow", "true")
                .header("X-User-Id", accountId.toString()));
    }

    private static MockMultipartFile csv(String externalRef) {
        String csv = """
                거래일시,입금액,출금액,잔액,적요,상대,참조
                2026-06-23 09:10,150000,,1150000,권한테스트 입금,권한테스트상사,%s
                """.formatted(externalRef);
        return new MockMultipartFile(
                "file",
                "bank-permission.csv",
                MediaType.TEXT_PLAIN_VALUE,
                csv.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class RestClientMockConfig {

        @Bean
        RestClientMockServerHolder restClientMockServerHolder() {
            return new RestClientMockServerHolder();
        }

        @Bean
        DynamicPermissionClient dynamicPermissionClient(RestClientMockServerHolder holder) {
            RestClient.Builder builder = RestClient.builder();
            holder.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(false).build();
            return new DefaultDynamicPermissionClient(
                    builder,
                    "http://auth-service",
                    INTERNAL_TOKEN,
                    "accounting-service"
            );
        }
    }

    static class RestClientMockServerHolder {
        private MockRestServiceServer server;
    }
}
