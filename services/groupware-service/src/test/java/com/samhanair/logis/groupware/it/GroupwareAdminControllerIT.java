package com.samhanair.logis.groupware.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.groupware.GroupwareServiceApplication;
import com.samhanair.logis.groupware.client.UserClient;
import com.samhanair.logis.groupware.dto.ApprovalDecisionRequest;
import com.samhanair.logis.groupware.dto.ApprovalLineCreateRequest;
import com.samhanair.logis.groupware.dto.MessageSendRequest;
import com.samhanair.logis.groupware.dto.ScheduleRequest;
import com.samhanair.logis.groupware.repository.ApprovalLineRepository;
import com.samhanair.logis.groupware.repository.MessageRepository;
import com.samhanair.logis.groupware.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.List;
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
 * Admin endpoint 권한 / 흐름 시나리오 (6 case).
 *
 * <ol>
 *   <li>결재선 생성 (POST /admin/groupware/approvals) → 201</li>
 *   <li>결재 승인 (PUT /admin/groupware/approvals/{id}/approve) → 200</li>
 *   <li>결재 반려 (PUT /admin/groupware/approvals/{id}/reject) → 200</li>
 *   <li>메신저 발송 (POST /admin/groupware/messages) → 201</li>
 *   <li>일정 등록 (POST /admin/groupware/schedules) → 201</li>
 *   <li>일정 조회 (GET /admin/groupware/schedules?ownerId&from&to) → 200</li>
 * </ol>
 *
 * <p>UserClient = {@code @MockBean} 격리 (memory feedback_it_mockbean_external_clients).
 */
@SpringBootTest(classes = GroupwareServiceApplication.class)
@AutoConfigureMockMvc
class GroupwareAdminControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApprovalLineRepository approvalLineRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;

    @MockBean
    private UserClient userClient;

    @BeforeEach
    void cleanup() {
        lenient().when(userClient.exists(any())).thenReturn(true);
        approvalLineRepository.deleteAll();
        messageRepository.deleteAll();
        scheduleRepository.deleteAll();
    }

    @Test
    void create_approval_returns_201() throws Exception {
        ApprovalLineCreateRequest req = new ApprovalLineCreateRequest(
                UUID.randomUUID(), "휴가 신청", "연차 1일",
                List.of(UUID.randomUUID(), UUID.randomUUID()));
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/groupware/approvals")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("PENDING"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.steps.length()").value(2));
    }

    @Test
    void approve_first_step_returns_200_in_progress() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        ApprovalLineCreateRequest createReq = new ApprovalLineCreateRequest(
                requester, "결재 진행 case", null, List.of(approver1, approver2));
        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/admin/groupware/approvals")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn();
        String approvalId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("approvalId").asText();

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest(approver1, null);
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/groupware/approvals/" + approvalId + "/approve")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void reject_first_step_returns_200_rejected() throws Exception {
        UUID requester = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        ApprovalLineCreateRequest createReq = new ApprovalLineCreateRequest(
                requester, "반려 case", null, List.of(approver1));
        MvcResult created = mockMvc.perform(MockMvcRequestBuilders.post("/admin/groupware/approvals")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andReturn();
        String approvalId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("approvalId").asText();

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest(approver1, "사유 부족");
        mockMvc.perform(MockMvcRequestBuilders.put("/admin/groupware/approvals/" + approvalId + "/reject")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void send_message_returns_201() throws Exception {
        MessageSendRequest req = new MessageSendRequest(UUID.randomUUID(), UUID.randomUUID(), "안녕하세요");
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/groupware/messages")
                        .header("X-User-Id", "user-sales")
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("UNREAD"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.body").value("안녕하세요"));
    }

    @Test
    void create_schedule_returns_201() throws Exception {
        ScheduleRequest req = new ScheduleRequest(
                UUID.randomUUID(), "주간 회의", "주간 정기",
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                null,
                List.of(UUID.randomUUID()));
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/groupware/schedules")
                        .header("X-User-Id", "user-sales")
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void find_schedules_in_range_returns_200() throws Exception {
        UUID owner = UUID.randomUUID();
        ScheduleRequest req = new ScheduleRequest(
                owner, "조회 fixture", null,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(2),
                null, null);
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/groupware/schedules")
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        String from = LocalDateTime.now().toString();
        String to = LocalDateTime.now().plusDays(2).toString();
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/groupware/schedules")
                        .param("ownerId", owner.toString())
                        .param("from", from)
                        .param("to", to)
                        .header("X-User-Id", "user-mgr")
                        .header("X-User-Role", "MANAGER"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].title").value("조회 fixture"));
    }
}
