package com.samhanair.logis.user.repository;

import com.samhanair.logis.user.domain.RoleChangeHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 역할 변경 이력 저장소 — Phase 10 P0-5.
 *
 * <p>{@code SQLRestriction("is_deleted = false")} 가 entity 레벨에서 활성 행만 반환. 페이지 조회는
 * 화면이 1 직원에 한정되므로 List 반환 (대용량 회귀 부재).
 */
public interface RoleChangeHistoryRepository extends JpaRepository<RoleChangeHistory, UUID> {

    /** 직원별 변경 이력 — 최신순. */
    List<RoleChangeHistory> findAllByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
