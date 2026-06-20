package com.samhanair.logis.user.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.EmployeeSignatureAudit;
import com.samhanair.logis.user.domain.SignatureChannel;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureAuditRepository;
import com.samhanair.logis.user.web.dto.EmployeeSignatureDto;
import com.samhanair.logis.user.web.dto.EmployeeSignatureResponse;
import com.samhanair.logis.user.web.dto.EmployeeSignatureUploadRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사원 서명(인감) 등록/무효화/배치 조회 워크플로우 - C1a.
 *
 * <p>검증(slip SlipSignatureService 미러 + magic-byte 추가):
 * <ol>
 *   <li>PNG base64 디코드 (실패 400)</li>
 *   <li>PNG magic-byte(8바이트 시그니처) 검증 - 비-PNG 422</li>
 *   <li>크기 50KB 이하 가드 - 초과 422</li>
 *   <li>서버 SHA-256 재계산 -> 클라 hash 불일치 400</li>
 * </ol>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeSignatureService {

    /** PNG 크기 가드 - 50KB (slip PNG_MAX_BYTES 미러). */
    public static final int PNG_MAX_BYTES = 50 * 1024;

    /** PNG 파일 시그니처 8바이트 (magic-byte). */
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final EmployeeRepository employeeRepository;
    private final EmployeeSignatureAuditRepository auditRepository;

    /**
     * 서명 등록(업로드/모바일) - 해시 재검증 + magic-byte + 50KB 이하 후 Employee.registerSignature.
     *
     * @param employeeId 대상 사원 UUID
     * @param req 업로드 요청(base64 + 클라 hash + channel)
     * @param actorUserId 처리자 user-id (모바일 공개 경로는 null 가능)
     * @return 등록 결과
     * @throws BusinessException(NOT_FOUND) 사원 미발견
     * @throws BusinessException(INVALID_INPUT) base64 디코드 실패 / hash 불일치
     * @throws BusinessException(UNPROCESSABLE_ENTITY) 비-PNG / 50KB 초과
     */
    public EmployeeSignatureResponse register(UUID employeeId,
                                              EmployeeSignatureUploadRequest req,
                                              String actorUserId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "직원을 찾을 수 없습니다: " + employeeId));

        byte[] png = decodePng(req.signaturePngBase64());
        if (!isPng(png)) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                    "PNG 이미지가 아닙니다 (magic-byte 불일치)");
        }
        if (png.length > PNG_MAX_BYTES) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY,
                    "서명 PNG 가 너무 큽니다 (" + png.length + " bytes, 최대 " + PNG_MAX_BYTES + ")");
        }
        String serverHash = sha256Hex(png);
        if (!serverHash.equalsIgnoreCase(req.signatureHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "서명 무결성 검증 실패 - 클라이언트 hash 가 일치하지 않습니다");
        }

        employee.registerSignature(png, serverHash, req.channel());
        auditRepository.save(EmployeeSignatureAudit.record(
                employee.getId(), serverHash, req.channel(), actorUserId));
        return EmployeeSignatureResponse.from(employee);
    }

    /**
     * 서명 무효화(MASTER) - 직전 hash/channel snapshot 후 Employee.invalidateSignature + audit.
     *
     * @param employeeId 대상 사원 UUID
     * @param reason 무효화 사유 (필수)
     * @param actorUserId 처리자 user-id (필수)
     * @throws BusinessException(NOT_FOUND) 사원 미발견
     * @throws BusinessException(CONFLICT) 미등록 상태 무효화 시도
     */
    public void invalidate(UUID employeeId, String reason, String actorUserId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "무효화 사유(reason)는 필수입니다");
        }
        if (reason.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "무효화 사유는 최대 500자입니다");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "직원을 찾을 수 없습니다: " + employeeId));
        String prevHash = employee.getSignatureHash();
        SignatureChannel prevChannel = employee.getSignatureChannel();

        employee.invalidateSignature(reason);
        auditRepository.save(EmployeeSignatureAudit.invalidate(
                employee.getId(), prevHash, prevChannel, reason, actorUserId));
    }

    /**
     * 내부 서명 배치 조회 - slip 결재란 인감 enrichment. 미등록 사원은 맵에서 생략한다.
     *
     * @param userIds 조회 대상 user UUID 목록
     * @return {@code userId -> EmployeeSignatureDto} (등록 사원만)
     */
    @Transactional(readOnly = true)
    public Map<UUID, EmployeeSignatureDto> resolveSignatures(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> distinct = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EmployeeSignatureDto> result = new LinkedHashMap<>();
        for (Employee e : employeeRepository.findAllByIdIn(distinct)) {
            if (e.getSignedAt() == null || e.getSignaturePng() == null) {
                continue;
            }
            String dataUri = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(e.getSignaturePng());
            String signedAt = e.getSignedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            result.put(e.getId(), new EmployeeSignatureDto(dataUri, signedAt));
        }
        return result;
    }

    private boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] decodePng(String input) {
        if (input == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "signaturePngBase64 가 비어있습니다");
        }
        String base64 = input.contains(",") ? input.substring(input.indexOf(',') + 1) : input;
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PNG base64 디코드 실패");
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 알고리즘 미지원");
        }
    }
}
