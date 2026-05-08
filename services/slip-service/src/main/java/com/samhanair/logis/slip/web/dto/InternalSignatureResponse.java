package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.SignatureSource;
import com.samhanair.logis.slip.domain.Slip;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Internal 전자서명 등록 응답 — Phase 10 W10-4 (PR #99) 신규.
 *
 * <p>arologis-service 의 SlipClient 가 호출한 {@code POST /internal/slips/{slipId}/signatures}
 * 의 응답 body. ApiResponse&lt;InternalSignatureResponse&gt; wrapper 안에 포장된다 (W10-3 F-3 채택).
 *
 * <p>UUID 비공개 가드 — slipId 는 호출자가 이미 알고 있는 식별자라 그대로 echo 하지만,
 * 사용자 노출 시는 slipNo (yyyy/MM/dd-N) 만 사용해야 한다.
 *
 * @param slipId 슬립 UUID (echo)
 * @param slipNo 전표번호 (yyyy/MM/dd-N) — 사용자 노출 식별자
 * @param signatureSource 등록된 source (LINK 또는 APP)
 * @param signedAt 서명 시각 (인수자) — 본 endpoint 가 인수자 서명을 갱신했을 때 채워짐
 * @param driverSignedAt 서명 시각 (기사) — 본 endpoint 가 기사 서명을 갱신했을 때 채워짐
 * @param signatureHash 등록된 SHA-256 hex (있으면)
 * @param signed 인수자 서명 등록 여부 (signedAt != null)
 * @param driverSigned 기사 서명 등록 여부 (driverSignedAt != null)
 */
public record InternalSignatureResponse(
        UUID slipId,
        String slipNo,
        SignatureSource signatureSource,
        LocalDateTime signedAt,
        LocalDateTime driverSignedAt,
        String signatureHash,
        boolean signed,
        boolean driverSigned
) {
    public static InternalSignatureResponse from(Slip slip, SignatureSource source, boolean isDriver) {
        return new InternalSignatureResponse(
                slip.getId(),
                slip.getSlipNo(),
                source,
                slip.getSignedAt(),
                slip.getDriverSignedAt(),
                isDriver ? slip.getDriverSignatureHash() : slip.getSignatureHash(),
                slip.isSigned(),
                slip.isDriverSigned()
        );
    }
}
