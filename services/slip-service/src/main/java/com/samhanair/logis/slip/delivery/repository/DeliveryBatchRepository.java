package com.samhanair.logis.slip.delivery.repository;

import com.samhanair.logis.slip.delivery.domain.DeliveryBatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

/**
 * DeliveryBatch — 배송 배치 저장소 (Slice B notification-slice-B).
 * Soft-delete 는 entity 의 {@code @SQLRestriction("is_deleted = false")} 로 자동 적용.
 */
public interface DeliveryBatchRepository extends JpaRepository<DeliveryBatch, UUID> {

    /** 토큰 단건 조회 — 공개 모바일 endpoint 의 핵심 lookup. */
    Optional<DeliveryBatch> findByBatchToken(String batchToken);

    /**
     * 배송일 + 기사 연락처 단건 조회 — 자동 그룹화 시 기존 배치 재사용 판정.
     * Plan §5.2 partial unique index {@code (driver_phone, batch_date) WHERE is_deleted=false}
     * 와 의미 정렬. 활성(soft-delete 제외) 1건만 반환되어야 한다.
     */
    Optional<DeliveryBatch> findByDriverPhoneAndBatchDate(String driverPhone, LocalDate batchDate);

    /**
     * 배송일 기준 페이지 (sent 필터 옵션) — 링크발송 화면 목록 source.
     * Spring Data 명명 쿼리는 nullable 필터를 단일 메서드로 표현하기 어려우므로 JPQL 사용.
     *
     * @param batchDate 배송일 필터 (필수)
     * @param sentFilter null 이면 전체, true 이면 발송완료만, false 이면 미발송만
     */
    @Query("""
            SELECT b FROM DeliveryBatch b
             WHERE b.batchDate = :batchDate
               AND (:sentFilter IS NULL
                    OR (:sentFilter = TRUE AND b.smsSentAt IS NOT NULL)
                    OR (:sentFilter = FALSE AND b.smsSentAt IS NULL))
             ORDER BY b.driverName ASC
            """)
    List<DeliveryBatch> findByBatchDateWithSentFilter(@Param("batchDate") LocalDate batchDate,
                                                     @Param("sentFilter") Boolean sentFilter);
}
