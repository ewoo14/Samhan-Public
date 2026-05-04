package com.samhanair.logis.slip.it;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.slip.SlipServiceApplication;
import com.samhanair.logis.slip.client.InventoryClient;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.ProductSummary;
import com.samhanair.logis.slip.notification.SmsGateway;
import com.samhanair.logis.slip.notification.SmsResult;
import java.math.BigDecimal;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slice B — Slip.driverName / driverPhone 필드 확장 round-trip + 회귀 0 가드.
 *
 * <p>Plan §3.1: Slip 신규 필드:
 * <ul>
 *   <li>{@code driverName VARCHAR(50) nullable}</li>
 *   <li>{@code driverPhone VARCHAR(20) nullable} — E.164 정규화</li>
 *   <li>{@code deliveryBatchId UUID nullable}</li>
 * </ul>
 *
 * <p>{@code Slip.editHeader()} 시그니처: 4 → 6 args (driverName/driverPhone 추가).
 * 기존 라이프사이클 메서드 (save/send/accept/process/inspect/complete/...) 무변경 →
 * {@link SlipInspectControllerIT} 8개 시나리오 회귀 0 명시.
 */
@SpringBootTest(classes = SlipServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class SlipDriverFieldsIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private SmsGateway smsGateway;

    @BeforeEach
    void mockExternalClients() {
        Mockito.lenient().when(productClient.lookup(ArgumentMatchers.anyList()))
                .thenAnswer(inv -> {
                    List<UUID> ids = inv.getArgument(0);
                    return ids.stream()
                            .map(id -> new ProductSummary(id, "테스트 제품", "MOD-001",
                                    UUID.randomUUID(), new BigDecimal("100000"), "ACTIVE"))
                            .toList();
                });
        Mockito.lenient().when(productClient.requireExists(ArgumentMatchers.any()))
                .thenAnswer(inv -> new ProductSummary(
                        inv.getArgument(0), "테스트 제품", "MOD-001",
                        UUID.randomUUID(), new BigDecimal("100000"), "ACTIVE"));
        Mockito.lenient().when(smsGateway.sendSms(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(SmsResult.success("MOCK-001"));
    }

    private Map<String, Object> outboundBody() {
        Map<String, Object> line = new HashMap<>();
        line.put("productId", UUID.randomUUID().toString());
        line.put("productName", "테스트 제품");
        line.put("modelName", "MOD-001");
        line.put("quantity", 1);
        line.put("unitPrice", 100000);

        Map<String, Object> body = new HashMap<>();
        body.put("slipType", "OUTBOUND");
        body.put("slipDate", "2026-05-04");
        body.put("sourceWarehouseId", UUID.randomUUID().toString());
        body.put("destinationWarehouseId", UUID.randomUUID().toString());
        body.put("partnerId", UUID.randomUUID().toString());
        body.put("partnerName", "테스트 거래처");
        body.put("deliveryTag", "DAY");
        body.put("memo", "Slice B driver round-trip");
        body.put("lines", List.of(line));
        return body;
    }

    /**
     * 시나리오 15 — POST 시 driverName/driverPhone 함께 저장 후 GET 응답에서 round-trip.
     */
    @Test
    void createSlip_withDriverFields_persistsRoundTrip() throws Exception {
        Map<String, Object> body = outboundBody();
        body.put("driverName", "김기사");
        body.put("driverPhone", "010-1234-5678");

        MvcResult created = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.driverName").value("김기사"))
                .andExpect(jsonPath("$.data.driverPhone").value("010-1234-5678"))
                .andReturn();

        String slipId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // GET 으로 재조회 round-trip.
        mockMvc.perform(get("/slips/" + slipId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driverName").value("김기사"))
                .andExpect(jsonPath("$.data.driverPhone").value("010-1234-5678"));
    }

    /**
     * 시나리오 16 — editHeader (PATCH /slips/{id}) 6-args 확장 검증.
     * DRAFT → editHeader (driverName/driverPhone 포함 6 fields) → 응답에 갱신된 값.
     */
    @Test
    void editHeader_with6Args_persistsDriverFields() throws Exception {
        // 사전: driver 필드 없이 생성.
        MvcResult created = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outboundBody())))
                .andExpect(status().isCreated())
                .andReturn();
        String slipId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asText();

        Map<String, Object> editBody = new HashMap<>();
        editBody.put("partnerName", "수정된 거래처");
        editBody.put("deliveryTag", "STACK");
        editBody.put("memo", "수정된 메모");
        editBody.put("slipDate", "2026-05-05");
        editBody.put("driverName", "박기사");
        editBody.put("driverPhone", "010-9876-5432");

        mockMvc.perform(patch("/slips/" + slipId + "/header")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driverName").value("박기사"))
                .andExpect(jsonPath("$.data.driverPhone").value("010-9876-5432"))
                .andExpect(jsonPath("$.data.partnerName").value("수정된 거래처"));
    }

    /**
     * 시나리오 17 — driverName/driverPhone 없이 (null) 생성도 허용 (nullable 필드).
     * 회귀 가드 — 기존 SlipController 시나리오 호환.
     */
    @Test
    void createSlip_withoutDriverFields_nullAccepted() throws Exception {
        MvcResult created = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outboundBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn();

        // null 또는 미존재 — 둘 다 허용.
        com.fasterxml.jackson.databind.JsonNode data =
                objectMapper.readTree(created.getResponse().getContentAsString()).get("data");
        boolean nameNullOrMissing =
                !data.has("driverName") || data.get("driverName").isNull();
        org.assertj.core.api.Assertions.assertThat(nameNullOrMissing).isTrue();
    }

    /**
     * 시나리오 18 — inspect endpoint 회귀 가드. driver 필드 추가가 inspect 라이프사이클 영향 0 검증.
     * (SlipInspectControllerIT 8 시나리오 회귀 0 명시 — 이 IT 1건이 빠른 smoke 회귀 역할.)
     */
    @Test
    void inspectStillWorks_unaffectedByDriverFields() throws Exception {
        Map<String, Object> body = outboundBody();
        body.put("driverName", "회귀기사");
        body.put("driverPhone", "010-0000-0000");

        MvcResult created = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String slipId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // 풀 라이프사이클 — driver 필드 존재해도 INSPECTING/COMPLETED 정상 전이.
        mockMvc.perform(post("/slips/" + slipId + "/save")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "SALES")).andExpect(status().isOk());
        mockMvc.perform(post("/slips/" + slipId + "/send")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "SALES")).andExpect(status().isOk());
        mockMvc.perform(post("/slips/" + slipId + "/accept")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "WAREHOUSE")).andExpect(status().isOk());
        mockMvc.perform(post("/slips/" + slipId + "/process")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "WAREHOUSE")).andExpect(status().isOk());
        mockMvc.perform(post("/slips/" + slipId + "/complete")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INSPECTING"));
        mockMvc.perform(post("/slips/" + slipId + "/inspect")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                // driver 필드도 보존 — 회귀 0.
                .andExpect(jsonPath("$.data.driverName").value("회귀기사"));
    }
}
