package com.samhanair.logis.arologis.dto;

import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import java.util.UUID;

/**
 * 가배차 지역 분류 응답 DTO — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>id 는 admin 화면 routing (수정/삭제) 용. 사용자 노출 식별자는 group_name.
 *
 * @param id UUID (admin routing)
 * @param groupName 그룹명 (사용자 노출)
 * @param keywords 시군구 콤마 구분 검색어
 * @param sortOrder 정렬 순서
 */
public record RegionResponse(UUID id, String groupName, String keywords, Integer sortOrder) {

    public static RegionResponse from(RegionDispatchClassification entity) {
        return new RegionResponse(
                entity.getId(),
                entity.getGroupName(),
                entity.getKeywords(),
                entity.getSortOrder());
    }
}
