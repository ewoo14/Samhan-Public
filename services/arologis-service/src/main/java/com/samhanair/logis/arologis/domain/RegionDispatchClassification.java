package com.samhanair.logis.arologis.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 가배차 지역 분류 마스터 — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>Samhan Public 프로그램에 native 이식. 노션 직접 통신 X — CSV 데이터 우리 DB 에 native 저장.
 *
 * <p>group_name 은 사용자 노출 식별자 (UUID 비공개 가드 — id 는 화면 노출 X).
 * keywords 는 시군구 콤마 구분 텍스트 ("송파구, 강남구, 서초구, ...") — 응용 단에서 split 사용.
 *
 * <p>BaseEntity 7 audit + Soft Delete (`@SQLRestriction`) 의무.
 */
@Entity
@Getter
@Table(name = "region_dispatch_classifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class RegionDispatchClassification extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 사용자 노출 식별자 — 활성 행 unique. UUID 노출 회피. */
    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    /** 시군구 콤마 구분 검색어 (예: "송파구, 강남구, 서초구"). 응용 단에서 split. */
    @Column(name = "keywords", nullable = false, columnDefinition = "TEXT")
    private String keywords;

    /** admin 화면 정렬 순서 (오름차순). */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private RegionDispatchClassification(String groupName, String keywords, Integer sortOrder) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("groupName 필수");
        }
        if (keywords == null || keywords.isBlank()) {
            throw new IllegalArgumentException("keywords 필수");
        }
        this.groupName = groupName.trim();
        this.keywords = keywords.trim();
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    /**
     * 신규 RegionDispatchClassification 생성.
     *
     * @param groupName 사용자 노출 그룹명 (예: "서울특별시")
     * @param keywords 시군구 콤마 구분 검색어
     * @param sortOrder 정렬 순서
     * @return 영속화 가능한 신규 인스턴스
     */
    public static RegionDispatchClassification of(String groupName, String keywords, Integer sortOrder) {
        return new RegionDispatchClassification(groupName, keywords, sortOrder);
    }

    /** 검색어 갱신 — admin 단건 수정 / CSV import upsert 사용. */
    public void updateKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            throw new IllegalArgumentException("keywords 필수");
        }
        this.keywords = keywords.trim();
    }

    /** 정렬 순서 갱신. */
    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    /**
     * keywords 콤마 split → 검색어 List (trim + 빈 값 제외).
     *
     * <p>매칭 utility ({@code RegionClassifier}) 가 사용.
     *
     * @return 검색어 리스트 (불변)
     */
    public List<String> splitKeywords() {
        if (keywords == null || keywords.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
