package com.samhanair.logis.accounting.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.accounting.AccountingServiceApplication;
import com.samhanair.logis.accounting.client.ChatRoomMappingClient;
import com.samhanair.logis.accounting.client.ETaxClient;
import com.samhanair.logis.accounting.client.KftcClient;
import com.samhanair.logis.accounting.client.PartnerLookupClient;
import com.samhanair.logis.accounting.client.ProductClient;
import com.samhanair.logis.accounting.client.SlipQueryClient;
import com.samhanair.logis.accounting.client.SlipServiceClient;
import com.samhanair.logis.accounting.domain.BankTransaction;
import com.samhanair.logis.accounting.repository.BankTransactionRepository;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.security.permission.PermissionAction;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * H-1 BankTransaction 통합 테스트.
 *
 * <p>실 PostgreSQL + Flyway V43 기반으로 CSV 범용 매핑 import, 중복 skip 멱등성,
 * CHECK 제약, 탭 필터, 상태전이 가드를 검증한다.
 */
@SpringBootTest(classes = AccountingServiceApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BankTransactionControllerIT extends AbstractPostgresIT {

    private static final String BASE_URL = "/accounting/bank-transactions";
    private static final String BANK_ACCOUNT_LABEL = "국민 123-456";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private BankTransactionRepository repository;

    @MockBean private SlipServiceClient slipServiceClient;
    @MockBean private SlipQueryClient slipQueryClient;
    @MockBean private PartnerLookupClient partnerLookupClient;
    @MockBean private ProductClient productClient;
    @MockBean private ChatRoomMappingClient chatRoomMappingClient;
    @MockBean private ETaxClient eTaxClient;
    @MockBean private KftcClient kftcClient;
    @MockBean(classes = com.samhanair.logis.security.permission.DynamicPermissionClient.class)
    private DynamicPermissionClient dynamicPermissionClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM bank_transaction");
        lenient().when(partnerLookupClient.findByPartnerIdsBatch(any())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("CSV import: MS949 한글 적요 적재 + 재업로드 중복 skip + UUID 미노출")
    void importCsv_idempotentAndHidesUuid() throws Exception {
        MockMultipartFile file = ms949Csv();

        importCsv(file)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.data.duplicateSkippedCount").value(0));

        importCsv(ms949Csv())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.importedCount").value(0))
                .andExpect(jsonPath("$.data.duplicateSkippedCount").value(2));

        mockMvc.perform(get(BASE_URL)
                        .param("matchStatus", "UNREFLECTED")
                        .param("from", "2026-06-23")
                        .param("to", "2026-06-23")
                        .param("bankAccountLabel", BANK_ACCOUNT_LABEL)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].description").value("이체 수수료"))
                .andExpect(jsonPath("$.data[1].description").value("삼한테스트상사 입금"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].matchedPartnerId").doesNotExist())
                .andExpect(jsonPath("$.data[0].matchedJournalId").doesNotExist());

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bank_transaction", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("RequirePermission: accounting.bank-matching CREATE deny 시 import 403")
    void importCsv_requiresCreatePermission() throws Exception {
        denyRequirePermission("accounting.bank-matching", PermissionAction.CREATE);

        importCsv(ms949Csv())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CHECK 제약: txn_type/source/match_status/amount native INSERT 거부")
    void checkConstraint_rejectsInvalidEnumAndAmount() {
        assertThatThrownBy(() -> insertNative("TRANSFER", "CSV_IMPORT", "UNREFLECTED", "1000.00", "bad-type"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertNative("DEPOSIT", "MANUAL", "UNREFLECTED", "1000.00", "bad-source"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertNative("DEPOSIT", "CSV_IMPORT", "MATCHED", "1000.00", "bad-status"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertNative("DEPOSIT", "CSV_IMPORT", "UNREFLECTED", "0.00", "bad-amount"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("상태전이 가드: 반영 후 강제 전환 거부")
    void domainTransition_rejectsInvalidTransition() throws Exception {
        importCsv(ms949Csv()).andExpect(status().isOk());

        BankTransaction transaction = repository.findByExternalRefAndIsDeletedFalse("BANK-001")
                .orElseThrow();
        transaction.markReflected(UUID.randomUUID());
        repository.saveAndFlush(transaction);

        assertThatThrownBy(() -> transaction.markForced(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition bank transaction");

        mockMvc.perform(get(BASE_URL)
                        .param("matchStatus", "REFLECTED")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].matchStatus").value("REFLECTED"));
    }

    private org.springframework.test.web.servlet.ResultActions importCsv(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart(BASE_URL + "/import")
                .file(file)
                .param("bankAccountLabel", BANK_ACCOUNT_LABEL)
                .param("dateColumn", "거래일시")
                .param("depositColumn", "입금액")
                .param("withdrawalColumn", "출금액")
                .param("balanceColumn", "잔액")
                .param("descriptionColumn", "적요")
                .param("counterpartyColumn", "상대")
                .param("externalRefColumn", "참조")
                .param("headerRow", "true")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "ACCOUNTANT"));
    }

    private static MockMultipartFile ms949Csv() {
        String csv = """
                거래일시,입금액,출금액,잔액,적요,상대,참조
                2026-06-23 09:10,150000,,1150000,삼한테스트상사 입금,삼한테스트상사,BANK-001
                2026-06-23 11:30,,50000,1100000,이체 수수료,국민은행,BANK-002
                """;
        return new MockMultipartFile(
                "file",
                "bank-ms949.csv",
                MediaType.TEXT_PLAIN_VALUE,
                csv.getBytes(Charset.forName("MS949")));
    }

    private void insertNative(String txnType, String source, String matchStatus, String amount, String externalRef) {
        jdbcTemplate.update("""
                INSERT INTO bank_transaction (
                    id, transacted_at, txn_type, amount, description, bank_account_label,
                    source, external_ref, match_status, created_at, created_by, is_deleted
                ) VALUES (
                    ?, TIMESTAMP '2026-06-23 09:00:00', ?, ?::numeric, 'bad', '국민 123-456',
                    ?, ?, ?, NOW(), 'it', FALSE
                )
                """, UUID.randomUUID(), txnType, amount, source, externalRef, matchStatus);
    }
}
