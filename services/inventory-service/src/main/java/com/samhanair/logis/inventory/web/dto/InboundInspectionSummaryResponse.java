package com.samhanair.logis.inventory.web.dto;

import com.samhanair.logis.inventory.client.SlipDetail;
import com.samhanair.logis.inventory.domain.InboundInspection;
import com.samhanair.logis.inventory.domain.InspectionStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 입고 검수 목록 응답 — history 페이지용 요약 정보 (라인 미포함).
 *
 * @param inspectionId InboundInspection 내부 PK
 * @param slipId       slip-service Slip UUID (internal)
 * @param slipNo       슬립번호 (사용자 노출 식별자)
 * @param partnerName  거래처명 snapshot
 * @param partnerBusinessNo 거래처 코드(bizNo) snapshot
 * @param slipDate     입고일 snapshot
 * @param status       검수 상태
 * @param inspectorId  검수 담당자 user-id
 * @param inspectorName 검수 담당자 표시명 (미조회 시 null)
 * @param stockApplied 재고 반영 여부
 * @param completedAt  검수 완료 일시 (미완료 시 null)
 * @param createdAt    생성 일시
 */
public record InboundInspectionSummaryResponse(
        UUID inspectionId,
        UUID slipId,
        String slipNo,
        String partnerName,
        String partnerBusinessNo,
        String slipDate,
        InspectionStatus status,
        String inspectorId,
        String inspectorName,
        boolean stockApplied,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    /**
     * InboundInspection 엔티티로부터 요약 응답 DTO 를 생성한다.
     *
     * @param inspection 영속 상태의 InboundInspection
     * @return InboundInspectionSummaryResponse
     */
    public static InboundInspectionSummaryResponse from(InboundInspection inspection) {
        return new InboundInspectionSummaryResponse(
                inspection.getId(),
                inspection.getSlipId(),
                inspection.getSlipNo(),
                null,
                null,
                null,
                inspection.getStatus(),
                inspection.getInspectorId(),
                null,
                inspection.isStockApplied(),
                inspection.getCompletedAt(),
                inspection.getCreatedAt()
        );
    }

    /**
     * 검수 헤더와 slip-service 상세 snapshot 으로 목록 응답 DTO 를 생성한다.
     *
     * @param inspection 영속 상태의 InboundInspection
     * @param slipDetail slip-service 상세 응답
     * @param inspectorName 검수 담당자 표시명 (미조회 시 null)
     * @return InboundInspectionSummaryResponse
     */
    public static InboundInspectionSummaryResponse from(
            InboundInspection inspection, SlipDetail slipDetail, String inspectorName) {
        return new InboundInspectionSummaryResponse(
                inspection.getId(),
                inspection.getSlipId(),
                inspection.getSlipNo(),
                slipDetail == null ? null : slipDetail.partnerName(),
                slipDetail == null ? null : slipDetail.businessNumber(),
                slipDetail == null ? null : slipDetail.slipDate(),
                inspection.getStatus(),
                inspection.getInspectorId(),
                inspectorName,
                inspection.isStockApplied(),
                inspection.getCompletedAt(),
                inspection.getCreatedAt()
        );
    }
}
