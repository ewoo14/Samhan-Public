package com.samhanair.logis.user.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.HrAuthorizationHelper;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.client.AuthClient;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.SignatureChannel;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import com.samhanair.logis.user.service.EmployeeSignatureHandoffService;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 핸드오프 토큰 발급/상태 admin 엔드포인트 IT (slice C1b). */
@SpringBootTest(classes = UserServiceApplication.class,
        properties = "samhan.security.department.enabled=true")
@AutoConfigureMockMvc
class HandoffTokenAdminControllerIT extends AbstractPostgresIT {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String DEPARTMENT_HEADER = "X-User-Department";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureHandoffTokenRepository tokenRepository;
    @Autowired private EmployeeSignatureHandoffService handoffService;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Employee employee;
    private String callerId;

    @BeforeEach
    void setUp() {
        lenient().when(dynamicPermissionClient.check(any(UUID.class), anyString(), any(PermissionAction.class)))
                .thenReturn(true);
        Department dept = departmentRepository.findByCode("C1B_ADMIN_IT")
                .orElseGet(() -> departmentRepository.save(Department.create("C1B_ADMIN_IT", "핸드오프관리IT", 904)));
        callerId = UUID.randomUUID().toString();
        employee = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "handoff-" + UUID.randomUUID(), "핸드오프대상", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null));
    }

    @Test
    void 토큰_발급_200_token64자_qrUrl_expiresAt() throws Exception {
        mockMvc.perform(withMaster(post("/api/v1/admin/users/{id}/signature/handoff-token", employee.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.token").value(org.hamcrest.Matchers.matchesPattern("[A-Za-z0-9_-]{64}")))
                .andExpect(jsonPath("$.data.qrUrl").value(org.hamcrest.Matchers.containsString(
                        "/api/public/employee-signatures/")))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    void 미존재_사원_토큰_발급_404() throws Exception {
        mockMvc.perform(withMaster(post("/api/v1/admin/users/{id}/signature/handoff-token", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void 재발급시_직전_미사용토큰_무효화() throws Exception {
        String first = postTokenAndExtract();
        String second = postTokenAndExtract();

        mockMvc.perform(withMaster(get("/api/v1/admin/users/{id}/signature/handoff/{token}/status",
                        employee.getId(), first)))
                .andExpect(status().isNotFound());
        mockMvc.perform(withMaster(get("/api/v1/admin/users/{id}/signature/handoff/{token}/status",
                        employee.getId(), second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(false))
                .andExpect(jsonPath("$.data.expired").value(false));
    }

    @Test
    void 상태조회_미발견_토큰_404() throws Exception {
        mockMvc.perform(withMaster(get("/api/v1/admin/users/{id}/signature/handoff/{token}/status",
                        employee.getId(), "nonexistent-token-xyz")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 다른_사원_ID로_유효토큰_조회는_404() throws Exception {
        String token = postTokenAndExtract();
        Employee other = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "handoff-other-" + UUID.randomUUID(), "다른핸드오프대상", "사원",
                Role.SALES, employee.getDepartment(), false, LocalDate.of(2026, 1, 1), null, null));

        mockMvc.perform(withMaster(get("/api/v1/admin/users/{id}/signature/handoff/{token}/status",
                        other.getId(), token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자_직접업로드_후_기존_미사용_핸드오프_토큰_제출은_404이고_서명은_유지된다() throws Exception {
        String token = postTokenAndExtract();
        byte[] adminPng = PNG;
        String adminHash = sha256Hex(adminPng);

        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", employee.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(adminPng, adminHash, SignatureChannel.UPLOAD)))
                .andExpect(status().isOk());

        byte[] mobilePng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x55
        };
        mockMvc.perform(post("/public/employee-signatures/{token}", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicBody(mobilePng, sha256Hex(mobilePng))))
                .andExpect(status().isNotFound());

        Employee saved = employeeRepository.findById(employee.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getSignatureHash()).isEqualTo(adminHash);
    }

    @Test
    void 동시_발급도_최종_미사용토큰은_1개만_남는다() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> handoffService.issueToken(employee.getId(), callerId).token());
            var second = executor.submit(() -> handoffService.issueToken(employee.getId(), callerId).token());

            List<String> issued = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            org.assertj.core.api.Assertions.assertThat(issued).doesNotHaveDuplicates();
            org.assertj.core.api.Assertions.assertThat(
                            tokenRepository.findAllByEmployeeIdAndUsedAtIsNull(employee.getId()))
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String postTokenAndExtract() throws Exception {
        var result = mockMvc.perform(
                        withMaster(post("/api/v1/admin/users/{id}/signature/handoff-token", employee.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.token");
    }

    private String uploadBody(byte[] png, String hash, SignatureChannel channel) {
        return """
                {"signaturePngBase64":"%s","signatureHash":"%s","channel":"%s"}
                """.formatted(Base64.getEncoder().encodeToString(png), hash, channel.name());
    }

    private String publicBody(byte[] png, String hash) {
        return """
                {"signaturePngBase64":"%s","signatureHash":"%s"}
                """.formatted(Base64.getEncoder().encodeToString(png), hash);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withMaster(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.header(USER_ID_HEADER, callerId)
                .header(ROLE_HEADER, "MASTER")
                .header(DEPARTMENT_HEADER, HrAuthorizationHelper.EXECUTIVE_OFFICE_NAME);
    }
}
