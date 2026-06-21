package com.samhanair.logis.user.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.user.domain.Employee;
import com.samhanair.logis.user.domain.EmployeeSignatureHandoffToken;
import com.samhanair.logis.user.domain.SignatureChannel;
import com.samhanair.logis.user.repository.EmployeeRepository;
import com.samhanair.logis.user.repository.EmployeeSignatureHandoffTokenRepository;
import com.samhanair.logis.user.web.dto.EmployeeSignatureUploadRequest;
import com.samhanair.logis.user.web.dto.HandoffStatusResponse;
import com.samhanair.logis.user.web.dto.HandoffTokenResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사원 서명 모바일 핸드오프 토큰 서비스 (slice C1b · spec §5.2).
 *
 * <p>발급: 사원 존재 검증 → 동일 사원 미사용 토큰 soft-delete 무효화 → 신규 토큰 발급.
 * 상태: 토큰 단건 lookup → {used, expired} 폴링 응답.
 *
 * <p>qrUrl base 는 {@code app.signature.public-base-url} — 모바일 공개 웹앱 origin
 * (C2 DevOps 확정). 웹앱 페이지 {@code /s/{token}} 경로 결합 (API 제출은 웹앱 same-origin).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EmployeeSignatureHandoffService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSignatureHandoffTokenRepository tokenRepository;
    private final EmployeeSignatureService signatureService;
    private final EntityManager entityManager;

    @Value("${app.signature.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    /**
     * 토큰 발급 — 동일 사원 미사용 토큰 무효화 후 신규 1개 발급.
     *
     * @param employeeId 서명 대상 사원
     * @param actorUserId 발급 관리자 user-id (감사용, nullable)
     * @return 발급 응답 (token / qrUrl / expiresAt ISO)
     * @throws BusinessException(NOT_FOUND) 사원 미존재
     */
    public HandoffTokenResponse issueToken(UUID employeeId, String actorUserId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "직원을 찾을 수 없습니다: " + employeeId));
        entityManager.lock(employee, LockModeType.PESSIMISTIC_WRITE);
        String deletedBy = actorUserId == null || actorUserId.isBlank() ? "system" : actorUserId;
        // 동일 사원 미사용 토큰 무효화 (soft-delete — @SQLRestriction 으로 이후 조회 제외)
        tokenRepository.findAllByEmployeeIdAndUsedAtIsNull(employeeId)
                .forEach(token -> token.markDeleted(deletedBy));
        EmployeeSignatureHandoffToken token = tokenRepository.save(
                EmployeeSignatureHandoffToken.issue(employeeId, actorUserId));
        // 모바일 공개 웹앱 페이지 origin + /s/{token} (사원이 폰으로 여는 SignaturePad 페이지).
        // API 제출(/api/public/employee-signatures/{token})은 웹앱이 same-origin 으로 POST.
        String qrUrl = normalizedPublicBaseUrl() + "/s/" + token.getToken();
        return new HandoffTokenResponse(
                token.getToken(),
                qrUrl,
                token.getExpiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    /**
     * 동일 사원의 열린 미사용 토큰 전체 무효화.
     *
     * <p>관리자가 서명을 직접 업로드하거나 무효화한 뒤 기존 모바일 핸드오프 링크가
     * TTL 내에 뒤늦게 제출되어 현재 서명을 덮어쓰지 못하게 soft-delete 한다.
     *
     * @param employeeId 서명 대상 사원
     * @param actorUserId 무효화 처리자 user-id (blank 이면 system)
     */
    public void revokeOpenTokens(UUID employeeId, String actorUserId) {
        String deletedBy = actorUserId == null || actorUserId.isBlank() ? "system" : actorUserId;
        tokenRepository.findAllByEmployeeIdAndUsedAtIsNull(employeeId)
                .forEach(token -> token.markDeleted(deletedBy));
    }

    /**
     * 토큰 상태 — desktop 폴링용. 토큰 미발견(또는 무효화) 시 404.
     *
     * @throws BusinessException(NOT_FOUND) 토큰 미발견/무효화
     */
    @Transactional(readOnly = true)
    public HandoffStatusResponse status(UUID employeeId, String token) {
        EmployeeSignatureHandoffToken found = tokenRepository.findByToken(token)
                .filter(t -> t.getEmployeeId().equals(employeeId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "토큰을 찾을 수 없습니다"));
        return new HandoffStatusResponse(found.isUsed(), found.isExpired());
    }

    /**
     * 공개 모바일 서명 제출 — 토큰 게이트 (slice C1b · spec §5.2).
     *
     * <p>처리: 토큰 employeeId projection(없으면 404) → Employee 잠금 →
     * 토큰 조회+잠금 → 만료(410) → 사용됨(409) →
     * C1a {@link EmployeeSignatureService#register} 재사용(PNG magic-byte + ≤50KB + SHA-256 재검증
     * + audit RECORD) → 토큰 markUsed 소진.
     *
     * @throws BusinessException(NOT_FOUND) 토큰 미발견/사원 미발견
     * @throws BusinessException(TOKEN_EXPIRED) 토큰 만료
     * @throws BusinessException(CONFLICT) 이미 사용
     * @throws BusinessException(INVALID_INPUT) hash mismatch / base64 디코드 실패
     * @throws BusinessException(UNPROCESSABLE_ENTITY) PNG 50KB 초과 / 비-PNG
     */
    public void submitPublic(String token, String signaturePngBase64, String signatureHash) {
        UUID employeeId = tokenRepository.findEmployeeIdByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 토큰입니다"));
        employeeRepository.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "직원을 찾을 수 없습니다"));
        EmployeeSignatureHandoffToken handoff = tokenRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 토큰입니다"));
        if (handoff.isExpired()) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "토큰이 만료되었습니다");
        }
        if (handoff.isUsed()) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 사용된 토큰입니다");
        }

        signatureService.register(handoff.getEmployeeId(),
                new EmployeeSignatureUploadRequest(
                        signaturePngBase64, signatureHash, SignatureChannel.MOBILE_CANVAS),
                null);
        handoff.markUsed();
    }

    private String normalizedPublicBaseUrl() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return "http://localhost:8080";
        }
        String trimmed = publicBaseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
