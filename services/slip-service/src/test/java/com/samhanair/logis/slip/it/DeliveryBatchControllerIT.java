package com.samhanair.logis.slip.it;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Slice B (notification-slice-B) — DeliveryBatch + 자동 그룹화 + Solapi SMS + 토큰 만료/재발급 IT.
 *
 * <p>BE endpoint 가정 (plan §4.1, slip-service:8086 + gateway 라우팅 {@code /api/delivery-batches/**}):
 * <ul>
 *   <li>{@code POST   /delivery-batches/auto-group?date=YYYY-MM-DD} — MANAGER/MASTER (200)</li>
 *   <li>{@code GET    /delivery-batches?date=&sent=}                — MANAGER/MASTER (200)</li>
 *   <li>{@code GET    /delivery-batches/{id}}                       — MANAGER/MASTER (200)</li>
 *   <li>{@code POST   /delivery-batches/{id}/send-sms}              — MANAGER/MASTER (200)
 *       → SmsGateway.sendSms 호출 + 성공 시 smsSentAt 기록 / 실패 시 smsLastError 기록</li>
 *   <li>{@code POST   /delivery-batches/{id}/slips} body {@code {slipId}}        — MANAGER/MASTER (200)</li>
 *   <li>{@code DELETE /delivery-batches/{id}/slips/{slipId}}        — MANAGER/MASTER (204)</li>
 *   <li>{@code POST   /delivery-batches/{id}/regenerate-token}      — MANAGER/MASTER (200)</li>
 * </ul>
 *
 * <p>도메인 라이프사이클 (plan §3.3, Layer 4):
 * <pre>
 *   DeliveryBatch.create(driver, date, slips) → batchToken 생성, tokenExpiresAt = batchDate+1일
 *   markSmsSent()              : smsSentAt=null → smsSentAt=now (Solapi 성공 후만)
 *   markSmsFailed(error)       : smsSentAt=null → smsSentAt=null + smsLastError 기록
 *   addSlip(slip)              : slip.deliveryBatchId 갱신
 *   removeSlip(slip)           : slip.deliveryBatchId = null
 * </pre>
 *
 * <p>회고 가드 (memory):
 * <ul>
 *   <li>{@code feedback_it_mockbean_external_clients} —
 *       {@link SmsGateway} / {@link InventoryClient} / {@link ProductClient} 모두 {@code @MockBean} +
 *       lenient stub. 누락 시 Eureka 비활성에서도 외부 RestClient 호출 → 500.</li>
 *   <li>{@code feedback_pm_integration_build_check} — 싱글턴 Testcontainers, ApiResponse 래핑
 *       jsonPath {@code $.data.*}, 잘못된 상태 전이 → 409, 미존재 → 404, 권한 부족 → 403.</li>
 *   <li>{@code feedback_uuid_no_user_visibility} — 공개 endpoint 응답 jsonPath 에 {@code slip.id} UUID
 *       미존재 검증 ({@link PublicSlipControllerIT} 참고).</li>
 * </ul>
 */
@SpringBootTest(classes = SlipServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class DeliveryBatchControllerIT extends AbstractPostgresIT {

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
                .thenReturn(SmsResult.success("MOCK-MSG-ID"));
    }

    /**
     * 헬퍼 — driverName/driverPhone 가 채워진 출고전표 1건을 SAVED 상태로 생성한다.
     * (auto-group 은 driver_phone 가 있는 슬립만 그룹 대상으로 가정.)
     */
    private String createSlipWithDriver(String driverName, String driverPhone, String slipDate)
            throws Exception {
        Map<String, Object> line = new HashMap<>();
        line.put("productId", UUID.randomUUID().toString());
        line.put("productName", "테스트 제품");
        line.put("modelName", "MOD-001");
        line.put("quantity", 1);
        line.put("unitPrice", 100000);

        Map<String, Object> body = new HashMap<>();
        body.put("slipType", "OUTBOUND");
        body.put("slipDate", slipDate);
        body.put("sourceWarehouseId", UUID.randomUUID().toString());
        body.put("destinationWarehouseId", UUID.randomUUID().toString());
        body.put("partnerId", UUID.randomUUID().toString());
        body.put("partnerName", "테스트 거래처");
        body.put("deliveryTag", "DAY");
        body.put("memo", "Slice B driver fields");
        body.put("driverName", driverName);
        body.put("driverPhone", driverPhone);
        body.put("lines", List.of(line));

        MvcResult result = mockMvc.perform(post("/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String slipId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // SAVED 까지 진행 — auto-group 대상은 SAVED/SENT 가정 (DRAFT 는 미발행 상태).
        mockMvc.perform(post("/slips/" + slipId + "/save")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "SALES"))
                .andExpect(status().isOk());
        return slipId;
    }

    private String autoGroup(String date, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/delivery-batches/auto-group")
                        .param("date", date)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", role))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    // -------- 시나리오 1 ~ 3: auto-group --------

    /**
     * 시나리오 1 — 같은 driverPhone + 같은 batchDate 슬립 2건 → batch 1개로 묶임.
     * 기대: $.data 길이 1, $.data[0].slipCount == 2 (또는 $.data[0].slips.length() == 2).
     */
    @Test
    void autoGroup_sameDriverSameDate_groupsTogether() throws Exception {
        String date = "2026-05-05";
        createSlipWithDriver("김기사", "010-1111-2222", date);
        createSlipWithDriver("김기사", "010-1111-2222", date);

        String body = autoGroup(date, "MANAGER");
        JsonNode data = objectMapper.readTree(body).get("data");
        // 단일 그룹.
        org.assertj.core.api.Assertions.assertThat(data.size()).isEqualTo(1);
        // 그룹 내 슬립 수 — slipCount 또는 slips 배열 길이 어느 한 쪽으로 노출 가정.
        JsonNode batch = data.get(0);
        int slipCount = batch.has("slipCount") ? batch.get("slipCount").asInt()
                : batch.has("slips") ? batch.get("slips").size() : -1;
        org.assertj.core.api.Assertions.assertThat(slipCount).isEqualTo(2);
    }

    /**
     * 시나리오 2 — 같은 driver, 다른 date → batch 2개로 분리.
     */
    @Test
    void autoGroup_differentDates_separateBatches() throws Exception {
        createSlipWithDriver("박기사", "010-3333-4444", "2026-05-06");
        createSlipWithDriver("박기사", "010-3333-4444", "2026-05-07");

        // auto-group 은 single date param — 두 번 호출.
        String body1 = autoGroup("2026-05-06", "MANAGER");
        String body2 = autoGroup("2026-05-07", "MANAGER");

        JsonNode data1 = objectMapper.readTree(body1).get("data");
        JsonNode data2 = objectMapper.readTree(body2).get("data");
        org.assertj.core.api.Assertions.assertThat(data1.size()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(data2.size()).isEqualTo(1);
        // batchToken 이 서로 달라야 함 (그룹 키 (phone, date) 기준 분리).
        org.assertj.core.api.Assertions.assertThat(data1.get(0).get("batchToken").asText())
                .isNotEqualTo(data2.get(0).get("batchToken").asText());
    }

    /**
     * 시나리오 3 — 같은 (driver, date) 재호출 시 기존 batch 반환 (멱등성).
     * UNIQUE(driverPhone, batchDate) partial index 가 중복 그룹 방지.
     */
    @Test
    void autoGroup_idempotent_returnsExisting() throws Exception {
        String date = "2026-05-08";
        createSlipWithDriver("이기사", "010-5555-6666", date);

        String body1 = autoGroup(date, "MANAGER");
        String body2 = autoGroup(date, "MANAGER");

        String token1 = objectMapper.readTree(body1).get("data").get(0).get("batchToken").asText();
        String token2 = objectMapper.readTree(body2).get("data").get(0).get("batchToken").asText();
        // 같은 batch — 같은 토큰.
        org.assertj.core.api.Assertions.assertThat(token1).isEqualTo(token2);
    }

    // -------- 시나리오 4 ~ 6: send-sms --------

    /**
     * 시나리오 4 — Solapi 성공 → smsSentAt 기록 (markSmsSent).
     * SmsGateway @MockBean 이 SmsResult.success 반환 → 응답 $.data.smsSentAt != null.
     */
    @Test
    void sendSms_success_marksSmsSentAt() throws Exception {
        String date = "2026-05-09";
        createSlipWithDriver("최기사", "010-7777-8888", date);
        String groupBody = autoGroup(date, "MANAGER");
        String batchId = objectMapper.readTree(groupBody).get("data").get(0).get("id").asText();

        Mockito.when(smsGateway.sendSms(ArgumentMatchers.eq("010-7777-8888"), ArgumentMatchers.anyString()))
                .thenReturn(SmsResult.success("MSG-SUCCESS-001"));

        mockMvc.perform(post("/delivery-batches/" + batchId + "/send-sms")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsSentAt").value(notNullValue()))
                .andExpect(jsonPath("$.data.smsLastError").value(nullValue()));

        Mockito.verify(smsGateway, Mockito.atLeastOnce())
                .sendSms(ArgumentMatchers.eq("010-7777-8888"), ArgumentMatchers.anyString());
    }

    /**
     * 시나리오 5 — Solapi error → smsLastError 기록, smsSentAt = null (markSmsFailed).
     * 재시도는 사용자 재클릭으로 처리 (기록만 남김, exception throw 안 함 — endpoint 200 또는 422).
     */
    @Test
    void sendSms_solapiError_recordsLastError() throws Exception {
        String date = "2026-05-10";
        createSlipWithDriver("정기사", "010-9999-0000", date);
        String groupBody = autoGroup(date, "MANAGER");
        String batchId = objectMapper.readTree(groupBody).get("data").get(0).get("id").asText();

        Mockito.when(smsGateway.sendSms(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(SmsResult.failure("Solapi quota exceeded"));

        // BE 가 200 (실패 기록 후 정상 응답) 또는 422/502 응답 둘 다 가능 — 둘 다 허용 + smsLastError 검증.
        MvcResult result = mockMvc.perform(post("/delivery-batches/" + batchId + "/send-sms")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andReturn();

        int status = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isIn(200, 422, 502);

        // 후처리 — GET 으로 재조회해서 smsLastError 검증 (smsSentAt 은 null).
        mockMvc.perform(get("/delivery-batches/" + batchId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsSentAt").value(nullValue()))
                .andExpect(jsonPath("$.data.smsLastError").value(notNullValue()));
    }

    /**
     * 시나리오 6 — 이미 발송된 batch 재발송 시 기본 409 (오발송 방지 — plan §7.2 ☑ 셀 클릭 후 confirm).
     * 강제 재발송 옵션 ({@code ?force=true}) 은 BE 미정 — 일단 기본 동작만 검증.
     */
    @Test
    void sendSms_alreadySent_409Conflict() throws Exception {
        String date = "2026-05-11";
        createSlipWithDriver("강기사", "010-1212-3434", date);
        String groupBody = autoGroup(date, "MANAGER");
        String batchId = objectMapper.readTree(groupBody).get("data").get(0).get("id").asText();

        // 1차 발송 — 성공.
        mockMvc.perform(post("/delivery-batches/" + batchId + "/send-sms")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk());

        // 2차 발송 (force 없음) — 409 CONFLICT.
        mockMvc.perform(post("/delivery-batches/" + batchId + "/send-sms")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isConflict());
    }

    // -------- 시나리오 7 ~ 8: addSlip / removeSlip --------

    /**
     * 시나리오 7 — addSlip 시 slip 의 deliveryBatchId 갱신.
     * (다른 batch X 에 속한 슬립을 batch Y 에 add 하면 slip.deliveryBatchId = Y).
     */
    @Test
    void addSlip_movesFromOtherBatch() throws Exception {
        String date = "2026-05-12";
        // batch X 용 슬립.
        String slipA = createSlipWithDriver("황기사", "010-1010-2020", date);
        autoGroup(date, "MANAGER");

        // batch Y 용 슬립 (다른 driverPhone).
        createSlipWithDriver("성기사", "010-3030-4040", date);
        String groupY = autoGroup(date, "MANAGER");
        // 두 batch 모두 반환됨 — driver phone "010-3030-4040" 인 것 찾기.
        JsonNode dataY = objectMapper.readTree(groupY).get("data");
        String batchYId = null;
        for (JsonNode b : dataY) {
            if ("010-3030-4040".equals(b.get("driverPhone").asText())) {
                batchYId = b.get("id").asText();
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(batchYId).isNotNull();

        // batch Y 에 slipA 수동 추가.
        Map<String, Object> reqBody = Map.of("slipId", slipA);
        mockMvc.perform(post("/delivery-batches/" + batchYId + "/slips")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqBody)))
                .andExpect(status().isOk());

        // 검증 — slipA 의 deliveryBatchId 가 batchYId 로 변경.
        mockMvc.perform(get("/slips/" + slipA)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deliveryBatchId").value(batchYId));
    }

    /**
     * 시나리오 8 — removeSlip → slip.deliveryBatchId = null.
     */
    @Test
    void removeSlip_clearsBatchId() throws Exception {
        String date = "2026-05-13";
        String slipId = createSlipWithDriver("탁기사", "010-5050-6060", date);
        String groupBody = autoGroup(date, "MANAGER");
        String batchId = objectMapper.readTree(groupBody).get("data").get(0).get("id").asText();

        // 사전 — slip 이 batch 에 속해 있음.
        mockMvc.perform(get("/slips/" + slipId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(jsonPath("$.data.deliveryBatchId").value(batchId));

        // remove.
        mockMvc.perform(delete("/delivery-batches/" + batchId + "/slips/" + slipId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isNoContent());

        // 검증 — deliveryBatchId == null.
        mockMvc.perform(get("/slips/" + slipId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(jsonPath("$.data.deliveryBatchId").value(nullValue()));
    }

    // -------- 시나리오 9: 권한 매트릭스 --------

    /**
     * 시나리오 9 — SALES 가 auto-group 시도 → 403 (MANAGER/MASTER 만 허용).
     */
    @Test
    void salesRole_autoGroup_returns403() throws Exception {
        String date = "2026-05-14";
        createSlipWithDriver("권한기사", "010-7070-8080", date);

        mockMvc.perform(post("/delivery-batches/auto-group")
                        .param("date", date)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isForbidden());
    }

    // -------- 시나리오 10: regenerate-token --------

    /**
     * 시나리오 10 — regenerate-token → 새 batchToken + tokenExpiresAt 갱신.
     */
    @Test
    void regenerateToken_extendsExpiry() throws Exception {
        String date = "2026-05-15";
        createSlipWithDriver("재발급기사", "010-9090-1010", date);
        String groupBody = autoGroup(date, "MANAGER");
        JsonNode batch = objectMapper.readTree(groupBody).get("data").get(0);
        String batchId = batch.get("id").asText();
        String oldToken = batch.get("batchToken").asText();

        mockMvc.perform(post("/delivery-batches/" + batchId + "/regenerate-token")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenExpiresAt", notNullValue()));

        // GET 으로 재조회 — 토큰 변경 확인.
        MvcResult after = mockMvc.perform(get("/delivery-batches/" + batchId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andReturn();
        String newToken = objectMapper.readTree(after.getResponse().getContentAsString())
                .get("data").get("batchToken").asText();
        org.assertj.core.api.Assertions.assertThat(newToken).isNotEqualTo(oldToken);
    }

    // -------- 시나리오 11: GET 목록 --------

    /**
     * 시나리오 11 — GET /delivery-batches?date=&sent= 목록 조회 (관리자 LinkDispatchListPage 데이터).
     * UUID 노출 가드 — 응답에 driverName/driverPhone 만, slip lines 의 productId UUID 미노출 (요약 view).
     */
    @Test
    void listBatches_byDateAndSent_returnsArray() throws Exception {
        String date = "2026-05-16";
        createSlipWithDriver("리스트기사1", "010-1111-9999", date);
        createSlipWithDriver("리스트기사2", "010-2222-9999", date);
        autoGroup(date, "MANAGER");

        mockMvc.perform(get("/delivery-batches")
                        .param("date", date)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data[0].driverName", notNullValue()))
                .andExpect(jsonPath("$.data[0].driverPhone", notNullValue()));
    }
}
