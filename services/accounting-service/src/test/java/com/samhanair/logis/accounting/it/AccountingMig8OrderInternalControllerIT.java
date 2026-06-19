package com.samhanair.logis.accounting.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.accounting.AccountingServiceApplication;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** MIG-8 이관 주문 내부 export API 계약 IT. */
@SpringBootTest(classes = AccountingServiceApplication.class)
@AutoConfigureMockMvc
class AccountingMig8OrderInternalControllerIT extends AbstractPostgresIT {

    private static final String URL = "/internal/accounting/mig8-orders";
    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000006a01");
    private static final UUID PARTNER_ID = UUID.fromString("00000000-0000-0000-0000-000000006a02");
    private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000006a03");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockBean(classes = com.samhanair.logis.security.permission.DynamicPermissionClient.class)
    private DynamicPermissionClient dynamicPermissionClient;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM order_lines");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    void mig8_orders_without_token_returns_401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mig8_orders_with_forged_user_header_without_token_returns_401() throws Exception {
        mockMvc.perform(get(URL)
                        .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                        .header("X-User-Role", "MASTER"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mig8_orders_with_invalid_token_returns_401() throws Exception {
        mockMvc.perform(get(URL)
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mig8_orders_empty_page_returns_200() throws Exception {
        mockMvc.perform(get(URL)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.size").value(200));
    }

    @Test
    void mig8_orders_export_order_and_lines_with_page_shape() throws Exception {
        seedMig8Order();
        seedNonMig8Order();

        mockMvc.perform(get(URL)
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[0].orderNo").value("2026/06/20-1"))
                .andExpect(jsonPath("$.data.content[0].partnerId").value(PARTNER_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].partnerName").value("삼한 테스트 거래처"))
                .andExpect(jsonPath("$.data.content[0].managerName").value("김담당"))
                .andExpect(jsonPath("$.data.content[0].progressStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].validUntil").value("2026-07-20"))
                .andExpect(jsonPath("$.data.content[0].paymentTerms").value("월말 결제"))
                .andExpect(jsonPath("$.data.content[0].reference").value("이관 주문"))
                .andExpect(jsonPath("$.data.content[0].totalSupplyAmount").value(10000.00))
                .andExpect(jsonPath("$.data.content[0].totalVatAmount").value(1000.00))
                .andExpect(jsonPath("$.data.content[0].linkedSlipNo").value("S-20260620-001"))
                .andExpect(jsonPath("$.data.content[0].externalRef").value("hash-1"))
                .andExpect(jsonPath("$.data.content[0].lines.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.data.content[0].lines[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].lines[0].itemName").value("테스트 품목 A"))
                .andExpect(jsonPath("$.data.content[0].lines[0].quantity").value(2.000))
                .andExpect(jsonPath("$.data.content[0].lines[0].unitPrice").value(5000.00))
                .andExpect(jsonPath("$.data.content[0].lines[0].supplyAmount").value(10000.00))
                .andExpect(jsonPath("$.data.content[0].lines[0].vatAmount").value(1000.00))
                .andExpect(jsonPath("$.data.content[0].lines[0].itemDueDate").value("2026-07-10"))
                .andExpect(jsonPath("$.data.content[0].lines[1].lineNo").value(2))
                .andExpect(jsonPath("$.data.content[0].lines[1].productId").doesNotExist());
    }

    private void seedMig8Order() {
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, order_no, partner_id, partner_name, manager_name, valid_until,
                    payment_terms, reference, progress_status, total_supply_amount,
                    total_vat_amount, linked_slip_no, external_ref, kind,
                    created_at, created_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, FALSE)
                """, ORDER_ID, "2026/06/20-1", PARTNER_ID, "삼한 테스트 거래처", "김담당",
                LocalDate.of(2026, 7, 20), "월말 결제", "이관 주문", "COMPLETED",
                new BigDecimal("10000.00"), new BigDecimal("1000.00"), "S-20260620-001",
                "hash-1", "ECOUNT_MIG8", "test");
        jdbcTemplate.update("""
                INSERT INTO order_lines (
                    id, order_id, line_no, product_id, item_name, quantity, unit_price,
                    supply_amount, vat_amount, item_due_date, created_at, created_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, FALSE)
                """, UUID.fromString("00000000-0000-0000-0000-000000006b01"), ORDER_ID, 2,
                null, "테스트 품목 B", new BigDecimal("1.000"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), null, "test");
        jdbcTemplate.update("""
                INSERT INTO order_lines (
                    id, order_id, line_no, product_id, item_name, quantity, unit_price,
                    supply_amount, vat_amount, item_due_date, created_at, created_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, FALSE)
                """, UUID.fromString("00000000-0000-0000-0000-000000006b02"), ORDER_ID, 1,
                PRODUCT_ID, "테스트 품목 A", new BigDecimal("2.000"), new BigDecimal("5000.00"),
                new BigDecimal("10000.00"), new BigDecimal("1000.00"),
                LocalDate.of(2026, 7, 10), "test");
    }

    private void seedNonMig8Order() {
        jdbcTemplate.update("""
                INSERT INTO orders (
                    id, order_no, partner_id, partner_name, progress_status,
                    total_supply_amount, total_vat_amount, external_ref, kind,
                    created_at, created_by, is_deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, FALSE)
                """, UUID.fromString("00000000-0000-0000-0000-000000006c01"), "2026/06/20-2",
                UUID.fromString("00000000-0000-0000-0000-000000006c02"), "비이관 거래처",
                "PENDING", BigDecimal.ZERO, BigDecimal.ZERO, "hash-2", "MANUAL", "test");
    }
}
