package com.samhanair.logis.notification.repository;

import com.samhanair.logis.notification.domain.NotificationCenter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * NotificationCenter 조회.
 *
 * <p>target_role CSV / target_user_id UUID 조합 필터. role 매칭은 PostgreSQL 의 {@code string_to_array}
 * + ANY 패턴으로 처리한다.
 */
public interface NotificationCenterRepository extends JpaRepository<NotificationCenter, UUID> {

    /**
     * 사용자 미확인 알림 (read_at IS NULL) 조회. 최신순.
     * (target_role 에 role 이 포함되거나, target_user_id = userId) 조합.
     */
    @Query(value = """
            SELECT n.* FROM notification_center n
            WHERE n.is_deleted = FALSE
              AND n.read_at IS NULL
              AND (
                   n.target_user_id = :userId
                OR (n.target_role IS NOT NULL
                    AND :role = ANY(string_to_array(n.target_role, ',')))
              )
            ORDER BY n.created_at DESC
            """, nativeQuery = true)
    List<NotificationCenter> findMyUnread(@Param("userId") UUID userId, @Param("role") String role);

    /**
     * 사용자 전체 알림 history (read_at 무관). 페이지네이션.
     */
    @Query(value = """
            SELECT n.* FROM notification_center n
            WHERE n.is_deleted = FALSE
              AND (
                   n.target_user_id = :userId
                OR (n.target_role IS NOT NULL
                    AND :role = ANY(string_to_array(n.target_role, ',')))
              )
            ORDER BY n.created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM notification_center n
            WHERE n.is_deleted = FALSE
              AND (
                   n.target_user_id = :userId
                OR (n.target_role IS NOT NULL
                    AND :role = ANY(string_to_array(n.target_role, ',')))
              )
            """,
            nativeQuery = true)
    Page<NotificationCenter> findMyHistory(@Param("userId") UUID userId, @Param("role") String role, Pageable pageable);
}
