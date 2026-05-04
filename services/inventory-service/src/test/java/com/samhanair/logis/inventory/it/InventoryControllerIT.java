package com.samhanair.logis.inventory.it;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.inventory.InventoryServiceApplication;
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
 * Plan §4 권한 매트릭스 + 입고/출고 핵심 시나리오. ApiGateway 가 X-User-Id / X-User-Role 헤더를
 * 주입하므로 IT 에서도 동일 헤더로 호출. {@link com.samhanair.logis.inventory.config.HeaderAuthenticationFilter}
 * 이 헤더 → SecurityContext 변환.
 *
 * <p>BE endpoint (정확):
 * <ul>
 *   <li>{@code GET    /inventory/warehouses}                       — 인증된 모든 역할, List 반환</li>
 *   <li>{@code POST   /inventory/warehouses}                       — MASTER/MANAGER/DEVELOPER (201)</li>
 *   <li>{@code POST   /inventory/lots/inbound}                     — MASTER/MANAGER/WAREHOUSE/INVENTORY (201)</li>
 *   <li>{@code POST   /inventory/deduct}                           — MASTER/MANAGER/DEVELOPER/SALES/WAREHOUSE/INVENTORY (200)</li>
 * </ul>
 *
 * <p>모든 응답은 ApiResponse 래핑이라 jsonPath 는 {@code $.data.*} 로 접근.
 * 미인증/권한 부족은 모두 403 (HeaderAuthenticationFilter 가 인증 미설정 → ExceptionTranslationFilter default → 403).
 *
 * <p>{@link ProductClient} 는 product-service 호출이라 IT 에서 mock — `requireExists` no-op,
 * `lookup` 은 빈 결과 (테스트에서 lookup 호출 안 함).
 */
@SpringBootTest(classes = InventoryServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class InventoryControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @MockBean
    private ProductClient productClient;

    private UUID hqWarehouseId;

    @BeforeEach
    void setUp() {
        hqWarehouseId = warehouseRepository.findByCode("HQ-001")
                .orElseThrow(() -> new IllegalStateException(
                        "HQ-001 시드 누락 — V2__seed_inventory_warehouses.sql 확인"))
                .getId();

        // ProductClient.requireExists 는 ProductSummary 를 반환 (void 아님) →
        // when().thenReturn() 패턴으로 mock. lookup 도 동일하게 임의 ProductSummary 반환.
        Mockito.lenient().when(productClient.requireExists(Mockito.any()))
                .thenAnswer(inv -> new ProductSummary(
                        inv.getArgument(0), "테스트 제품", "TEST-001",
                        UUID.randomUUID(), new BigDecimal("100000"), "ACTIVE"));
        Mockito.lenient().when(productClient.lookup(Mockito.anyList()))
                .thenAnswer(inv -> {
                    List<UUID> ids = inv.getArgument(0);
                    return ids.stream()
                            .map(id -> new ProductSummary(id, "테스트 제품", "TEST-001",
                                    UUID.randomUUID(), new BigDecimal("100000"), "ACTIVE"))
                            .toList();
                });
    }

    @Test
    void unauthenticated_get_returns403() throws Exception {
        // 헤더 없이 요청 → HeaderAuthenticationFilter 가 인증 미설정 → 403.
        mockMvc.perform(get("/inventory/warehouses"))
                .andExpect(status().isForbidden());
    }

    @Test
    void salesRole_postWarehouse_returns403() throws Exception {
        // SALES 는 창고 등록 불가 (MASTER/MANAGER/DEVELOPER 만).
        Map<String, Object> body = new HashMap<>();
        body.put("code", "SALES-FAIL-001");
        body.put("name", "SALES 가 만들면 안 됨");
        body.put("type", "HEADQUARTERS");
        body.put("displayOrder", 999);

        mockMvc.perform(post("/inventory/warehouses")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerRole_postWarehouse_returns201_thenList_includesIt() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "MGR-NEW-001");
        body.put("name", "매니저가 등록한 신규 창고");
        body.put("type", "VEHICLE");
        body.put("address", "서울시 송파");
        body.put("displayOrder", 500);
        body.put("description", "테스트용 차량창고");

        // ApiResponse<T> 래핑 → jsonPath 는 $.data.*.
        MvcResult result = mockMvc.perform(post("/inventory/warehouses")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.code").value("MGR-NEW-001"))
                .andReturn();

        String createdId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();

        // 조회는 SALES 도 가능. List 반환이라 $.data[?(@.id=='...')] 형식.
        mockMvc.perform(get("/inventory/warehouses")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + createdId + "')].code").exists());
    }

    @Test
    void warehouseRole_inbound_thenDeduct_succeeds() throws Exception {
        UUID productId = UUID.randomUUID();

        // 1) WAREHOUSE 권한으로 입고 — 100개, 단가 100,000. POST /inventory/lots/inbound → 201.
        Map<String, Object> inboundBody = new HashMap<>();
        inboundBody.put("productId", productId.toString());
        inboundBody.put("warehouseId", hqWarehouseId.toString());
        inboundBody.put("quantity", 100);
        inboundBody.put("unitCost", 100000);
        inboundBody.put("lotNo", "FIFO-RECV-001");

        mockMvc.perform(post("/inventory/lots/inbound")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inboundBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()));

        // 2) WAREHOUSE 권한으로 출고 — 30개. FIFO 로 첫 번째 lot 에서 차감. POST /inventory/deduct → 200.
        Map<String, Object> deductBody = new HashMap<>();
        deductBody.put("productId", productId.toString());
        deductBody.put("warehouseId", hqWarehouseId.toString());
        deductBody.put("quantity", 30);
        deductBody.put("note", "FIFO 출고 검증");

        mockMvc.perform(post("/inventory/deduct")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deductBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(notNullValue()));
    }

    @Test
    void deduct_insufficientStock_returns409() throws Exception {
        // 재고 부족 시나리오는 balance 는 존재하되 lot 합계만 모자란 경우 — 먼저 10개 입고로
        // balance 를 생성하고 그 다음 50개 출고 시도 → BusinessException(CONFLICT) → 409.
        // (balance 자체가 없으면 NOT_FOUND 404 반환되므로 CONFLICT 시나리오가 되려면 입고 필요.)
        UUID productId = UUID.randomUUID();

        Map<String, Object> inboundBody = new HashMap<>();
        inboundBody.put("productId", productId.toString());
        inboundBody.put("warehouseId", hqWarehouseId.toString());
        inboundBody.put("quantity", 10);
        inboundBody.put("unitCost", 100000);
        inboundBody.put("lotNo", "INSUFFICIENT-PRE-001");

        mockMvc.perform(post("/inventory/lots/inbound")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inboundBody)))
                .andExpect(status().isCreated());

        Map<String, Object> deductBody = new HashMap<>();
        deductBody.put("productId", productId.toString());
        deductBody.put("warehouseId", hqWarehouseId.toString());
        deductBody.put("quantity", 50);
        deductBody.put("note", "재고 부족 시나리오 (10개만 있는데 50개 요청)");

        mockMvc.perform(post("/inventory/deduct")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "WAREHOUSE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deductBody)))
                .andExpect(status().isConflict());
    }

    @Test
    void salesRole_inbound_returns403() throws Exception {
        // SALES 는 입고 불가 (MASTER/MANAGER/WAREHOUSE/INVENTORY 만).
        UUID productId = UUID.randomUUID();
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId.toString());
        body.put("warehouseId", hqWarehouseId.toString());
        body.put("quantity", 10);
        body.put("unitCost", 100000);
        body.put("lotNo", "SALES-FAIL-001");

        mockMvc.perform(post("/inventory/lots/inbound")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }
}
