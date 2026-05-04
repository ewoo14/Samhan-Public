package com.samhanair.logis.slip.it;

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
import com.samhanair.logis.slip.notification.SmsGateway;
import com.samhanair.logis.slip.notification.SmsResult;
import com.samhanair.logis.slip.repository.DeliveryBatchRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * Slice B 공개 모바일 endpoint — no-auth (batchToken 만 검증) IT.
 *
 * <p>BE endpoint 가정 (plan §4.2):
 * <ul>
 *   <li>{@code GET /public/batches/{token}}                       — no auth, 200 / 404 / 410</li>
 *   <li>{@code GET /public/batches/{token}/slips/{slipId}}        — no auth, 200 / 404 / 410</li>
 *   <li>{@code POST /public/batches/{token}/slips/{slipId}/signature} — Slice C 후속 (본 IT 미검증)</li>
 * </ul>
 *
 * <p>API Gateway: {@code /public/**} 인증 우회 (SecurityConfig {@code permitAll}).
 *
 * <p>회고 가드 ({@code feedback_uuid_no_user_visibility}) — 공개 응답 jsonPath 에:
 * <ul>
 *   <li>{@code $.data.id} (batch UUID) — 노출 금지</li>
 *   <li>{@code $.data.slips[*].id} (slip UUID) — 노출 금지</li>
 *   <li>{@code $.data.slips[*].lines[*].productId} (product UUID) — 노출 금지</li>
 *   <li>허용: slipNo (비즈니스 식별자), productName, modelName, partnerName, quantity</li>
 * </ul>
 */
@SpringBootTest(classes = SlipServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class PublicSlipControllerIT extends AbstractPostgresIT {

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

    /** Plan §3.2: tokenExpiresAt 강제 갱신용 — 만료 시나리오. */
    @Autowired
    private DeliveryBatchRepository deliveryBatchRepository;

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

    private String createSlipAndAutoGroup(String date, String driverName, String driverPhone)
            throws Exception {
        Map<String, Object> line = new HashMap<>();
        line.put("productId", UUID.randomUUID().toString());
        line.put("productName", "에어컨");
        line.put("modelName", "MOD-AC1");
        line.put("quantity", 1);
        line.put("unitPrice", 100000);

        Map<String, Object> body = new HashMap<>();
        body.put("slipType", "OUTBOUND");
        body.put("slipDate", date);
        body.put("sourceWarehouseId", UUID.randomUUID().toString());
        body.put("destinationWarehouseId", UUID.randomUUID().toString());
        body.put("partnerId", UUID.randomUUID().toString());
        body.put("partnerName", "공개페이지 거래처");
        body.put("deliveryTag", "DAY");
        body.put("memo", "Slice B 공개 페이지 테스트");
        body.put("driverName", driverName);
        body.put("driverPhone", driverPhone);
        body.put("lines", List.of(line));

        MvcResult created = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        String slipId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asText();

        mockMvc.perform(post("/slips/" + slipId + "/save")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "SALES")).andExpect(status().isOk());

        // auto-group → batch 1건.
        MvcResult groupResult = mockMvc.perform(post("/delivery-batches/auto-group")
                        .param("date", date)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(groupResult.getResponse().getContentAsString())
                .get("data").get(0).get("batchToken").asText();
    }

    /**
     * 시나리오 12 — 유효 토큰 → 200 + 슬립 N건.
     * UUID 비공개 가드 — slip.id / batch.id / line.productId 모두 응답에서 제거 검증.
     */
    @Test
    void validToken_returnsBatchAndSlips() throws Exception {
        String date = "2026-05-17";
        String token = createSlipAndAutoGroup(date, "공개기사", "010-2424-3535");

        MvcResult result = mockMvc.perform(get("/public/batches/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driverName").value("공개기사"))
                .andExpect(jsonPath("$.data.batchDate").value(notNullValue()))
                .andExpect(jsonPath("$.data.slips").value(notNullValue()))
                .andReturn();

        // UUID 비공개 가드.
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        org.assertj.core.api.Assertions.assertThat(data.has("id"))
                .as("batch UUID 노출 금지 — feedback_uuid_no_user_visibility")
                .isFalse();
        for (JsonNode slip : data.get("slips")) {
            org.assertj.core.api.Assertions.assertThat(slip.has("id"))
                    .as("slip UUID 노출 금지").isFalse();
            // slipNo 는 비즈니스 식별자 — 노출 허용.
            org.assertj.core.api.Assertions.assertThat(slip.has("slipNo")).isTrue();
            if (slip.has("lines")) {
                for (JsonNode line : slip.get("lines")) {
                    org.assertj.core.api.Assertions.assertThat(line.has("productId"))
                            .as("product UUID 노출 금지").isFalse();
                }
            }
        }
    }

    /**
     * 시나리오 13 — 만료된 토큰 → 410 GONE (plan N5: 배송일 +1일 자동 만료).
     * tokenExpiresAt 을 강제로 과거로 set 후 GET → 410.
     */
    @Test
    void expiredToken_returns410() throws Exception {
        String date = "2026-05-18";
        String token = createSlipAndAutoGroup(date, "만료기사", "010-2525-3636");

        // tokenExpiresAt 을 1시간 전으로 강제 갱신.
        deliveryBatchRepository.findByBatchToken(token).ifPresent(batch -> {
            batch.expireToken(LocalDateTime.now().minusHours(1));
            deliveryBatchRepository.save(batch);
        });

        mockMvc.perform(get("/public/batches/" + token))
                .andExpect(status().isGone());
    }

    /**
     * 시나리오 14 — 임의 토큰 → 404.
     */
    @Test
    void invalidToken_returns404() throws Exception {
        mockMvc.perform(get("/public/batches/" + "totally-invalid-token-zzz-9999"))
                .andExpect(status().isNotFound());
    }
}
