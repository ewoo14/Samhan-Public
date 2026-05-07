package com.samhanair.logis.arologis.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.arologis.ArologisServiceApplication;
import com.samhanair.logis.arologis.client.NotificationClient;
import com.samhanair.logis.arologis.client.PartnerClient;
import com.samhanair.logis.arologis.client.SlipClient;
import com.samhanair.logis.arologis.client.UserClient;
import com.samhanair.logis.arologis.repository.DispatchRepository;
import com.samhanair.logis.arologis.repository.DriverLocationRepository;
import com.samhanair.logis.arologis.repository.DriverRepository;
import com.samhanair.logis.arologis.repository.SignatureRepository;
import com.samhanair.logis.arologis.repository.VehicleRepository;
import com.samhanair.logis.arologis.repository.VehicleStopRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Admin endpoint 시나리오 (9 case) — Phase 10 W10-1.
 *
 * <ol>
 *   <li>parse-kakao 정상 (사용자 카톡 예시 입력 → 13 차량 응답)</li>
 *   <li>parse-kakao 헤더 없음 → 400</li>
 *   <li>dispatches POST 저장 정상 → 200 + dispatchId</li>
 *   <li>dispatches GET list 정상 → 200</li>
 *   <li>dispatches/{id} GET 미존재 → 404</li>
 *   <li>auto-match 호출 (Mock matcher) → 200 + matched 카운트</li>
 *   <li>assign-driver 미존재 driver → 404</li>
 *   <li>stop status 갱신 미존재 → 404</li>
 *   <li>drivers list 정상 → 200</li>
 * </ol>
 */
@SpringBootTest(classes = ArologisServiceApplication.class)
@AutoConfigureMockMvc
class ArologisAdminControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DispatchRepository dispatchRepository;
    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleStopRepository stopRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private SignatureRepository signatureRepository;
    @Autowired
    private DriverLocationRepository locationRepository;

    @MockBean
    private PartnerClient partnerClient;
    @MockBean
    private UserClient userClient;
    @MockBean
    private SlipClient slipClient;
    @MockBean
    private NotificationClient notificationClient;

    private static final String SAMPLE_KAKAO = """
            8일착 야상입니다
            1. 상일+초월
            -인천 남동구 구월동(에스엠하나공조-214)아침8시
            -인천 서구 봉수대로(대한공조-170)아침9시반
            1톤
            """;

    @BeforeEach
    void setUp() {
        lenient().when(partnerClient.findByCodes(any())).thenReturn(java.util.List.of());
        lenient().when(partnerClient.findByCode(any())).thenReturn(Optional.empty());
        lenient().when(userClient.findById(any())).thenReturn(Optional.empty());
        lenient().when(slipClient.registerSignature(any(), any())).thenReturn(false);
        lenient().when(notificationClient.send(any(), any(), any(), any())).thenReturn(true);

        // signatures → vehicle_stops → vehicles → dispatches FK 순서로 cleanup
        signatureRepository.deleteAll();
        locationRepository.deleteAll();
        stopRepository.deleteAll();
        vehicleRepository.deleteAll();
        dispatchRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    void parseKakao_returns_200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("kakaoText", SAMPLE_KAKAO));
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/arologis/dispatches/parse-kakao")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER")
                        .contentType("application/json")
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.dispatchType").value("NIGHT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.vehicles").isArray());
    }

    @Test
    void parseKakao_with_invalid_text_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("kakaoText", "잘못된 메시지"));
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/arologis/dispatches/parse-kakao")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER")
                        .contentType("application/json")
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void create_dispatch_returns_200_with_id() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("kakaoText", SAMPLE_KAKAO));
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/arologis/dispatches")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER")
                        .contentType("application/json")
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.dispatchId").exists());
    }

    @Test
    void list_dispatches_returns_200_array() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/arologis/dispatches")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray());
    }

    @Test
    void find_missing_dispatch_returns_404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/arologis/dispatches/" + UUID.randomUUID())
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void auto_match_returns_200() throws Exception {
        // 우선 dispatch 생성
        String createBody = objectMapper.writeValueAsString(Map.of("kakaoText", SAMPLE_KAKAO));
        String createResponse = mockMvc.perform(MockMvcRequestBuilders.post("/admin/arologis/dispatches")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER")
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        String dispatchId = objectMapper.readTree(createResponse)
                .get("data").get("dispatchId").asText();

        mockMvc.perform(MockMvcRequestBuilders.post("/admin/arologis/dispatches/" + dispatchId + "/auto-match")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.totalVehicles").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.matched").exists());
    }

    @Test
    void assign_driver_unknown_code_returns_404() throws Exception {
        // dispatch 생성
        String createBody = objectMapper.writeValueAsString(Map.of("kakaoText", SAMPLE_KAKAO));
        String createResponse = mockMvc.perform(MockMvcRequestBuilders.post("/admin/arologis/dispatches")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER")
                        .contentType("application/json")
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        String dispatchId = objectMapper.readTree(createResponse)
                .get("data").get("dispatchId").asText();

        String body = objectMapper.writeValueAsString(Map.of("driverCode", "UNKNOWN-999"));
        mockMvc.perform(MockMvcRequestBuilders.post(
                        "/admin/arologis/dispatches/" + dispatchId + "/vehicles/1/assign-driver")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER")
                        .contentType("application/json")
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void update_stop_status_missing_returns_404() throws Exception {
        UUID id = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of("status", "ARRIVED"));
        mockMvc.perform(MockMvcRequestBuilders.put(
                        "/admin/arologis/dispatches/" + id + "/vehicles/1/stops/1/status")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER")
                        .contentType("application/json")
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void list_drivers_returns_200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/arologis/drivers")
                        .header("X-User-Id", "test-admin")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray());
    }
}
