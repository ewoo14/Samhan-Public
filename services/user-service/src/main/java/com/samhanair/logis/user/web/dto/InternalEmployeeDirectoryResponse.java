package com.samhanair.logis.user.web.dto;

import java.util.UUID;

/**
 * 종합견적서 담당자 directory internal 응답.
 *
 * <p>담당자는 거래처 담당자가 아니라 우리 행정직원(Employee)이다. 형제 service 전용 응답이므로
 * userId UUID 를 포함할 수 있으며, estimate-app 은 ecountCode/fullName 만 legacy 담당자 shape 로 사용한다.
 *
 * @param userId 직원 UUID
 * @param fullName 직원 이름
 * @param ecountCode 이카운트 직원 코드 (nullable)
 * @param departmentName 부서명 (nullable)
 */
public record InternalEmployeeDirectoryResponse(
        UUID userId,
        String fullName,
        String ecountCode,
        String departmentName
) {
}
