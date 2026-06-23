package com.samhanair.logis.accounting.it;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.accounting.AccountingServiceApplication;
import com.samhanair.logis.accounting.client.ETaxClient;
import com.samhanair.logis.accounting.client.KftcClient;
import com.samhanair.logis.accounting.client.PartnerLookupClient;
import com.samhanair.logis.accounting.client.PartnerSummary;
import com.samhanair.logis.accounting.domain.Journal;
import com.samhanair.logis.accounting.domain.JournalLine;
import com.samhanair.logis.accounting.domain.JournalSourceType;
import com.samhanair.logis.accounting.report.ReportPermissionGuard;
import com.samhanair.logis.accounting.repository.JournalRepository;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.security.permission.PermissionAction;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정명세서 IT.
 *
 * <p>실 {@link Journal}/{@link JournalLine} POSTED 분개를 시드하여 기준일 이하
 * 계정×거래처 잔액 스냅샷과 채권/채무 정상 잔액 방향을 검증한다.
 */
@SpringBootTest(classes = AccountingServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class AccountStatementControllerIT extends AbstractPostgresIT {

    private static final UUID RECEIVABLE_A_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RECEIVABLE_B_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYABLE_C_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COUNTER_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID UNRESOLVED_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JournalRepository journalRepository;

    /** 외부 e-Tax client 격리. */
    @MockBean private ETaxClient eTaxClient;
    /** 외부 KFTC client 격리. */
    @MockBean private KftcClient kftcClient;
    /** partner-service lookup client 격리. */
    @MockBean private PartnerLookupClient partnerLookupClient;
    /** 동적 권한 client 격리. */
    @MockBean(classes = DynamicPermissionClient.class) private DynamicPermissionClient dynamicPermissionClient;

    @BeforeEach
    void setUpPartnerLookup() {
        lenient().when(partnerLookupClient.findByPartnerIdsBatch(anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<UUID> ids = invocation.getArgument(0, List.class);
                    Map<UUID, PartnerSummary> names = new HashMap<>();
                    if (ids.contains(RECEIVABLE_A_ID)) {
                        names.put(RECEIVABLE_A_ID, partner(RECEIVABLE_A_ID, "P-2026-0001", "삼한공조 A"));
                    }
                    if (ids.contains(RECEIVABLE_B_ID)) {
                        names.put(RECEIVABLE_B_ID, partner(RECEIVABLE_B_ID, "P-2026-0002", "삼한공조 B"));
                    }
                    if (ids.contains(PAYABLE_C_ID)) {
                        names.put(PAYABLE_C_ID, partner(PAYABLE_C_ID, "P-2026-0003", "대한운송 C"));
                    }
                    if (ids.contains(COUNTER_ID)) {
                        names.put(COUNTER_ID, partner(COUNTER_ID, "P-2026-9999", "상대거래처"));
                    }
                    return names;
                });
    }

    @Test
    @DisplayName("계정명세서 — 기준일 계정×거래처 잔액과 채권/채무 방향을 집계")
    void accountStatementComputesPartnerBalancesByAccountAsOfDate() throws Exception {
        seedFixtures();

        MvcResult result = mockMvc.perform(get("/accounting/reports/account-statement")
                        .param("asOfDate", "2026-06-30")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000101")
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).get("data");

        JsonNode receivableAccount = findAccount(data, "110");
        assertText(receivableAccount.get("balanceDirection"), "DEBIT");
        assertText(receivableAccount.get("balanceDirectionDisplayName"), "차변잔액");

        JsonNode receivableA = findLine(data, "110", "삼한공조 A");
        assertText(receivableA.get("partnerCode"), "P-2026-0001");
        assertAmount(receivableA.get("increase"), "10000.00");
        assertAmount(receivableA.get("decrease"), "3000.00");
        assertAmount(receivableA.get("balance"), "7000.00");

        JsonNode receivableB = findLine(data, "110", "삼한공조 B");
        assertText(receivableB.get("partnerCode"), "P-2026-0002");
        assertAmount(receivableB.get("increase"), "5000.00");
        assertAmount(receivableB.get("decrease"), "0.00");
        assertAmount(receivableB.get("balance"), "5000.00");

        JsonNode unresolved = findLine(data, "110", "(미조회)");
        assertText(unresolved.get("partnerCode"), "");
        assertAmount(unresolved.get("increase"), "700.00");
        assertAmount(unresolved.get("balance"), "700.00");

        JsonNode payableAccount = findAccount(data, "201");
        assertText(payableAccount.get("balanceDirection"), "CREDIT");
        assertText(payableAccount.get("balanceDirectionDisplayName"), "대변잔액");

        JsonNode payableC = findLine(data, "201", "대한운송 C");
        assertText(payableC.get("partnerCode"), "P-2026-0003");
        assertAmount(payableC.get("increase"), "8000.00");
        assertAmount(payableC.get("decrease"), "2000.00");
        assertAmount(payableC.get("balance"), "6000.00");

        JsonNode etcLine = findLine(data, "201", "기타");
        assertText(etcLine.get("partnerCode"), "");
        assertAmount(etcLine.get("increase"), "1000.00");
        assertAmount(etcLine.get("decrease"), "0.00");
        assertAmount(etcLine.get("balance"), "1000.00");
        assertLineCount(data, "201", "기타", 1);

        JsonNode total = data.get("total");
        assertAmount(total.get("receivableTotal").get("balance"),
                findGroup(data, "RECEIVABLE").get("subtotal").get("balance").asText());
        assertAmount(total.get("payableTotal").get("balance"),
                findGroup(data, "PAYABLE").get("subtotal").get("balance").asText());
        if (total.has("balance")) {
            throw new AssertionError("혼합 계정명세서 total 에 단일 balance 가 노출되었습니다");
        }

        if (body.contains(RECEIVABLE_A_ID.toString())
                || body.contains(RECEIVABLE_B_ID.toString())
                || body.contains(PAYABLE_C_ID.toString())
                || body.contains(UNRESOLVED_ID.toString())
                || body.contains("\"partnerId\"")) {
            throw new AssertionError("계정명세서 응답에 partner UUID 가 노출되었습니다");
        }
    }

    @Test
    @DisplayName("계정명세서 — accountCode 지정 시 해당 계정만 반환")
    void accountStatementFiltersByAccountCode() throws Exception {
        seedFixtures();

        MvcResult result = mockMvc.perform(get("/accounting/reports/account-statement")
                        .param("asOfDate", "2026-06-30")
                        .param("accountCode", "110")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000101")
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).get("data");

        assertText(data.get("accountCode"), "110");
        findLine(data, "110", "삼한공조 A");
        JsonNode receivableGroup = data.get("groups").get(0);
        assertText(receivableGroup.get("groupCode"), "RECEIVABLE");
        assertText(receivableGroup.get("groupName"), "채권");
        assertAmount(data.get("total").get("receivableTotal").get("balance"),
                receivableGroup.get("subtotal").get("balance").asText());
        if (!data.get("total").get("payableTotal").isNull()) {
            throw new AssertionError("accountCode=110 조회에 채무 합계가 노출되었습니다");
        }
        assertAccountMissing(data, "201");
        if (body.contains("외상매입금")) {
            throw new AssertionError("accountCode=110 조회에 채무 계정이 포함되었습니다");
        }
    }

    @Test
    @DisplayName("계정명세서 — VIEW 권한 deny 시 403")
    void accountStatementDeniedPermissionReturns403() throws Exception {
        denyRequirePermission(ReportPermissionGuard.PAGE_CODE, PermissionAction.VIEW);

        mockMvc.perform(get("/accounting/reports/account-statement")
                        .param("asOfDate", "2026-06-30")
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000101")
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isForbidden());
    }

    private void seedFixtures() {
        seedPosted("AST-AR-A-OPEN", LocalDate.of(2026, 5, 31), "A 외상매출 발생",
                line("110", "10000.00", "0.00", RECEIVABLE_A_ID, "A 매출채권"),
                line("401", "0.00", "10000.00", COUNTER_ID, "매출"));
        seedPosted("AST-AR-A-COLLECT", LocalDate.of(2026, 6, 10), "A 일부 회수",
                line("101", "3000.00", "0.00", COUNTER_ID, "보통예금"),
                line("110", "0.00", "3000.00", RECEIVABLE_A_ID, "A 회수"));
        seedPosted("AST-AR-B", LocalDate.of(2026, 6, 15), "B 외상매출 발생",
                line("110", "5000.00", "0.00", RECEIVABLE_B_ID, "B 매출채권"),
                line("401", "0.00", "5000.00", COUNTER_ID, "매출"));
        seedPosted("AST-AR-UNRESOLVED", LocalDate.of(2026, 6, 15), "미조회 외상매출 발생",
                line("110", "700.00", "0.00", UNRESOLVED_ID, "미조회 매출채권"),
                line("401", "0.00", "700.00", COUNTER_ID, "미조회 매출"));
        seedPosted("AST-AP-ETC-1", LocalDate.of(2026, 6, 16), "기타 외상매입 1",
                line("500", "400.00", "0.00", COUNTER_ID, "기타 매입 1"),
                line("201", "0.00", "400.00", null, "기타 매입채무 1"));
        seedPosted("AST-AP-ETC-2", LocalDate.of(2026, 6, 17), "기타 외상매입 2",
                line("500", "600.00", "0.00", COUNTER_ID, "기타 매입 2"),
                line("201", "0.00", "600.00", null, "기타 매입채무 2"));
        seedPosted("AST-AP-C-OPEN", LocalDate.of(2026, 6, 20), "C 외상매입 발생",
                line("500", "8000.00", "0.00", COUNTER_ID, "매입"),
                line("201", "0.00", "8000.00", PAYABLE_C_ID, "C 매입채무"));
        seedPosted("AST-AP-C-PAY", LocalDate.of(2026, 6, 21), "C 일부 지급",
                line("201", "2000.00", "0.00", PAYABLE_C_ID, "C 지급"),
                line("101", "0.00", "2000.00", COUNTER_ID, "보통예금"));
        seedPosted("AST-AFTER-BOUNDARY", LocalDate.of(2026, 7, 1), "기준일 이후 제외",
                line("110", "9999.00", "0.00", RECEIVABLE_A_ID, "기준일 이후"),
                line("401", "0.00", "9999.00", COUNTER_ID, "기준일 이후 매출"));
        seedDraft("AST-DRAFT-IGNORED", LocalDate.of(2026, 6, 22), "미게시 제외",
                line("110", "999.00", "0.00", RECEIVABLE_B_ID, "미게시"),
                line("401", "0.00", "999.00", COUNTER_ID, "미게시 매출"));
    }

    private void seedPosted(String journalNo, LocalDate date, String description, LineSpec... specs) {
        Journal journal = journal(journalNo, date, description, specs);
        journal.post("account-statement-it");
        journalRepository.saveAndFlush(journal);
    }

    private void seedDraft(String journalNo, LocalDate date, String description, LineSpec... specs) {
        journalRepository.saveAndFlush(journal(journalNo, date, description, specs));
    }

    private Journal journal(String journalNo, LocalDate date, String description, LineSpec... specs) {
        Journal journal = Journal.create(journalNo, date, description, JournalSourceType.MANUAL, null);
        int lineNo = 1;
        for (LineSpec spec : specs) {
            journal.addLine(JournalLine.create(
                    journal,
                    lineNo++,
                    spec.accountCode(),
                    new BigDecimal(spec.debit()),
                    new BigDecimal(spec.credit()),
                    spec.partnerId(),
                    spec.memo()
            ));
        }
        return journal;
    }

    private LineSpec line(String accountCode, String debit, String credit, UUID partnerId, String memo) {
        return new LineSpec(accountCode, debit, credit, partnerId, memo);
    }

    private JsonNode findAccount(JsonNode data, String accountCode) {
        for (JsonNode group : data.get("groups")) {
            for (JsonNode account : group.get("accounts")) {
                if (accountCode.equals(account.get("accountCode").asText())) {
                    return account;
                }
            }
        }
        throw new AssertionError("계정을 찾지 못했습니다: " + accountCode);
    }

    private JsonNode findGroup(JsonNode data, String groupCode) {
        for (JsonNode group : data.get("groups")) {
            if (groupCode.equals(group.get("groupCode").asText())) {
                return group;
            }
        }
        throw new AssertionError("그룹을 찾지 못했습니다: " + groupCode);
    }

    private void assertAccountMissing(JsonNode data, String accountCode) {
        for (JsonNode group : data.get("groups")) {
            for (JsonNode account : group.get("accounts")) {
                if (accountCode.equals(account.get("accountCode").asText())) {
                    throw new AssertionError("제외되어야 할 계정이 포함되었습니다: " + accountCode);
                }
            }
        }
    }

    private JsonNode findLine(JsonNode data, String accountCode, String partnerName) {
        for (JsonNode group : data.get("groups")) {
            for (JsonNode account : group.get("accounts")) {
                for (JsonNode line : account.get("lines")) {
                    if (accountCode.equals(line.get("accountCode").asText())
                            && partnerName.equals(line.get("partnerName").asText())) {
                        return line;
                    }
                }
            }
        }
        throw new AssertionError("계정명세서 라인을 찾지 못했습니다: " + accountCode + " / " + partnerName);
    }

    private void assertLineCount(JsonNode data, String accountCode, String partnerName, int expected) {
        int count = 0;
        for (JsonNode group : data.get("groups")) {
            for (JsonNode account : group.get("accounts")) {
                for (JsonNode line : account.get("lines")) {
                    if (accountCode.equals(line.get("accountCode").asText())
                            && partnerName.equals(line.get("partnerName").asText())) {
                        count++;
                    }
                }
            }
        }
        if (count != expected) {
            throw new AssertionError("라인 수 불일치 expected=" + expected + ", actual=" + count);
        }
    }

    private void assertAmount(JsonNode node, String expected) {
        BigDecimal actual = node.decimalValue();
        BigDecimal expectedAmount = new BigDecimal(expected);
        if (actual.compareTo(expectedAmount) != 0) {
            throw new AssertionError("금액 불일치 expected=" + expectedAmount + ", actual=" + actual);
        }
    }

    private void assertText(JsonNode node, String expected) {
        if (!expected.equals(node.asText())) {
            throw new AssertionError("문자열 불일치 expected=" + expected + ", actual=" + node.asText());
        }
    }

    private PartnerSummary partner(UUID id, String partnerCode, String name) {
        return new PartnerSummary(id, partnerCode, name, null, null);
    }

    private record LineSpec(String accountCode, String debit, String credit, UUID partnerId, String memo) {
    }
}
