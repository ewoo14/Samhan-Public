package com.samhanair.logis.partner.repository;

import com.samhanair.logis.partner.domain.BlockedPartner;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Phase 10 PR-D Part B — BLOCK 발송금지 거래처 저장소.
 *
 * <p>{@code @SQLRestriction("is_deleted = false")} 가 활성 행만 자동 필터링하므로 본 저장소
 * 메서드는 별도 isDeleted 조건을 명시하지 않아도 활성 행만 반환한다. 다만 메서드 이름은
 * 의도 명시 + Spring Data JPA fluent 가독성을 위해 {@code AndIsDeletedFalse} 접미사를 보존
 * (V1 PartnerRepository 와 동일 패턴).
 */
@Repository
public interface BlockedPartnerRepository extends JpaRepository<BlockedPartner, UUID> {

    /**
     * PR-E 알림 발송 가드 — partner_code 의 활성 BLOCK row 존재 여부.
     *
     * <p>chat-service / push-service 의 전송 진입점에서 본 메서드로 가드. 활성 BLOCK 1건이라도
     * 존재하면 알림 발송 차단 (Samhan Public 사용자 명시 정책).
     *
     * @param partnerCode 거래처 코드
     * @return 활성 BLOCK 존재 시 true
     */
    boolean existsByPartnerCodeAndIsDeletedFalse(String partnerCode);

    /** partner_code 단건 lookup — admin 화면 / 차단 해제 흐름. */
    Optional<BlockedPartner> findByPartnerCode(String partnerCode);

    /** admin 목록 조회 — blocked_at 정렬은 Pageable 에 위임. */
    Page<BlockedPartner> findAll(Pageable pageable);
}
