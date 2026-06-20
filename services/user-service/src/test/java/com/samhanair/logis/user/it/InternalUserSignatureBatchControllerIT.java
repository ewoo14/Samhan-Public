package com.samhanair.logis.user.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.user.UserServiceApplication;
import com.samhanair.logis.user.client.AuthClient;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.SignatureChannel;
import com.samhanair.logis.user.repository.DepartmentRepository;
import com.samhanair.logis.user.repository.EmployeeRepository;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** POST /internal/users/signatures 배치 IT - C1a (배치/미등록생략/join-key/토큰). */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
class InternalUserSignatureBatchControllerIT extends AbstractPostgresIT {

    private static final String TOKEN = "test-internal-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private EmployeeRepository employeeRepository;

    @MockBean private DynamicPermissionClient dynamicPermissionClient;
    @MockBean private AuthClient authClient;

    private Department department;

    @BeforeEach
    void setUp() {
        department = departmentRepository.findByCode("SIG_BATCH_IT")
                .orElseGet(() -> departmentRepository.save(
                        Department.create("SIG_BATCH_IT", "서명배치IT", 954)));
    }

    private UUID signedEmployee() throws Exception {
        Employee e = Employee.create(UUID.randomUUID(), "sigb-" + shortId(), "서명사원", "사원",
                Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        e.registerSignature(png, sha256Hex(png), SignatureChannel.UPLOAD);
        return employeeRepository.saveAndFlush(e).getId();
    }

    private UUID unsignedEmployee() {
        Employee e = Employee.create(UUID.randomUUID(), "sigu-" + shortId(), "미등록사원", "사원",
                Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
        return employeeRepository.saveAndFlush(e).getId();
    }

    private static String sha256Hex(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void 배치조회는_등록사원만_맵에_담고_미등록은_생략한다() throws Exception {
        UUID signed = signedEmployee();
        UUID unsigned = unsignedEmployee();
        UUID missing = UUID.randomUUID();

        mockMvc.perform(post("/internal/users/signatures")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s","%s","%s"]}
                                """.formatted(signed, unsigned, missing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['%s'].signaturePngBase64".formatted(signed))
                        .value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.data['%s'].signedAt".formatted(signed)).isNotEmpty())
                .andExpect(jsonPath("$.data['%s']".formatted(unsigned)).doesNotExist())
                .andExpect(jsonPath("$.data['%s']".formatted(missing)).doesNotExist());
    }

    @Test
    void join_key_회귀_slip_userId로_조회시_해당_사원_서명을_반환한다() throws Exception {
        // slip 의 createdBy/dispatcherUserId/inspectorUserId = Employee.id (P4 join key).
        UUID slipUserId = signedEmployee();

        mockMvc.perform(post("/internal/users/signatures")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s"]}
                                """.formatted(slipUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['%s'].signaturePngBase64".formatted(slipUserId)).isNotEmpty());
    }

    @Test
    void 빈_userIds는_빈_맵을_반환한다() throws Exception {
        mockMvc.perform(post("/internal/users/signatures")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void null_userIds는_400() throws Exception {
        mockMvc.perform(post("/internal/users/signatures")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userIds_50개초과는_400() throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append('"').append(UUID.randomUUID()).append('"');
        }

        mockMvc.perform(post("/internal/users/signatures")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":[%s]}
                                """.formatted(ids)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void X_Internal_Token_누락은_403() throws Exception {
        mockMvc.perform(post("/internal/users/signatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void X_Internal_Token_불일치는_401() throws Exception {
        mockMvc.perform(post("/internal/users/signatures")
                        .header("X-Internal-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userIds":["%s"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }
}
