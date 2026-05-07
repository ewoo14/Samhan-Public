package com.samhanair.logis.arologis.repository;

import com.samhanair.logis.arologis.domain.Dispatch;
import com.samhanair.logis.arologis.domain.DispatchType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Dispatch 저장소 — 날짜 / 유형 조회.
 */
@Repository
public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {

    List<Dispatch> findAllByDispatchDateOrderByCreatedAtDesc(LocalDate dispatchDate);

    List<Dispatch> findAllByDispatchDateAndDispatchTypeOrderByCreatedAtDesc(
            LocalDate dispatchDate, DispatchType dispatchType);
}
