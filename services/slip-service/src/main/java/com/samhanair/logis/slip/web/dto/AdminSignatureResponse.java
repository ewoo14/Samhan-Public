package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.SignatureChannel;
import com.samhanair.logis.slip.domain.Slip;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 관리자 서명 조회 응답 — Slice C (signature-slice-C Plan §2).
 *
 * <p>{@code GET /api/slips/{id}/signature} 응답 (MANAGER/MASTER).
 * 관리자 화면이라 hash 전체 64자 노출 + share token + 만료 시각 포함. 디버그용 충분한 메타.
 *
 * @param slipId 슬립 UUID (admin 권한 — 노출 허용)
 * @param signed 서명 등록 여부 (false 면 모든 메타가 null)
 * @param signerName 인수자명
 * @param signedAt 서명 시각
 * @param signatureHash SHA-256 hex 64자 (전체)
 * @param signatureChannel 채널
 * @param signaturePngBase64 PNG data URI (관리자 검토용)
 * @param shareToken 인수자 share 토큰
 * @param shareTokenExpiresAt 만료 시각
 * @param shareExpired 현재 만료 상태 (계산값)
 */
public record AdminSignatureResponse(
        java.util.UUID slipId,
        boolean signed,
        String signerName,
        LocalDateTime signedAt,
        String signatureHash,
        SignatureChannel signatureChannel,
        String signaturePngBase64,
        String shareToken,
        LocalDateTime shareTokenExpiresAt,
        boolean shareExpired) {

    /** 도메인 entity 로부터 응답 매핑. */
    public static AdminSignatureResponse from(Slip slip) {
        boolean signed = slip.isSigned();
        String pngBase64 = null;
        if (signed && slip.getSignaturePng() != null) {
            pngBase64 = "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(slip.getSignaturePng());
        }
        return new AdminSignatureResponse(
                slip.getId(),
                signed,
                slip.getSignerName(),
                slip.getSignedAt(),
                slip.getSignatureHash(),
                slip.getSignatureChannel(),
                pngBase64,
                slip.getSignatureShareToken(),
                slip.getSignatureShareExpiresAt(),
                signed && slip.isSignatureShareExpired());
    }
}
