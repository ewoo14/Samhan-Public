package com.samhanair.logis.arologis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가배차 지역 분류 단건 입력 — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>POST /admin/regions (group_name 필수) + PUT /admin/regions/{id} (keywords/sortOrder 갱신).
 *
 * @param groupName 그룹명 (POST 시 필수, PUT 시 무시)
 * @param keywords 시군구 콤마 구분 검색어 (필수)
 * @param sortOrder 정렬 순서 (옵션, null = 0)
 */
public record RegionUpsertRequest(
        @Size(max = 50, message = "groupName 은 50자 이하") String groupName,
        @NotBlank(message = "keywords 필수") String keywords,
        Integer sortOrder
) {}
