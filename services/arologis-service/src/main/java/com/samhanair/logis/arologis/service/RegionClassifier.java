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
 * <h2>매칭 알고리즘 — TM PR #115 광역 prefix 가중치 정정</h2>
 *
 * <p>키워드 모호성 회귀 방지: "중구"가 서울/대구/부산/인천 등 다중 그룹에 존재하므로 단순 sort_order
 * 우선 매칭은 첫 번째 sort_order 그룹으로 잘못 분류된다 (예: "대구 중구..." → 서울특별시).
 * 따라서 광역 prefix 가중치를 1차 우선 적용:
 *
 * <ol>
 *   <li><strong>1차 — 광역 prefix 매칭 (최우선)</strong> — address 가 group_name 의 광역 prefix
 *       (서울/인천/대구/부산/광주/대전/울산/세종/제주) 를 포함하면 해당 광역 그룹의 keywords 로 한정 매칭.
 *       → "대구 중구..." → 광역 = 대구 → 대구광역시의 keywords 안에서 "중구" 매칭 → "대구광역시"</li>
 *   <li><strong>2차 — sort_order 우선 keywords 매칭 (광역 prefix 미존재 시 fallback)</strong> —
 *       전체 활성 분류 sort_order 오름차순 + group_name 보조 정렬 후 keywords substring 검색.
 *       → "수원시 영통구..." → "수원" → 경기남부</li>
 *   <li><strong>3차 — group_name 자체 substring fallback</strong> ("서울특별시" → "서울" prefix 만으로
 *       매칭 — 기존 동작 유지)</li>
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

        // 1차 — 광역 prefix 가중치 매칭 (TM PR #115 정정).
        // address 에 광역 prefix (예: "대구") 가 포함되어 있으면 해당 광역 그룹의 keywords 로 한정 검색.
        // → "중구" 같은 모호 키워드의 다중 그룹 충돌 방지.
        for (RegionDispatchClassification rdc : all) {
            String prefix = stripCityPrefix(rdc.getGroupName());
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (!normalized.contains(prefix)) {
                continue;
            }
            // 광역 prefix 적중 — 본 그룹의 keywords 안에서만 매칭 시도
            for (String kw : rdc.splitKeywords()) {
                if (kw.isBlank()) {
                    continue;
                }
                String kwNormalized = kw.replace(" ", "");
                if (normalized.contains(kwNormalized)) {
                    return rdc.getGroupName();
                }
            }
            // 광역 prefix 만 존재하고 시군구 미특정 — 그래도 그 광역 그룹으로 분류
            // (예: "서울 어딘가" 같이 시군구 미상)
            return rdc.getGroupName();
        }

        // 2차 — sort_order 우선 keywords 정확 매칭 (광역 prefix 미존재 시 — "수원시 영통구..." 등)
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

        // 3차 — group_name 자체 substring fallback (legacy 호환)
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
