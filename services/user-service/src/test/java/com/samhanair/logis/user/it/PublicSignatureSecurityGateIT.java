package com.samhanair.logis.user.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.client.AuthClient;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
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

/** 공개 제출 인증우회 표면 회귀 가드 — permitAll 경로 격리 + 위조 identity 헤더 무시 (slice C1b). */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
class PublicSignatureSecurityGateIT extends AbstractPostgresIT {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureHandoffTokenRepository tokenRepository;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.findByCode("C1BSEC_IT")
                .orElseGet(() -> departmentRepository.save(Department.create("C1BSEC_IT", "보안게이트 테스트팀", 903)));
        employee = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "sec-" + UUID.randomUUID(), "보안게이트대상", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null));
    }

    @Test
    void 공개경로는_X_User_헤더_없이도_권한게이트_미적용_200() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
    }

    @Test
    void 위조_identity_헤더_주입해도_공개경로_동작_무변동_200() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
    }

    @Test
    void 위조_identity_헤더로도_미발견_토큰_게이트를_우회할_수_없다_404() throws Exception {
        mockMvc.perform(post("/public/employee-signatures/{token}", "missing-token-with-forged-headers")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isNotFound());
    }

    private String body(byte[] png, String hash) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "signaturePngBase64", Base64.getEncoder().encodeToString(png),
                "signatureHash", hash));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
