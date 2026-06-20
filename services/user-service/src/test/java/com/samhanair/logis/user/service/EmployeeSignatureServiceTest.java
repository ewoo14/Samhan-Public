package com.samhanair.logis.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.user.domain.Department;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.EmployeeSignatureAudit;
import com.samhanair.logis.user.domain.SignatureChannel;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureAuditRepository;
import com.samhanair.logis.user.web.dto.EmployeeSignatureDto;
import com.samhanair.logis.user.web.dto.EmployeeSignatureResponse;
import com.samhanair.logis.user.web.dto.EmployeeSignatureUploadRequest;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** EmployeeSignatureService 단위 테스트 - C1a (해시/magic-byte/50KB/배치/404/409). */
@ExtendWith(MockitoExtension.class)
class EmployeeSignatureServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeSignatureAuditRepository auditRepository;
    @InjectMocks private EmployeeSignatureService service;

    private Employee employee;
    private UUID empId;

    @BeforeEach
    void setUp() {
        empId = UUID.randomUUID();
        Department department = Department.create("SIG", "서명팀", 951);
        employee = Employee.create(empId, "sig01", "서명자", "사원",
                Role.STAFF, department, false, LocalDate.of(2026, 1, 1), null, null);
    }

    /** 최소 유효 PNG = 8바이트 PNG 시그니처. */
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

    @Test
    void register_정상_업로드는_서명을_저장하고_RECORD_audit를_적재한다() throws Exception {
        byte[] png = pngBytes();
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                b64(png), sha256Hex(png), SignatureChannel.UPLOAD);

        EmployeeSignatureResponse res = service.register(empId, req, "actor-1");

        assertThat(res.registered()).isTrue();
        assertThat(res.signatureChannel()).isEqualTo("UPLOAD");
        assertThat(res.signedAt()).isNotNull();
        assertThat(employee.getSignatureHash()).isEqualTo(req.signatureHash());
        verify(auditRepository).save(any(EmployeeSignatureAudit.class));
    }

    @Test
    void register_해시_불일치는_400_INVALID_INPUT() {
        byte[] png = pngBytes();
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                b64(png), "f".repeat(64), SignatureChannel.UPLOAD);

        assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void register_PNG_magic_byte_아니면_422() throws Exception {
        byte[] notPng = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                b64(notPng), sha256Hex(notPng), SignatureChannel.UPLOAD);

        assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
    }

    @Test
    void register_50KB_초과는_422() throws Exception {
        byte[] big = new byte[EmployeeSignatureService.PNG_MAX_BYTES + 1];
        big[0] = (byte) 0x89; big[1] = 0x50; big[2] = 0x4E; big[3] = 0x47;
        big[4] = 0x0D; big[5] = 0x0A; big[6] = 0x1A; big[7] = 0x0A;
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));
        EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                b64(big), sha256Hex(big), SignatureChannel.UPLOAD);

        assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
    }

    @Test
    void register_미존재_사원은_404() throws Exception {
        when(employeeRepository.findById(empId)).thenReturn(Optional.empty());
        byte[] png = pngBytes();
        EmployeeSignatureUploadRequest req = new EmployeeSignatureUploadRequest(
                b64(png), sha256Hex(png), SignatureChannel.UPLOAD);

        assertThatThrownBy(() -> service.register(empId, req, "actor-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void invalidate_미등록_사원은_409_CONFLICT() {
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.invalidate(empId, "사유", "master-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void invalidate_등록된_서명을_NULL로_만들고_INVALIDATE_audit를_적재한다() throws Exception {
        byte[] png = pngBytes();
        employee.registerSignature(png, sha256Hex(png), SignatureChannel.UPLOAD);
        when(employeeRepository.findById(empId)).thenReturn(Optional.of(employee));

        service.invalidate(empId, "오등록", "master-1");

        assertThat(employee.getSignedAt()).isNull();
        verify(auditRepository).save(any(EmployeeSignatureAudit.class));
    }

    @Test
    void resolveSignatures_등록된_사원만_맵에_담고_미등록은_생략한다() throws Exception {
        byte[] png = pngBytes();
        employee.registerSignature(png, sha256Hex(png), SignatureChannel.UPLOAD);
        UUID unsignedId = UUID.randomUUID();
        Department d = Department.create("SIG2", "서명팀2", 952);
        Employee unsigned = Employee.create(unsignedId, "sig02", "미등록", "사원",
                Role.STAFF, d, false, LocalDate.of(2026, 1, 1), null, null);
        lenient().when(employeeRepository.findAllByIdIn(any()))
                .thenReturn(List.of(employee, unsigned));

        Map<UUID, EmployeeSignatureDto> result =
                service.resolveSignatures(List.of(empId, unsignedId));

        assertThat(result).containsKey(empId);
        assertThat(result).doesNotContainKey(unsignedId);
        assertThat(result.get(empId).signaturePngBase64()).startsWith("data:image/png;base64,");
    }

    @Test
    void resolveSignatures_빈_입력은_빈_맵() {
        assertThat(service.resolveSignatures(List.of())).isEmpty();
        assertThat(service.resolveSignatures(null)).isEmpty();
    }
}
