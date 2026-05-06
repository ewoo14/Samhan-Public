package com.samhanair.logis.groupware.repository;

import com.samhanair.logis.groupware.domain.Schedule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** 일정 저장소 — 소유자별 + 기간 검색. */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    /** 단건 조회 — participants 컬렉션 fetch 강제 (update path lucky pass 명시적 fix). */
    @Override
    @EntityGraph(attributePaths = "participants")
    Optional<Schedule> findById(UUID id);

    /**
     * 소유자별 + 기간 겹침 조회. 이벤트 [startsAt, endsAt] 가 [from, to] 와 겹치는 모든 row.
     *
     * <p>겹침 = !(eventEnd < windowStart || eventStart > windowEnd) → eventEnd >= from AND eventStart <= to.
     */
    @Query("select distinct s from Schedule s left join fetch s.participants "
            + "where s.ownerId = :ownerId "
            + "and s.endsAt >= :from and s.startsAt <= :to "
            + "order by s.startsAt asc")
    List<Schedule> findOwnedInRange(@Param("ownerId") UUID ownerId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
