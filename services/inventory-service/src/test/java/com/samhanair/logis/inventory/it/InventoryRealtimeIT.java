package com.samhanair.logis.inventory.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.inventory.InventoryServiceApplication;
import com.samhanair.logis.inventory.client.AccountingClient;
import com.samhanair.logis.inventory.client.ProductClient;
import com.samhanair.logis.inventory.client.ProductSummary;
import com.samhanair.logis.inventory.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * PR-H4b — inventory-service shared:realtime-abstraction 통합 IT.
 *
 * <p>시나리오:
 * <ol>
 *   <li>실사 PLANNED → start (IN_PROGRESS) → audit timeline 1행 (status 변경)</li>
 *   <li>실사 PLANNED → cancel (CANCELLED) → 잠금 정책 가드 통과 (PLANNED 는 free)</li>
 *   <li>COMPLETED 단계의 edit-request 생성 → 200 + status PENDING</li>
 *   <li>PLANNED 단계의 edit-request 생성 → 400 (자유 단계는 요청 불필요)</li>
 * </ol>
 */
@SpringBootTest(classes = InventoryServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class InventoryRealtimeIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private AccountingClient accountingClient;

    private UUID hqId;

    @BeforeEach
    void setUp() {
        hqId = warehouseRepository.findByCode("HQ-001")
                .orElseThrow(() -> new IllegalStateException("HQ-001 시드 누락"))
                .getId();

        Mockito.when(productClient.lookup(Mockito.anyList()))
                .thenAnswer(invocation -> {
                    List<UUID> ids = invocation.getArgument(0);
                    return ids.stream()
                            .map(id -> new ProductSummary(id, "테스트 제품", "TEST-001",
                                    UUID.randomUUID(), new BigDecimal("100000.00"), "ACTIVE"))
                            .toList();
                });
    }

    /** start 시점에 status audit log 1행 + timeline 조회 200. */
    @Test
    void start_recordsAuditLogAndExposesTimeline() throws Exception {
        String auditId = createAudit();
        startAudit(auditId);

        mockMvc.perform(get("/inventory/audits/" + auditId + "/audit-logs")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "INVENTORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fieldName").value("status"))
                .andExpect(jsonPath("$.data[0].oldValue").value("PLANNED"))
                .andExpect(jsonPath("$.data[0].newValue").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].revisionNo").value(1));
    }

    /** PLANNED → cancel — 잠금 정책 free 통과 + audit row 1건. */
    @Test
    void cancelFromPlanned_passesLockPolicyAndRecordsAudit() throws Exception {
        String auditId = createAudit();

        mockMvc.perform(post("/inventory/audits/" + auditId + "/cancel")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "INVENTORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/inventory/audits/" + auditId + "/audit-logs")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "INVENTORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].newValue").value("CANCELLED"));
    }

    /** COMPLETED 단계 edit-request 생성 — status PENDING + 200. */
    @Test
    void createEditRequest_onCompletedAudit_returnsPending() throws Exception {
        String auditId = createAudit();
        startAudit(auditId);
        completeAudit(auditId);

        Map<String, String> body = Map.of(
                "requestType", "EDIT",
                "reason", "차이 분개 정정 필요");

        mockMvc.perform(post("/inventory/audits/" + auditId + "/edit-requests")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Name", "회계담당")
                        .header("X-User-Role", "ACCOUNTANT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestType").value("EDIT"))
                .andExpect(jsonPath("$.data.targetRole").value("MANAGER"));
    }

    /** PLANNED 단계 edit-request → 400 (자유 단계 — 요청 불필요). */
    @Test
    void createEditRequest_onPlannedAudit_returns400() throws Exception {
        String auditId = createAudit();

        Map<String, String> body = Map.of("requestType", "EDIT");

        mockMvc.perform(post("/inventory/audits/" + auditId + "/edit-requests")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "INVENTORY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private String createAudit() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", hqId.toString());
        body.put("auditDate", "2026-12-31");
        MvcResult created = mockMvc.perform(post("/inventory/audits")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "INVENTORY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    private void startAudit(String auditId) throws Exception {
        mockMvc.perform(post("/inventory/audits/" + auditId + "/start")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "INVENTORY"))
                .andExpect(status().isOk());
    }

    private void completeAudit(String auditId) throws Exception {
        mockMvc.perform(post("/inventory/audits/" + auditId + "/complete")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "INVENTORY"))
                .andExpect(status().isOk());
    }
}
