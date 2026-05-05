package com.samhanair.logis.slip.repository;

import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipSourceType;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Slip 헤더 — 단건/필터 페이지 조회. partial unique 는 {@code slip_no} 컬럼에 적용 (V1 SQL). */
public interface SlipRepository extends JpaRepository<Slip, UUID> {

    /** 전표번호({@code yyyy/MM/dd-NNN}) 단건 조회. soft-delete 제외. */
    Optional<Slip> findBySlipNo(String slipNo);

    /** 상태별 페이지 조회. soft-delete 제외. */
    Page<Slip> findAllByStatusAndIsDeletedFalse(SlipStatus status, Pageable pageable);

    /** slipType 별 페이지 조회. soft-delete 제외. */
    Page<Slip> findAllBySlipTypeAndIsDeletedFalse(SlipType slipType, Pageable pageable);

    /** slipType + status 동시 필터 페이지. soft-delete 제외. */
    Page<Slip> findAllBySlipTypeAndStatusAndIsDeletedFalse(SlipType slipType, SlipStatus status, Pageable pageable);

    /** 활성 전체 페이지. soft-delete 제외. */
    Page<Slip> findAllByIsDeletedFalse(Pageable pageable);

    // ---- Slice B (notification-slice-B) ----

    /**
     * 같은 driverPhone + slipDate 의 슬립 목록 — 자동 그룹화의 source.
     * 미배치 (deliveryBatchId IS NULL) 슬립만 반환할지 여부는 호출자 (DeliveryBatchService) 결정.
     */
    List<Slip> findAllByDriverPhoneAndSlipDateAndIsDeletedFalse(String driverPhone, LocalDate slipDate);

    /**
     * 특정 배송일에 driverPhone 이 채워진 모든 슬립 — 자동 그룹화 candidate set.
     * 같은 phone 끼리 묶어 batch 1건씩 생성. 호출자에서 phone 별 group by 후 처리.
     */
    List<Slip> findAllBySlipDateAndDriverPhoneIsNotNullAndIsDeletedFalse(LocalDate slipDate);

    /** 특정 배치에 속한 슬립 목록 — 배치 상세 화면 / 공개 모바일 페이지 source. */
    List<Slip> findAllByDeliveryBatchIdAndIsDeletedFalse(UUID deliveryBatchId);

    // ---- Slice C (signature-slice-C) ----

    /**
     * signatureShareToken 단건 조회 — 인수자 view 공개 endpoint source.
     * partial UNIQUE INDEX (V5) 로 token 발급된 슬립만 유일성 보장.
     */
    Optional<Slip> findBySignatureShareTokenAndIsDeletedFalse(String signatureShareToken);

    // ---- Phase 6 M5 (slip-service-integration) — 발행 출처 + idempotency 조회 ----

    /**
     * idempotencyKey 단건 조회 — Sync REST 발행 endpoint 의 1단계 가드.
     * partial UNIQUE INDEX (V7) 로 token 발급된 슬립만 유일성 보장.
     * 같은 키 + 같은 본문 → 200 (기존 slipNo). 같은 키 + 다른 본문 → 409 Conflict.
     */
    Optional<Slip> findByIdempotencyKeyAndIsDeletedFalse(String idempotencyKey);

    /**
     * 발행 출처 기준 조회 — {@code GET /api/v1/slips/by-source} endpoint source.
     * 같은 estimateNumber/partnerOrderId 의 슬립 목록 (정상적으로는 1건, 재시도 충돌 시 0건 또는 1건).
     */
    List<Slip> findAllBySourceTypeAndSourceIdAndIsDeletedFalse(
            SlipSourceType sourceType, String sourceId);
}
