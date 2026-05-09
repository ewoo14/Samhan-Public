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
 * <p>주소 → 그룹명 매칭 정확성 검증. 5 case.
 * <ul>
 *   <li>case 1 — "서울 송파구..." → "서울특별시"</li>
 *   <li>case 2 — "수원시 영통구..." → "경기남부"</li>
 *   <li>case 3 — "인천 남동구..." → "인천광역시"</li>
 *   <li>case 4 — "대구 수성구..." → "대구광역시"</li>
 *   <li>case 5 — 매칭 안 됨 → null (외국 주소 / blank)</li>
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
    @DisplayName("case 2 — 수원시 영통구 → 경기남부")
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
}
