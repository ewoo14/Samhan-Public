package com.samhanair.logis.slip.publish;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.slip.SlipServiceApplication;
import com.samhanair.logis.slip.client.InventoryClient;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.ProductSummary;
import com.samhanair.logis.slip.it.AbstractPostgresIT;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 6 M5 (slip-service-integration) — 통합 발행 endpoint IT.
 *
 * <p>커버리지:
 * <ul>
 *   <li>happy path — {@code POST /api/v1/slips/from-estimate} 201 + slipNo 응답</li>
 *   <li>happy path — {@code POST /api/v1/slips/from-partner-order} 201</li>
 *   <li>idempotency — 같은 키 + 같은 본문 → 200 + replay flag + 동일 slipNo</li>
 *   <li>idempotency — 같은 키 + 다른 본문 → 409 Conflict</li>
 *   <li>{@code GET /api/v1/slips/by-source} — sourceType + sourceId 조회</li>
 *   <li>warehouseCode 매핑 누락 → 400</li>
 *   <li>인증 누락 → 403</li>
 * </ul>
 *
 * <p>외부 client 격리 ({@code feedback_it_mockbean_external_clients.md}):
 * <ul>
 *   <li>{@link ProductClient} — lookupByModel 가 가짜 ProductSummary 반환</li>
 *   <li>{@link InventoryClient} — 발행만 검증, accept/complete 호출 X 이므로 사용 안 됨 (lenient mock)</li>
 * </ul>
 *
 * <p>{@code @TestPropertySource} 로 warehouse-code-map 주입 (yaml 의 dev 기본값과 동일).
 */
@SpringBootTest(classes = SlipServiceApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.publish.warehouse-code-map.00003=11111111-1111-1111-1111-111111111111",
        "app.publish.warehouse-code-map.2=22222222-2222-2222-2222-222222222222",
        "app.publish.warehouse-code-map.14=33333333-3333-3333-3333-333333333333",
        "app.publish.warehouse-code-map.1=44444444-4444-4444-4444-444444444444"
})
class SlipPublishControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private InventoryClient inventoryClient;

    @BeforeEach
    void setupMocks() {
        // lookupByModel — 모든 productCode 에 대해 가짜 ProductSummary 반환.
        Mockito.lenient().when(productClient.lookupByModel(ArgumentMatchers.anyString()))
                .thenAnswer(inv -> new ProductSummary(
                        UUID.randomUUID(), "테스트 제품", inv.getArgument(0, String.class),
                        UUID.randomUUID(), new BigDecimal("100000"), "ACTIVE"));
        // 기존 lookup/requireExists 도 IT 실패 방지용 lenient 처리 (publish 경로는 사용 X).
        Mockito.lenient().when(productClient.lookup(ArgumentMatchers.anyList()))
                .thenReturn(List.of());
    }

    // ---------------- happy path: from-estimate ----------------

    @Test
    void publishFromEstimate_returns201_andSlipNo() throws Exception {
        Map<String, Object> body = estimateBody("EST-2026-0001");

        mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .header("Idempotency-Key", "idem-est-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slipId").value(notNullValue()))
                .andExpect(jsonPath("$.data.slipNo").value(notNullValue()))
                .andExpect(jsonPath("$.data.sourceType").value("ESTIMATE"))
                .andExpect(jsonPath("$.data.sourceId").value("EST-2026-0001"))
                .andExpect(jsonPath("$.data.idempotencyKey").value("idem-est-001"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));
    }

    // ---------------- happy path: from-partner-order ----------------

    @Test
    void publishFromPartnerOrder_returns201() throws Exception {
        Map<String, Object> body = partnerOrderBody("PO-2026-0001");

        mockMvc.perform(post("/api/v1/slips/from-partner-order")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER")
                        .header("Idempotency-Key", "idem-po-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sourceType").value("PARTNER_ORDER"))
                .andExpect(jsonPath("$.data.sourceId").value("PO-2026-0001"));
    }

    // ---------------- idempotency: same key + same body → 200 replay ----------------

    @Test
    void sameIdempotencyKey_sameBody_returns200_withReplayFlag_andSameSlipNo() throws Exception {
        Map<String, Object> body = estimateBody("EST-IDEM-001");
        String idemKey = "idem-replay-test";

        // 1차 호출 — 201
        MvcResult first = mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String firstSlipNo = readSlipNo(first);

        // 2차 호출 — 같은 키 + 같은 본문 → 200 + 같은 slipNo + replay=true
        MvcResult second = mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andReturn();
        String secondSlipNo = readSlipNo(second);

        org.assertj.core.api.Assertions.assertThat(secondSlipNo).isEqualTo(firstSlipNo);
    }

    // ---------------- idempotency: same key + different body → 409 ----------------

    @Test
    void sameIdempotencyKey_differentBody_returns409() throws Exception {
        Map<String, Object> body1 = estimateBody("EST-DIFF-001");
        String idemKey = "idem-conflict-test";

        mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isCreated());

        // 같은 키 + 다른 본문 (수량 변경) → 409
        Map<String, Object> body2 = estimateBody("EST-DIFF-001");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body2.get("lines");
        lines.get(0).put("qty", "999");

        mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isConflict());
    }

    // ---------------- by-source 조회 ----------------

    @Test
    void getBySource_returnsAllMatching() throws Exception {
        Map<String, Object> body = estimateBody("EST-LOOKUP-001");

        mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .header("Idempotency-Key", "idem-lookup-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/slips/by-source")
                        .param("sourceType", "ESTIMATE")
                        .param("sourceId", "EST-LOOKUP-001")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceId").value("EST-LOOKUP-001"))
                .andExpect(jsonPath("$.data[0].sourceType").value("ESTIMATE"));
    }

    // ---------------- guards ----------------

    @Test
    void publishFromEstimate_unmappedWarehouseCode_returns400() throws Exception {
        Map<String, Object> body = estimateBody("EST-UNMAPPED");
        body.put("warehouseCode", "99999"); // 매핑 누락

        mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .header("Idempotency-Key", "idem-unmapped")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishFromEstimate_unauthenticated_returns403() throws Exception {
        Map<String, Object> body = estimateBody("EST-NOAUTH");

        mockMvc.perform(post("/api/v1/slips/from-estimate")
                        .header("Idempotency-Key", "idem-noauth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ---------------- helpers ----------------

    private Map<String, Object> estimateBody(String estimateNumber) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("lineNo", 1);
        line.put("productCode", "MOD-220V-4HP");
        line.put("productName", "에어컨");
        line.put("spec", "220V 4HP");
        line.put("qty", "2");
        line.put("unitPriceExVat", 100000);
        line.put("unitPriceVat", 110000);
        line.put("supplyAmount", 200000);
        line.put("vatAmount", 20000);
        line.put("remarks", "라인 메모");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("estimateNumber", estimateNumber);
        body.put("ioDate", "20260504");
        body.put("partnerCode", "CUST-0001");
        body.put("partnerName", "테스트 거래처");
        body.put("employeeCode", "EMP-0001");
        body.put("warehouseCode", "00003");
        body.put("ioType", "10");
        body.put("shippingAddress", "서울 강남구");
        body.put("inspectionAddress", "서울 강남구 검수");
        body.put("receiverPhone", "010-0000-0000");
        body.put("memo", "급송");
        body.put("paymentDueLabel", "익월말 결제");
        body.put("discountInfo", "5% 할인");
        body.put("lines", new java.util.ArrayList<>(List.of(line)));
        return body;
    }

    private Map<String, Object> partnerOrderBody(String partnerOrderId) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("lineNo", 1);
        line.put("productCode", "MOD-220V-4HP");
        line.put("productName", "에어컨");
        line.put("spec", "220V 4HP");
        line.put("qty", "3");
        line.put("unitPriceExVat", 100000);
        line.put("unitPriceVat", 110000);
        line.put("supplyAmount", 300000);
        line.put("vatAmount", 30000);
        line.put("remarks", "PO 라인");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerOrderId", partnerOrderId);
        body.put("ioDate", "20260504");
        body.put("partnerCode", "CUST-0002");
        body.put("partnerName", "협력사");
        body.put("employeeCode", "EMP-0002");
        body.put("warehouseCode", "00003");
        body.put("shippingAddress", "경기 성남시");
        body.put("receiverPhone", "010-1111-1111");
        body.put("memo", "PO 메모");
        body.put("paymentDueLabel", "월말 결제");
        body.put("discountInfo", "");
        body.put("orderApprovedAt", "2026-05-04T10:00:00");
        body.put("lines", new java.util.ArrayList<>(List.of(line)));
        return body;
    }

    private String readSlipNo(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("slipNo").asText();
    }
}
