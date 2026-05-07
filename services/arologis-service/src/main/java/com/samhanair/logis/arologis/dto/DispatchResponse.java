package com.samhanair.logis.arologis.dto;

import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.DispatchType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dispatch 요약 응답 DTO — UUID 비공개 가드 (id 필드는 admin 전용 응답에만 사용).
 *
 * <p>본 PR (W10-1) 은 admin endpoint 에서 dispatchId 를 응답에 포함 (admin 화면 routing 용).
 * Driver-app endpoint 는 별도 DTO 로 driverCode + 정차 정보만 노출.
 */
public record DispatchResponse(
        String dispatchId,
        LocalDate dispatchDate,
        DispatchType dispatchType,
        LocalDateTime createdAt
) {

    public static DispatchResponse from(Dispatch dispatch) {
        return new DispatchResponse(
                dispatch.getId() == null ? null : dispatch.getId().toString(),
                dispatch.getDispatchDate(),
                dispatch.getDispatchType(),
                dispatch.getCreatedAt());
    }
}
