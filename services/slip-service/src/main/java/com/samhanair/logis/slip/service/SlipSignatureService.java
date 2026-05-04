package com.samhanair.logis.slip.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.delivery.domain.DeliveryBatch;
import com.samhanair.logis.slip.delivery.repository.DeliveryBatchRepository;
import com.samhanair.logis.slip.delivery.web.dto.PublicSignatureRequest;
import com.samhanair.logis.slip.delivery.web.dto.PublicSignatureResponse;
import com.samhanair.logis.slip.delivery.web.dto.PublicSignatureViewResponse;
import com.samhanair.logis.slip.domain.SignatureChannel;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.domain.SlipSignatureAudit;
import com.samhanair.logis.slip.repository.SlipRepository;
import com.samhanair.logis.slip.repository.SlipSignatureAuditRepository;
import com.samhanair.logis.slip.web.dto.AdminSignatureResponse;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인수자 전자서명 워크플로우 — Slice C (signature-slice-C Plan §1.3 + §2).
 *
 * <p>endpoint 4종 처리:
 * <ul>
 *   <li>{@link #recordSignature} — 공개 모바일 POST {@code /public/batches/.../signature}</li>
 *   <li>{@link #findByShareToken} — 공개 인수자 view GET {@code /public/signatures/{shareToken}}</li>
 *   <li>{@link #getSignature} — 관리자 GET {@code /api/slips/{id}/signature}</li>
 *   <li>{@link #invalidateSignature} — 관리자 DELETE {@code /api/slips/{id}/signature}</li>
 * </ul>
 *
 * <p>핵심 검증 (Plan §5):
 * <ol>
 *   <li>PNG bytes 의 SHA-256 hex 재계산 → 클라이언트 hash mismatch 시 INVALID_INPUT (400)</li>
 *   <li>PNG 크기 ≤ {@value #PNG_MAX_BYTES} bytes 가드 (50KB)</li>
 *   <li>signerName 1~50자 (DTO @Size 가 1차, 도메인이 2차 가드)</li>
 *   <li>토큰/슬립 미발견 — NOT_FOUND (404, 정보 노출 X)</li>
 *   <li>share token 만료 — Controller 에서 410 GONE 매핑 (CONFLICT 던짐)</li>
 * </ol>
 *
 * <p>audit 적재: 도메인 mutation 직후 같은 트랜잭션에서 INSERT — RECORD/INVALIDATE 2종.
 * 공개 endpoint RECORD 시 actorUserId=NULL (인증 없음), 관리자 INVALIDATE 시 X-User-Id 보존.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SlipSignatureService {

    /** PNG 크기 가드 — 50KB (Plan §5). */
    public static final int PNG_MAX_BYTES = 50 * 1024;

    private final SlipRepository slipRepository;
    private final DeliveryBatchRepository batchRepository;
    private final SlipSignatureAuditRepository auditRepository;

    /**
     * 공개 모바일 서명 등록 — Plan §2 의 4 endpoint 중 1번.
     *
     * <p>처리 순서:
     * <ol>
     *   <li>batch token 검증 + 만료 검증 (만료 시 CONFLICT → Controller 410)</li>
     *   <li>해당 batch + slipNo 슬립 lookup (없으면 NOT_FOUND)</li>
     *   <li>PNG base64 디코드 + 크기 가드 (50KB) + 서버 SHA-256 재계산 + clientHash 비교</li>
     *   <li>{@link Slip#recordSignature} 호출 — INSPECTING/COMPLETED/SHIPPING 가드</li>
     *   <li>{@link SlipSignatureAudit#record} INSERT</li>
     *   <li>응답: signedAt + shareToken + 만료 시각</li>
     * </ol>
     *
     * @param batchToken delivery batch token (base64url 64자)
     * @param slipNo 전표번호 ({@code 2026/05/05-1} 또는 {@code 2026-05-05-1} slug 형식 모두 허용)
     * @param req 요청 body
     * @return 서명 결과 (shareToken 포함)
     * @throws BusinessException(NOT_FOUND) 토큰/슬립 미발견
     * @throws BusinessException(CONFLICT) batch token 만료, slip 단계 미충족
     * @throws BusinessException(INVALID_INPUT) PNG 50KB 초과, hash mismatch
     */
    public PublicSignatureResponse recordSignature(String batchToken, String slipNo,
                                                   PublicSignatureRequest req) {
        // 1. batch token 검증
        DeliveryBatch batch = batchRepository.findByBatchToken(batchToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 토큰입니다"));
        if (batch.isExpired()) {
            throw new BusinessException(ErrorCode.CONFLICT, "토큰이 만료되었습니다");
        }

        // 2. slip lookup (slipNo slug 양쪽 형식 허용 — design mobile-spec.md §1.1)
        String canonicalSlipNo = canonicalSlipNo(slipNo);
        Slip slip = findBatchSlip(batch.getId(), canonicalSlipNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "슬립을 찾을 수 없습니다"));

        // 3. PNG 디코드 + 50KB 가드 + 서버 hash 재계산
        byte[] png = decodePng(req.signaturePngBase64());
        if (png.length > PNG_MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "서명 PNG 가 너무 큽니다 (" + png.length + " bytes, 최대 " + PNG_MAX_BYTES + ")");
        }
        String serverHash = sha256Hex(png);
        if (!serverHash.equalsIgnoreCase(req.clientHash())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "서명 무결성 검증 실패 — 클라이언트 hash 가 일치하지 않습니다");
        }

        // 4. 도메인 메서드 위임 (단계 가드 + 5필드 갱신 + share token 발급)
        applyMutation(() -> slip.recordSignature(req.signerName(), png, serverHash,
                SignatureChannel.MOBILE_CANVAS));

        // 5. audit 적재 — actorUserId=NULL (공개 endpoint, 인증 없음)
        auditRepository.save(SlipSignatureAudit.record(slip.getId(),
                slip.getSignerName(), slip.getSignatureHash()));

        // 6. 응답
        return new PublicSignatureResponse(
                slip.getSignedAt(),
                slip.getSignatureShareToken(),
                slip.getSignatureShareExpiresAt(),
                slip.getSignatureHash());
    }

    /**
     * 인수자 view 조회 — Plan §2 의 endpoint 2번. read-only.
     *
     * @param shareToken 인수자 share 토큰
     * @return read-only 슬립 핵심 + PNG base64 (UUID 미노출)
     * @throws BusinessException(NOT_FOUND) 토큰 미발견 또는 미서명 슬립
     * @throws BusinessException(CONFLICT) 토큰 만료 (Controller 가 410 GONE 으로 매핑)
     */
    @Transactional(readOnly = true)
    public PublicSignatureViewResponse findByShareToken(String shareToken) {
        Slip slip = slipRepository.findBySignatureShareTokenAndIsDeletedFalse(shareToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "유효하지 않은 토큰입니다"));
        if (!slip.isSigned()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 토큰입니다");
        }
        if (slip.isSignatureShareExpired()) {
            throw new BusinessException(ErrorCode.CONFLICT, "토큰이 만료되었습니다");
        }

        BigDecimal total = BigDecimal.ZERO;
        java.util.List<PublicSignatureViewResponse.Slip.Line> lines = new java.util.ArrayList<>();
        for (SlipLine line : slip.getLines()) {
            lines.add(new PublicSignatureViewResponse.Slip.Line(
                    line.getProductName(),
                    line.getSpecification(),
                    line.getQuantity()));
            if (line.getLineTotal() != null) {
                total = total.add(line.getLineTotal());
            }
        }

        PublicSignatureViewResponse.Slip slipView = new PublicSignatureViewResponse.Slip(
                slip.getSlipNo(),
                slip.getPartnerName(),
                slip.getSlipDate(),
                lines,
                total);

        String pngBase64 = null;
        if (slip.getSignaturePng() != null) {
            pngBase64 = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(slip.getSignaturePng());
        }
        String hashShort = slip.getSignatureHash() != null && slip.getSignatureHash().length() >= 8
                ? slip.getSignatureHash().substring(0, 8)
                : slip.getSignatureHash();

        PublicSignatureViewResponse.Signature sig = new PublicSignatureViewResponse.Signature(
                slip.getSignerName(),
                slip.getSignedAt(),
                pngBase64,
                hashShort);

        return new PublicSignatureViewResponse(slipView, sig, slip.getSignatureShareExpiresAt());
    }

    /**
     * 관리자 서명 단건 조회 — Plan §2 의 endpoint 3번 (MANAGER/MASTER).
     *
     * @param slipId 슬립 UUID
     * @return 관리자용 서명 정보 (PNG base64 + hash 전체 64자 + share token)
     * @throws BusinessException(NOT_FOUND) 슬립 미발견
     */
    @Transactional(readOnly = true)
    public AdminSignatureResponse getSignature(UUID slipId) {
        Slip slip = slipRepository.findById(slipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "전표를 찾을 수 없습니다"));
        return AdminSignatureResponse.from(slip);
    }

    /**
     * 관리자 서명 무효화 — Plan §2 의 endpoint 4번 (MASTER only).
     *
     * <p>처리 순서:
     * <ol>
     *   <li>슬립 lookup (없으면 NOT_FOUND)</li>
     *   <li>직전 hash/signerName snapshot (도메인 호출 후 NULL 됨)</li>
     *   <li>{@link Slip#invalidateSignature} 호출 — signedAt!=null 가드</li>
     *   <li>{@link SlipSignatureAudit#invalidate} INSERT (actorUserId=호출자)</li>
     * </ol>
     *
     * @param slipId 슬립 UUID
     * @param reason 무효화 사유 (필수)
     * @param actorUserId 처리자 user-id (X-User-Id, 필수 — controller 가드)
     * @return 갱신된 응답 (signed=false)
     * @throws BusinessException(NOT_FOUND) 슬립 미발견
     * @throws BusinessException(CONFLICT) 미서명 상태 무효화 시도
     * @throws BusinessException(INVALID_INPUT) reason null/blank
     */
    public AdminSignatureResponse invalidateSignature(UUID slipId, String reason, String actorUserId) {
        Slip slip = slipRepository.findById(slipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "전표를 찾을 수 없습니다"));
        // 직전 hash/signerName snapshot — invalidate 후 NULL 됨
        String prevHash = slip.getSignatureHash();
        String prevSignerName = slip.getSignerName();
        applyMutation(() -> slip.invalidateSignature(reason));
        auditRepository.save(SlipSignatureAudit.invalidate(slip.getId(),
                prevSignerName, prevHash, reason, actorUserId));
        return AdminSignatureResponse.from(slip);
    }

    // ---------- helpers ----------

    /**
     * slipNo slug 정규화 — design mobile-spec.md §1.1 권장 매핑.
     * 모바일에서 {@code 2026-05-05-1} 슬러그로 들어오면 {@code 2026/05/05-1} 로 복원.
     * 이미 슬래시 형식이면 그대로 반환.
     */
    private String canonicalSlipNo(String slipNo) {
        if (slipNo == null) {
            return null;
        }
        if (slipNo.contains("/")) {
            return slipNo;
        }
        // yyyy-MM-dd-N → yyyy/MM/dd-N
        // 첫 3개 dash 만 슬래시로 (4번째 dash 는 seqNo 분리자 보존)
        String[] parts = slipNo.split("-", 4);
        if (parts.length == 4) {
            return parts[0] + "/" + parts[1] + "/" + parts[2] + "-" + parts[3];
        }
        return slipNo;
    }

    /**
     * batch 의 slipNo 단건 조회 — 같은 batchId + slipNo 매칭 슬립 1건. soft-delete 제외.
     */
    private java.util.Optional<Slip> findBatchSlip(UUID batchId, String slipNo) {
        List<Slip> slips = slipRepository.findAllByDeliveryBatchIdAndIsDeletedFalse(batchId);
        return slips.stream()
                .filter(s -> slipNo.equals(s.getSlipNo()))
                .findFirst();
    }

    /**
     * data URI 또는 raw base64 → PNG bytes.
     * design mobile-spec.md §3.6: {@code data:image/png;base64,iVBORw0...} 형식.
     */
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

    /** PNG bytes → SHA-256 hex 64자 (Plan §5 + design mobile-spec.md §3.7). */
    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SHA-256 알고리즘 미지원");
        }
    }

    /**
     * 도메인 mutation 실행 — 다른 service 와 일관된 예외 매핑 (CONFLICT/INVALID_INPUT).
     */
    private void applyMutation(Runnable mutation) {
        try {
            mutation.run();
        } catch (BusinessException ex) {
            throw ex;
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "전표 동시 수정 충돌 — 새로고침 후 재시도하세요");
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
    }
}
