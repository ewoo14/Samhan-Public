package com.samhanair.logis.notification.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.notification.NotificationServiceApplication;
import com.samhanair.logis.notification.client.UserClient;
import com.samhanair.logis.notification.domain.NotificationChannel;
import com.samhanair.logis.notification.domain.RecipientType;
import com.samhanair.logis.notification.dto.NotificationSendRequest;
import com.samhanair.logis.notification.repository.NotificationLogRepository;
import com.samhanair.logis.notification.repository.NotificationRequestRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Admin endpoint 권한 / 흐름 시나리오 (5 case).
 *
 * <ol>
 *   <li>발송 (POST /admin/notifications/send) → 201, SENT</li>
 *   <li>이력 조회 (GET /admin/notifications) → 200, 1건</li>
 *   <li>단건 조회 (GET /admin/notifications/{id}) → 200</li>
 *   <li>재시도 — FAILED 상태 fixture seed 후 (POST /admin/notifications/{id}/retry) → 200</li>
 *   <li>미존재 단건 조회 → 404</li>
 * </ol>
 *
 * <p>UserClient = {@code @MockBean} 격리.
 */
@SpringBootTest(classes = NotificationServiceApplication.class)
@AutoConfigureMockMvc
class NotificationAdminControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NotificationRequestRepository requestRepository;
    @Autowired
    private NotificationLogRepository logRepository;

    @MockBean
    private UserClient userClient;

    @BeforeEach
    void cleanup() {
        lenient().when(userClient.exists(any())).thenReturn(true);
        lenient().when(userClient.verifyBulk(anyList())).thenAnswer(inv -> {
            List<UUID> ids = inv.getArgument(0);
            Map<UUID, Boolean> r = new HashMap<>();
            for (UUID id : ids) {
                r.put(id, true);
            }
            return r;
        });
        logRepository.deleteAll();
        requestRepository.deleteAll();
    }

    @Test
    void send_returns_201_sent() throws Exception {
        NotificationSendRequest req = new NotificationSendRequest(
                RecipientType.EXTERNAL_PHONE, null, "010-1111-2222",
                NotificationChannel.SMS, null, null, "테스트 SMS", null);
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/notifications/send")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("SENT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.channel").value("SMS"));
    }

    @Test
    void list_returns_200_with_filter() throws Exception {
        // 1건 발송 후 list 호출
        NotificationSendRequest req = new NotificationSendRequest(
                RecipientType.USER, UUID.randomUUID(), null,
                NotificationChannel.PUSH, null, "list 테스트", "본문", null);
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/notifications/send")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        mockMvc.perform(MockMvcRequestBuilders.get("/admin/notifications")
                        .param("channel", "PUSH")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1));
    }

    @Test
    void find_one_returns_200() throws Exception {
        NotificationSendRequest req = new NotificationSendRequest(
                RecipientType.EXTERNAL_PHONE, null, "010-2222-3333",
                NotificationChannel.SMS, null, null, "단건", null);
        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/admin/notifications/send")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();
        String requestId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("requestId").asText();
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/notifications/" + requestId)
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.requestId").value(requestId));
    }

    @Test
    void retry_after_marking_failed_returns_200() throws Exception {
        // 1) 발송 (성공) — repository 직접 fail 마킹
        NotificationSendRequest req = new NotificationSendRequest(
                RecipientType.EXTERNAL_PHONE, null, "010-3333-4444",
                NotificationChannel.SMS, null, null, "retry case", null);
        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/admin/notifications/send")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();
        String requestId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("requestId").asText();

        // 2) FAILED 상태로 직접 전이 (테스트 fixture)
        var entity = requestRepository.findById(UUID.fromString(requestId)).orElseThrow();
        entity.markFailed(false);
        requestRepository.save(entity);

        // 3) retry 호출 → 200, status=SENT 또는 RETRYING (FCM stub-success 라 SENT 가능)
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/notifications/" + requestId + "/retry")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true));
    }

    @Test
    void find_one_missing_returns_404() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/notifications/" + missing)
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("NOT_FOUND"));
    }
}
