package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import com.samhanair.logis.arologis.repository.RegionDispatchClassificationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주소 문자열 → 가배차 지역 그룹 매칭 utility — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>KakaoDispatchParser 가 stop.parsedAddress 를 본 classifier 에 통과시켜 regionGroup 을
 * 결정. 매칭 안 됨 시 null 반환 (저장은 진행, classified_region_group 만 NULL).
 *
 * <p>매칭 알고리즘 (단순 substring contains, sort_order 우선):
 * <ol>
 *   <li>전체 활성 분류 목록을 sort_order 오름차순 + group_name 보조 정렬로 로드</li>
 *   <li>각 분류의 keywords (콤마 split) 중 하나라도 address 에 포함되면 해당 group_name 반환</li>
 *   <li>"서울"/"인천"/"경기" 같은 광역 키워드는 keywords 에 없으나 group_name 자체로 fallback 매칭</li>
 *   <li>모두 실패 시 null</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionClassifier {

    private final RegionDispatchClassificationRepository repository;

    /**
     * 주소 → 그룹명 매칭.
     *
     * @param address 주소 문자열 (parsedAddress, null/blank 가능)
     * @return 매칭된 그룹명 (예: "서울특별시") ; 매칭 실패 시 null
     */
    @Transactional(readOnly = true)
    public String classify(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.replace(" ", "");
        List<RegionDispatchClassification> all = repository.findAllByOrderBySortOrderAscGroupNameAsc();

        // 1차 — keywords 정확 매칭 (sort_order 우선)
        for (RegionDispatchClassification rdc : all) {
            for (String kw : rdc.splitKeywords()) {
                if (kw.isBlank()) {
                    continue;
                }
                String kwNormalized = kw.replace(" ", "");
                if (normalized.contains(kwNormalized)) {
                    return rdc.getGroupName();
                }
            }
        }

        // 2차 — group_name 자체 substring fallback ("서울특별시" → "서울")
        for (RegionDispatchClassification rdc : all) {
            String prefix = stripCityPrefix(rdc.getGroupName());
            if (prefix != null && !prefix.isBlank() && normalized.contains(prefix)) {
                return rdc.getGroupName();
            }
        }
        log.debug("RegionClassifier 매칭 실패 — address={}", address);
        return null;
    }

    /**
     * 그룹명 → 광역 prefix 변환 ("서울특별시" → "서울", "인천광역시" → "인천", "경기동부" → null —
     * 경기 4 그룹 (동부/남부/서부/북부) 충돌 회피).
     */
    private String stripCityPrefix(String groupName) {
        if (groupName == null) {
            return null;
        }
        if (groupName.endsWith("특별시")) {
            return groupName.substring(0, groupName.length() - 3);
        }
        if (groupName.endsWith("광역시")) {
            return groupName.substring(0, groupName.length() - 3);
        }
        if (groupName.endsWith("특별자치시") || groupName.endsWith("특별자치도")) {
            return groupName.substring(0, groupName.length() - 5);
        }
        if (groupName.endsWith("도")) {
            return groupName.substring(0, groupName.length() - 1);
        }
        // "경기동부" 등 4 분할 그룹은 fallback prefix 매칭 비활성 (광역 "경기" 충돌 회피)
        return null;
    }
}
