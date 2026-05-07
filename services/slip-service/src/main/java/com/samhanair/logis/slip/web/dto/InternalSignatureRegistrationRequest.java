package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.SignatureSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Internal 전자서명 등록 요청 — Phase 10 W10-4 (PR #99) 신규.
 *
 * <p>arologis-service 의 SlipClient 가 정차 완료 시 driver-app 캡처 서명을 slip-service 로 전파하기
 * 위해 호출하는 {@code POST /internal/slips/{slipId}/signatures} endpoint 의 request body.
 *
 * <p>현 슬라이스에서는 imageRef 가 S3 placeholder 문자열 (Phase 11 cutover 시 실 업로드).
 * PNG bytes 자체는 본 endpoint 로 전송되지 않고 imageRef 로만 참조 — slip-service 측 signature_png
 * 컬럼은 PNG-skip 모드로 비워두고 hash 만 보존.
 *
 * @param signatureSource 서명 source — {@link SignatureSource#APP} 만 본 endpoint 에서 허용
 *     (LINK 는 기존 공개 모바일 endpoint 전용).
 * @param imageRef 이미지 reference (S3 placeholder, 1~500자, 필수)
 * @param signatureHash SHA-256 hex 64자 (선택 — arologis 가 PNG bytes 보유 안 하면 null)
 * @param signerName 인수자명 (선택, ≤50자) — null 이면 driverCode 를 fallback signer 로 사용
 * @param driverCode 기사 식별 코드 (선택, ≤50자) — 기사 서명 등록 분기용
 * @param capturedAt 캡처 시각 (필수, ISO8601 UTC)
 * @param capturedLatitude GPS 위도 (선택, APP source 일 때 권장)
 * @param capturedLongitude GPS 경도 (선택)
 */
public record InternalSignatureRegistrationRequest(
        @NotNull SignatureSource signatureSource,
        @NotNull @Size(min = 1, max = 500) String imageRef,
        @Size(max = 64) String signatureHash,
        @Size(max = 50) String signerName,
        @Size(max = 50) String driverCode,
        @NotNull LocalDateTime capturedAt,
        BigDecimal capturedLatitude,
        BigDecimal capturedLongitude
) {}
