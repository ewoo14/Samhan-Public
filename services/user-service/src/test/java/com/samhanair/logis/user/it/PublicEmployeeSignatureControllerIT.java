package com.samhanair.logis.user.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
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
import com.samhanair.logis.user.service.EmployeeSignatureHandoffService;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** 공개 제출 엔드포인트 위협모델 IT — 만료/재사용/위조/hash/크기 (slice C1b · spec §10). */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
class PublicEmployeeSignatureControllerIT extends AbstractPostgresIT {

    // 1x1 투명 PNG (89 504E 470D ... magic-byte 포함 유효 PNG)
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureHandoffTokenRepository tokenRepository;
    @Autowired private EmployeeSignatureHandoffService handoffService;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department dept = departmentRepository.findByCode("C1BPUB_IT")
                .orElseGet(() -> departmentRepository.save(Department.create("C1BPUB_IT", "공개제출 테스트팀", 902)));
        employee = employeeRepository.save(Employee.create(
                UUID.randomUUID(), "pub-" + UUID.randomUUID(), "공개서명대상", "사원",
                Role.SALES, dept, false, LocalDate.of(2026, 1, 1), null, null));
    }

    @Test
    void 정상_제출_200_사원_서명_반영_토큰_소진() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));

        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());

        Employee reloaded = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat((byte[]) ReflectionTestUtils.getField(reloaded, "signaturePng")).isNotNull();
        assertThat((String) ReflectionTestUtils.getField(reloaded, "signatureHash"))
                .isEqualToIgnoringCase(sha256Hex(PNG));
        EmployeeSignatureHandoffToken used = tokenRepository.findByToken(token.getToken()).orElseThrow();
        assertThat(used.isUsed()).isTrue();
    }

    @Test
    void 만료_토큰_제출_거부_410() throws Exception {
        EmployeeSignatureHandoffToken token =
                EmployeeSignatureHandoffToken.issue(employee.getId(), null);
        ReflectionTestUtils.setField(token, "expiresAt", LocalDateTime.now().minusMinutes(1));
        tokenRepository.save(token);

        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isGone());
    }

    @Test
    void 재사용_토큰_제출_거부_409() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isConflict());
    }

    @Test
    void 위조_미발견_토큰_404() throws Exception {
        mockMvc.perform(post("/public/employee-signatures/{token}", "forged-token-deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isNotFound());
    }

    @Test
    void hash_mismatch_400() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, "0".repeat(64))))
                .andExpect(status().isBadRequest());
        assertThat(tokenRepository.findByToken(token.getToken()).orElseThrow().isUsed()).isFalse();
    }

    @Test
    void PNG_50KB_초과_422() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        byte[] big = new byte[50 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, Math.min(PNG.length, 8));
        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(big, sha256Hex(big))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void base64_90000자_초과_요청은_decode_전_400() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));

        mockMvc.perform(post("/public/employee-signatures/{token}", token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "signaturePngBase64", "a".repeat(90001),
                                "signatureHash", "0".repeat(64)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 재발급_무효화된_구토큰_제출_거부_404() throws Exception {
        EmployeeSignatureHandoffToken first = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        first.markDeleted("test");
        tokenRepository.save(first);
        EmployeeSignatureHandoffToken second = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));

        mockMvc.perform(post("/public/employee-signatures/{token}", first.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/public/employee-signatures/{token}", second.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(PNG, sha256Hex(PNG))))
                .andExpect(status().isOk());
    }

    @Test
    void 동일_토큰_동시제출은_한번만_성공하고_나머지는_409() throws Exception {
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employee.getId(), null));
        String pngBase64 = b64(PNG);
        String hash = sha256Hex(PNG);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() ->
                    handoffService.submitPublic(token.getToken(), pngBase64, hash));
            var second = executor.submit(() ->
                    handoffService.submitPublic(token.getToken(), pngBase64, hash));

            int success = 0;
            int conflict = 0;
            for (var future : java.util.List.of(first, second)) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    success++;
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof BusinessException be
                            && be.getErrorCode() == ErrorCode.CONFLICT) {
                        conflict++;
                    } else {
                        throw ex;
                    }
                }
            }

            assertThat(success).isEqualTo(1);
            assertThat(conflict).isEqualTo(1);
            assertThat(tokenRepository.findByToken(token.getToken()).orElseThrow().isUsed()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private String body(byte[] png, String hash) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "signaturePngBase64", Base64.getEncoder().encodeToString(png),
                "signatureHash", hash));
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
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
