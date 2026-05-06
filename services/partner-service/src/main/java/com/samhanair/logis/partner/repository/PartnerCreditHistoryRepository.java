package com.samhanair.logis.partner.repository;

import com.samhanair.logis.partner.domain.CreditEventType;
import com.samhanair.logis.partner.domain.PartnerCreditHistory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 신용 거래 이력 저장소. append-only — 본 entity 는 update 호출 없음. */
@Repository
public interface PartnerCreditHistoryRepository extends JpaRepository<PartnerCreditHistory, UUID> {

    /** 거래처별 이력 페이지 조회 (admin 화면). */
    Page<PartnerCreditHistory> findAllByPartnerIdOrderByOccurredAtDesc(UUID partnerId, Pageable pageable);

    /** 기간 + 이벤트 유형 필터 (회계 집계용). */
    List<PartnerCreditHistory> findAllByPartnerIdAndEventTypeAndOccurredAtBetweenOrderByOccurredAtAsc(
            UUID partnerId, CreditEventType eventType, LocalDateTime from, LocalDateTime to);
}
