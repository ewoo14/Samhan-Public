package com.samhanair.logis.user.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.samhanair.logis.user.repository.EmployeeSignatureAuditRepository;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** AdminUserController 서명 PATCH/DELETE IT - C1a (저장/해시/50KB/재등록/409). */
@SpringBootTest(classes = UserServiceApplication.class,
        properties = "samhan.security.department.enabled=true")
@AutoConfigureMockMvc
class AdminUserSignatureControllerIT extends AbstractPostgresIT {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String DEPARTMENT_HEADER = "X-User-Department";

    @Autowired private MockMvc mockMvc;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSignatureAuditRepository auditRepository;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Department department;

    @BeforeEach
    void setUp() {
        lenient().when(dynamicPermissionClient.check(any(UUID.class), anyString(), any(PermissionAction.class)))
                .thenReturn(true);
        department = departmentRepository.findByCode("SIG_CTRL_IT")
                .orElseGet(() -> departmentRepository.save(
                        Department.create("SIG_CTRL_IT", "서명컨트롤IT", 953)));
    }

    private UUID newEmployee() {
        Employee e = Employee.create(UUID.randomUUID(), "sigit-" + shortId(), "서명사원", "사원",
                Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
        return employeeRepository.saveAndFlush(e).getId();
    }

    private static byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
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

    private String uploadBody(byte[] png, String hash, SignatureChannel channel) {
        return """
                {"signaturePngBase64":"%s","signatureHash":"%s","channel":"%s"}
                """.formatted(b64(png), hash, channel.name());
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withMaster(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.header(USER_ID_HEADER, UUID.randomUUID().toString())
                .header(ROLE_HEADER, "MASTER")
                .header(DEPARTMENT_HEADER, HrAuthorizationHelper.EXECUTIVE_OFFICE_NAME);
    }

    @Test
    void PATCH_업로드는_서명을_저장하고_200에_registered_true를_반환한다() throws Exception {
        UUID id = newEmployee();
        byte[] png = pngBytes();

        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(png, sha256Hex(png), SignatureChannel.UPLOAD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.signatureChannel").value("UPLOAD"))
                .andExpect(jsonPath("$.data.signedAt").isNotEmpty());

        Employee saved = employeeRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getSignatureHash())
                .isEqualTo(sha256Hex(png));
        org.assertj.core.api.Assertions.assertThat(
                auditRepository.findAllByEmployeeIdOrderByCreatedAtDesc(id)).hasSize(1);
    }

    @Test
    void PATCH_해시_불일치는_400() throws Exception {
        UUID id = newEmployee();
        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(pngBytes(), "f".repeat(64), SignatureChannel.UPLOAD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void PATCH_비_PNG_magic_byte는_422() throws Exception {
        UUID id = newEmployee();
        byte[] notPng = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(notPng, sha256Hex(notPng), SignatureChannel.UPLOAD)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void PATCH_50KB_초과는_422() throws Exception {
        UUID id = newEmployee();
        byte[] big = new byte[EmployeeSignatureServiceMaxRef.MAX + 1];
        big[0] = (byte) 0x89; big[1] = 0x50; big[2] = 0x4E; big[3] = 0x47;
        big[4] = 0x0D; big[5] = 0x0A; big[6] = 0x1A; big[7] = 0x0A;
        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(big, sha256Hex(big), SignatureChannel.UPLOAD)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void PATCH_재등록은_기존_서명을_교체한다() throws Exception {
        UUID id = newEmployee();
        byte[] first = pngBytes();
        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(first, sha256Hex(first), SignatureChannel.UPLOAD)))
                .andExpect(status().isOk());
        byte[] second = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x11};
        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(second, sha256Hex(second), SignatureChannel.MOBILE_CANVAS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signatureChannel").value("MOBILE_CANVAS"));

        Employee saved = employeeRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getSignatureHash())
                .isEqualTo(sha256Hex(second));
    }

    @Test
    void DELETE_등록된_서명은_204로_무효화된다() throws Exception {
        UUID id = newEmployee();
        byte[] png = pngBytes();
        mockMvc.perform(withMaster(patch("/api/v1/admin/users/{id}/signature", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadBody(png, sha256Hex(png), SignatureChannel.UPLOAD)))
                .andExpect(status().isOk());

        mockMvc.perform(withMaster(delete("/api/v1/admin/users/{id}/signature", id))
                        .param("reason", "오등록 정정"))
                .andExpect(status().isNoContent());

        Employee saved = employeeRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getSignedAt()).isNull();
    }

    @Test
    void DELETE_미등록_서명_무효화는_409() throws Exception {
        UUID id = newEmployee();
        mockMvc.perform(withMaster(delete("/api/v1/admin/users/{id}/signature", id))
                        .param("reason", "사유"))
                .andExpect(status().isConflict());
    }

    /** PNG_MAX_BYTES 참조 (서비스 상수 직접 인용). */
    static final class EmployeeSignatureServiceMaxRef {
        static final int MAX =
                com.samhanair.logis.user.service.EmployeeSignatureService.PNG_MAX_BYTES;
    }
}
