package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.user.domain.Employee;
import java.time.format.DateTimeFormatter;

/**
 * 사원 서명 등록/조회 응답 - C1a. PATCH .../signature 200 body.
 *
 * @param registered 서명 등록 여부
 * @param signedAt 등록 시각 ISO-8601 (미등록 시 null)
 * @param signatureChannel 입력 채널 이름 (미등록 시 null)
 */
public record EmployeeSignatureResponse(
        boolean registered,
        String signedAt,
        String signatureChannel
) {
    /** Employee 의 현재 서명 상태로 응답 매핑. */
    public static EmployeeSignatureResponse from(Employee employee) {
        boolean registered = employee.getSignedAt() != null;
        return new EmployeeSignatureResponse(
                registered,
                registered ? employee.getSignedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null,
                employee.getSignatureChannel() == null ? null : employee.getSignatureChannel().name());
    }
}
