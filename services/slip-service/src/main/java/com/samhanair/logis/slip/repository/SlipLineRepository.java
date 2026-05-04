package com.samhanair.logis.slip.repository;

import com.samhanair.logis.slip.domain.SlipLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Slip 라인 — 라인 단건 mutation 보조 (조회는 보통 헤더 cascade 로 처리). */
public interface SlipLineRepository extends JpaRepository<SlipLine, UUID> {
}
