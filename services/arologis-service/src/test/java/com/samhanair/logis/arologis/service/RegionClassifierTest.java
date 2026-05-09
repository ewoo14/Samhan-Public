package com.samhanair.logis.arologis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import com.samhanair.logis.arologis.repository.RegionDispatchClassificationRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RegionClassifier 단위 테스트 — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>주소 → 그룹명 매칭 정확성 검증.
 *
 * <h2>광역 prefix 가중치 회귀 (TM PR #115 추가)</h2>
 * <p>"중구"가 서울/대구/부산/인천 다중 그룹에 동시 존재하므로, address 의 광역 prefix
 * (예: "대구") 적중 시 해당 광역 그룹의 keywords 안에서만 한정 매칭되어야 한다.
 *
 * <ul>
 *   <li>case 1 — "서울 송파구..." → "서울특별시"</li>
 *   <li>case 2 — "수원시 영통구..." → "경기남부"</li>
 *   <li>case 3 — "인천 남동구..." → "인천광역시"</li>
 *   <li>case 4 — "대구 수성구..." → "대구광역시"</li>
 *   <li>case 5 — 매칭 안 됨 → null (외국 주소 / blank)</li>
 *   <li>case 6 — 모호 "중구" 다중 그룹 광역 prefix 가중치 (회귀 방지)</li>
 *   <li>case 7 — 광역 prefix 만 존재 (시군구 미특정) → 광역 그룹</li>
 * </ul>
 */
class RegionClassifierTest {

    private final RegionDispatchClassificationRepository repository =
            mock(RegionDispatchClassificationRepository.class);

    private RegionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RegionClassifier(repository);
        // 노션 export CSV 19+ 그룹 일부 (실제 시드 패턴 모방)
        when(repository.findAllByOrderBySortOrderAscGroupNameAsc()).thenReturn(List.of(
                RegionDispatchClassification.of("서울특별시",
                        "송파구, 강남구, 서초구, 강동구, 광진구, 영등포구, 관악구, 강서구, 구로구, 양천구, 마포구, 종로구, 중구",
                        1),
                RegionDispatchClassification.of("경기동부",
                        "광주, 하남, 이천, 여주, 양평, 가평", 2),
                RegionDispatchClassification.of("경기남부",
                        "수원, 성남, 용인, 화성, 오산, 평택, 안성", 3),
                RegionDispatchClassification.of("경기서부",
                        "부천, 광명, 시흥, 안산, 군포, 의왕, 과천, 김포, 안양", 4),
                RegionDispatchClassification.of("경기북부",
                        "의정부, 동두천, 고양, 구리, 남양주, 파주, 양주, 포천, 연천", 5),
                RegionDispatchClassification.of("인천광역시",
                        "중구, 동구, 미추홀구, 연수구, 남동구, 부평구, 계양구, 서구, 강화, 옹진", 6),
                RegionDispatchClassification.of("대구광역시",
                        "중구, 동구, 서구, 남구, 북구, 수성구, 달서구, 달성", 7),
                RegionDispatchClassification.of("부산광역시",
                        "중구, 서구, 동구, 영도구, 부산진구, 해운대구, 사하구", 8),
                RegionDispatchClassification.of("제주특별자치도",
                        "제주, 서귀포", 9)));
    }

    @Test
    @DisplayName("case 1 — 서울 송파구 → 서울특별시")
    void classifySeoulSongpa() {
        assertThat(classifier.classify("서울 송파구 잠실동")).isEqualTo("서울특별시");
        assertThat(classifier.classify("서울특별시 강남구 역삼동")).isEqualTo("서울특별시");
    }

    @Test
    @DisplayName("case 2 — 수원시 영통구 → 경기남부 (광역 prefix 미존재 fallback)")
    void classifyGyeonggiSouth() {
        assertThat(classifier.classify("수원시 영통구 매탄동")).isEqualTo("경기남부");
        assertThat(classifier.classify("경기 화성시 동탄")).isEqualTo("경기남부");
    }

    @Test
    @DisplayName("case 3 — 인천 남동구 → 인천광역시")
    void classifyIncheon() {
        assertThat(classifier.classify("인천 남동구 구월동")).isEqualTo("인천광역시");
        assertThat(classifier.classify("인천남동구논현동755-1")).isEqualTo("인천광역시");
    }

    @Test
    @DisplayName("case 4 — 대구 수성구 → 대구광역시")
    void classifyDaegu() {
        assertThat(classifier.classify("대구 수성구 범어동")).isEqualTo("대구광역시");
    }

    @Test
    @DisplayName("case 5 — 매칭 안 됨 / null / blank → null")
    void classifyNoMatch() {
        // 일본 주소 — 키워드 미존재
        assertThat(classifier.classify("Tokyo Shibuya")).isNull();
        // null/blank
        assertThat(classifier.classify(null)).isNull();
        assertThat(classifier.classify("")).isNull();
        assertThat(classifier.classify("   ")).isNull();
    }

    /**
     * TM PR #115 회귀 — "중구"는 서울/인천/대구/부산 4 그룹 동시 보유.
     * 광역 prefix 가중치 적용 전 (sort_order 우선) 모두 "서울특별시" 로 잘못 분류됐다.
     */
    @Test
    @DisplayName("case 6 — 모호 \"중구\" 다중 그룹 광역 prefix 가중치 (회귀)")
    void classifyAmbiguousJunggu() {
        // 대구 중구 → 대구광역시 (서울이 sort_order 1 이지만 광역 "대구" 가중치 적용)
        assertThat(classifier.classify("대구 중구 동인동")).isEqualTo("대구광역시");
        assertThat(classifier.classify("대구광역시 중구 봉산동")).isEqualTo("대구광역시");

        // 부산 중구 → 부산광역시
        assertThat(classifier.classify("부산 중구 광복동")).isEqualTo("부산광역시");
        assertThat(classifier.classify("부산광역시 중구 중앙동")).isEqualTo("부산광역시");

        // 인천 중구 → 인천광역시
        assertThat(classifier.classify("인천 중구 신흥동")).isEqualTo("인천광역시");

        // 서울 중구 → 서울특별시 (광역 "서울" 가중치 정상 적용)
        assertThat(classifier.classify("서울 중구 명동")).isEqualTo("서울특별시");
        assertThat(classifier.classify("서울특별시 중구 을지로")).isEqualTo("서울특별시");
    }

    /**
     * TM PR #115 회귀 — 광역 prefix 만 존재하고 시군구 미특정 케이스도 광역 그룹으로 분류.
     */
    @Test
    @DisplayName("case 7 — 광역 prefix 만 존재 (시군구 미특정) → 광역 그룹")
    void classifyMetropolitanPrefixOnly() {
        // "서울 어딘가" 처럼 시군구 미상이지만 광역 prefix 적중
        assertThat(classifier.classify("서울특별시 어딘가")).isEqualTo("서울특별시");
        // "대구 OO" 처럼 키워드 미스이지만 광역 prefix 적중
        assertThat(classifier.classify("대구 어딘가동 999-1")).isEqualTo("대구광역시");
    }
}
