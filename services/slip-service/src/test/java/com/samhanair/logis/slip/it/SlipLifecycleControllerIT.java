package com.samhanair.logis.slip.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.slip.SlipServiceApplication;
import com.samhanair.logis.slip.client.InventoryClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출고 전표 풀 9단계 라이프사이클 + 입고 전표 ship/deliver 스킵 검증 + slipNo 형식 검증.
 *
 * <p>출고 라이프사이클 (BE PM 명시):
 * DRAFT → SAVED → SENT → ACCEPTED → PROCESSING → COMPLETED → SHIPPING → DELIVERED → CONFIRMED.
 *
 * <p>입고 라이프사이클: ship/deliver 스킵 → COMPLETED 후 바로 CONFIRMED.
 * 입고 전표에 ship() 호출 시 → 409 (직전 상태 COMPLETED 가 아닌 SHIPPING 으로의 전이 불가).
 *
 * <p>slipNo 형식: `YYYY/MM/DD-N` (예: `2026/05/04-1`).
 */
@SpringBootTest(classes = SlipServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class SlipLifecycleControllerIT extends AbstractPostgresIT {

    private static final Pattern SLIP_NO_PATTERN =
            Pattern.compile("^\\d{4}/\\d{2}/\\d{2}-\\d+$");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryClient inventoryClient;

    private Map<String, Object> outboundBody() {
        return slipBody("OUTBOUND", true);
    }

    private Map<String, Object> inboundBody() {
        return slipBody("INBOUND", false);
    }

    private Map<String, Object> slipBody(String slipType, boolean withSource) {
        Map<String, Object> line = new HashMap<>();
        line.put("productId", UUID.randomUUID().toString());
        line.put("productName", "테스트 제품");
        line.put("modelName", "MOD-001");
        line.put("quantity", 5);
        line.put("unitPrice", 100000);
        line.put("note", "라인");

        Map<String, Object> body = new HashMap<>();
        body.put("slipType", slipType);
        body.put("slipDate", "2026-05-04");
        if (withSource) {
            body.put("sourceWarehouseId", UUID.randomUUID().toString());
        }
        body.put("destinationWarehouseId", UUID.randomUUID().toString());
        body.put("partnerId", UUID.randomUUID().toString());
        body.put("partnerName", "테스트 거래처");
        body.put("deliveryTag", "DAY");
        body.put("memo", "메모");
        body.put("lines", List.of(line));
        return body;
    }

    @Test
    void outbound_fullLifecycle_DraftToConfirmed() throws Exception {
        // 1) DRAFT 생성 (SALES).
        MvcResult created = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outboundBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        String slipId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // 2) SAVED (SALES).
        mockMvc.perform(post("/slips/" + slipId + "/save")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SAVED"));

        // 3) SENT (SALES).
        mockMvc.perform(post("/slips/" + slipId + "/send")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        // 4) ACCEPTED (WAREHOUSE) — InventoryClient.reserve 호출.
        mockMvc.perform(post("/slips/" + slipId + "/accept")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        // 5) PROCESSING (WAREHOUSE).
        mockMvc.perform(post("/slips/" + slipId + "/process")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        // 6) COMPLETED (WAREHOUSE) — InventoryClient.deduct(fromReservation=true).
        mockMvc.perform(post("/slips/" + slipId + "/complete")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 7) SHIPPING (WAREHOUSE).
        mockMvc.perform(post("/slips/" + slipId + "/ship")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPING"));

        // 8) DELIVERED (WAREHOUSE).
        mockMvc.perform(post("/slips/" + slipId + "/deliver")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));

        // 9) CONFIRMED (ACCOUNTANT).
        mockMvc.perform(post("/slips/" + slipId + "/confirm")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void inbound_lifecycle_skipsShipDeliver() throws Exception {
        // 입고 전표 생성 (sourceWarehouseId 없음).
        MvcResult created = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inboundBody())))
                .andExpect(status().isCreated())
                .andReturn();
        String slipId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // save → send → accept → process → complete (입고 normal path).
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
                .header("X-User-Role", "WAREHOUSE")).andExpect(status().isOk());

        // 입고전표 ship() 시도 → 409 (입고는 ship/deliver 단계 스킵).
        mockMvc.perform(post("/slips/" + slipId + "/ship")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE"))
                .andExpect(status().isConflict());

        // 입고전표는 COMPLETED → 바로 CONFIRMED 가능.
        mockMvc.perform(post("/slips/" + slipId + "/confirm")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ACCOUNTANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void slipNumber_format_isYyyyMmDdSeq() throws Exception {
        // 응답의 slipNo 가 YYYY/MM/DD-N 정규식 매칭 여부 검증.
        MvcResult result = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outboundBody())))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data");
        String slipNo = data.get("slipNo").asText();

        Assertions.assertThat(slipNo)
                .as("slipNo 형식은 YYYY/MM/DD-N 이어야 한다 — 실제: %s", slipNo)
                .matches(SLIP_NO_PATTERN);
    }
}
