package com.samhanair.logis.user.web.dto;

/**
 * 내부 서명 배치 조회 항목 - C1a. slip-service 결재란 인감 enrichment 용.
 *
 * <p>UUID 비공개 - 본 DTO 는 형제 service 한정 응답이며 PNG base64 + 등록 시각만 노출(userId 키는
 * Map 키로만 사용). signaturePngBase64 는 {@code data:image/png;base64,...} data URI 형식.
 *
 * @param signaturePngBase64 서명 PNG data URI (등록된 사원만 맵에 포함)
 * @param signedAt 등록 시각 ISO-8601
 */
public record EmployeeSignatureDto(
        String signaturePngBase64,
        String signedAt
) {
}
