package com.samhanair.logis.slip.delivery.web.dto;

import com.samhanair.logis.slip.delivery.domain.DeliveryBatch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DeliveryBatch admin 응답 — Plan §4.1 관리자 endpoint 용.
 * 관리자 화면 LinkDispatchListPage / BatchDetailModal 에 표시.
 *
 * <p>UUID 비공개 가드 (memory {@code feedback_uuid_no_user_visibility.md}):
 * 본 응답은 admin endpoint 한정 — id 노출 OK. 공개 모바일 endpoint 는
 * {@link PublicBatchResponse} 별도 사용 (slip.id UUID 미노출).
 *
 * @param id 배치 UUID
 * @param batchToken base64url 64자 토큰 (모바일 링크용)
 * @param driverName 기사명
 * @param driverPhone 기사 연락처
 * @param batchDate 배송일
 * @param tokenExpiresAt 토큰 만료 시각
 * @param smsSentAt SMS 발송 완료 시각 (null 이면 미발송)
 * @param smsLastError 직전 SMS 발송 실패 사유 (null 이면 정상)
 * @param slipCount 배치 내 슬립 건수
 * @param slipNos 배치 내 슬립 번호 목록 (UUID 미노출 — slipNo 만)
 */
public record DeliveryBatchResponse(
        UUID id,
        String batchToken,
        String driverName,
        String driverPhone,
        LocalDate batchDate,
        LocalDateTime tokenExpiresAt,
        LocalDateTime smsSentAt,
        String smsLastError,
        int slipCount,
        List<String> slipNos) {

    public static DeliveryBatchResponse of(DeliveryBatch batch, List<String> slipNos) {
        return new DeliveryBatchResponse(
                batch.getId(),
                batch.getBatchToken(),
                batch.getDriverName(),
                batch.getDriverPhone(),
                batch.getBatchDate(),
                batch.getTokenExpiresAt(),
                batch.getSmsSentAt(),
                batch.getSmsLastError(),
                slipNos == null ? 0 : slipNos.size(),
                slipNos);
    }
}
