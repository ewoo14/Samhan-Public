package com.samhanair.logis.partner.repository;

import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** 거래처 마스터 저장소 — partnerCode lookup (M5 의존성 해소) + 관리자 검색. */
@Repository
public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    /** 거래처 코드 lookup — slip-service /internal/partners/{partnerCode} 호출의 핵심 query. */
    Optional<Partner> findByPartnerCode(String partnerCode);

    /**
     * 거래처 코드 bulk lookup — Phase 9 W5 신규 (D-P9-16).
     *
     * <p>dashboard-service 의 매출 집계 화면 등에서 partnerCode N건 동시 조회 시 직렬 RPC N회 → 1회 batch
     * 호출 전환의 backing query. Spring Data JPA 가 {@code IN} 절을 자동 생성. 빈 컬렉션 호출 시
     * 빈 리스트 반환 (DB 조회 자체 회피는 service 계층 책임).
     */
    List<Partner> findAllByPartnerCodeIn(Collection<String> partnerCodes);

    /** 사업자번호 중복 검사 — 신규 등록 가드. */
    Optional<Partner> findByBizNo(String bizNo);

    /** 상태별 페이지 조회 (admin 검색). */
    Page<Partner> findAllByStatus(PartnerStatus status, Pageable pageable);

    /** 거래처명 부분 일치 검색 (admin 검색, 대소문자 무시). */
    List<Partner> findAllByNameContainingIgnoreCase(String namePart);
}
