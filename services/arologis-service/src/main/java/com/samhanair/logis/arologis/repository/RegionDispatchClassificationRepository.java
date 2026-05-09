package com.samhanair.logis.arologis.repository;

import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 가배차 지역 분류 마스터 저장소 — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>활성 행 group_name unique. CSV import 는 group_name 기준 upsert.
 */
@Repository
public interface RegionDispatchClassificationRepository
        extends JpaRepository<RegionDispatchClassification, UUID> {

    /** 활성 행 group_name lookup — CSV import upsert 사용. */
    Optional<RegionDispatchClassification> findByGroupName(String groupName);

    /** sort_order 오름차순 + group_name 보조 정렬. */
    List<RegionDispatchClassification> findAllByOrderBySortOrderAscGroupNameAsc();
}
