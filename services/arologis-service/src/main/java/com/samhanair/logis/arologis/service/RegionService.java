package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import com.samhanair.logis.arologis.repository.RegionDispatchClassificationRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가배차 지역 분류 CRUD service — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>admin 화면 단건 추가/수정/삭제 + 전체 조회. CSV 일괄 import 는 {@link RegionImportService}.
 *
 * <p>Soft Delete 만 (markDeleted) — UUID 비공개 가드 (사용자 노출 = group_name).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionDispatchClassificationRepository repository;

    /** 전체 활성 분류 조회 (sort_order 오름차순 + group_name 보조). */
    @Transactional(readOnly = true)
    public List<RegionDispatchClassification> findAll() {
        return repository.findAllByOrderBySortOrderAscGroupNameAsc();
    }

    /** 단건 조회 — 미존재 시 BusinessException. */
    @Transactional(readOnly = true)
    public RegionDispatchClassification findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "지역 분류 미존재: " + id));
    }

    /**
     * 신규 분류 등록 — group_name 활성 행 unique 가드.
     *
     * @param groupName 그룹명 (예: "서울특별시")
     * @param keywords 시군구 콤마 구분 검색어
     * @param sortOrder 정렬 순서 (null = 0)
     * @return 저장된 entity
     */
    @Transactional
    public RegionDispatchClassification create(String groupName, String keywords, Integer sortOrder) {
        repository.findByGroupName(groupName).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 존재하는 그룹명: " + groupName);
        });
        RegionDispatchClassification saved = repository.save(
                RegionDispatchClassification.of(groupName, keywords, sortOrder));
        log.info("지역 분류 신규 등록 — groupName={}, sortOrder={}", groupName, sortOrder);
        return saved;
    }

    /** keywords + sort_order 갱신 (group_name 은 불변 — 신규 추가/Soft Delete 후 재등록 패턴). */
    @Transactional
    public RegionDispatchClassification update(UUID id, String keywords, Integer sortOrder) {
        RegionDispatchClassification entity = findById(id);
        if (keywords != null && !keywords.isBlank()) {
            entity.updateKeywords(keywords);
        }
        if (sortOrder != null) {
            entity.updateSortOrder(sortOrder);
        }
        log.info("지역 분류 갱신 — groupName={}, sortOrder={}", entity.getGroupName(), entity.getSortOrder());
        return entity;
    }

    /** Soft Delete — admin 전용. */
    @Transactional
    public void softDelete(UUID id, String userId) {
        RegionDispatchClassification entity = findById(id);
        entity.markDeleted(userId == null ? "system" : userId);
        log.info("지역 분류 Soft Delete — groupName={}, by={}", entity.getGroupName(), userId);
    }
}
