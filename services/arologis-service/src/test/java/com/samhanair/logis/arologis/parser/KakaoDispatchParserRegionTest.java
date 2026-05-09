package com.samhanair.logis.arologis.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.domain.RegionDispatchClassification;
import com.samhanair.logis.arologis.repository.RegionDispatchClassificationRepository;
import com.samhanair.logis.arologis.service.RegionClassifier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KakaoDispatchParser × RegionClassifier 통합 테스트 — Phase 10 W10-1 PR-D Part 2-1.
 *
 * <p>Spring DI 환경 시뮬레이션 — KakaoDispatchParser 가 RegionClassifier 주입 시
 * ParsedStop.regionGroup 이 정상 매칭됨을 검증.
 */
class KakaoDispatchParserRegionTest {

    private static final LocalDate REFERENCE = LocalDate.now();

    private RegionClassifier buildClassifier() {
        RegionDispatchClassificationRepository repo =
                mock(RegionDispatchClassificationRepository.class);
        when(repo.findAllByOrderBySortOrderAscGroupNameAsc()).thenReturn(List.of(
                RegionDispatchClassification.of("서울특별시",
                        "송파구, 강남구, 영등포구, 구로구, 양천구, 마포구", 1),
                RegionDispatchClassification.of("경기동부", "광주, 하남, 이천, 여주, 양평, 가평", 2),
                RegionDispatchClassification.of("경기남부", "수원, 성남, 용인, 화성, 오산, 평택", 3),
                RegionDispatchClassification.of("경기서부",
                        "부천, 광명, 시흥, 안산, 군포, 의왕, 과천, 김포, 안양", 4),
                RegionDispatchClassification.of("인천광역시",
                        "중구, 동구, 미추홀구, 연수구, 남동구, 부평구, 계양구, 서구", 5)));
        return new RegionClassifier(repo);
    }

    @Test
    @DisplayName("parser × classifier — regionGroup 자동 매칭 (인천 남동구 → 인천광역시)")
    void parserSetsRegionGroup() {
        KakaoDispatchParser parser = new KakaoDispatchParser(buildClassifier());

        String kakao = """
                8일착 야상입니다
                1. 상일+초월
                -인천 남동구 구월동(에스엠하나공조-214)아침8시
                -경기 부천시(부천공조-88)9시
                -서울 송파구 잠실(잠실시스템-44)9시
                1톤
                """;

        ParsedDispatch parsed = parser.parse(kakao, REFERENCE);
        ParsedDispatch.ParsedVehicle v1 = parsed.vehicles().get(0);
        List<ParsedDispatch.ParsedStop> stops = v1.stops().stream()
                .filter(s -> !s.unparsed())
                .toList();

        assertThat(stops).hasSize(3);
        assertThat(stops.get(0).regionGroup()).isEqualTo("인천광역시");
        assertThat(stops.get(1).regionGroup()).isEqualTo("경기서부");
        assertThat(stops.get(2).regionGroup()).isEqualTo("서울특별시");
    }

    @Test
    @DisplayName("parser 단독 (classifier 미주입) — regionGroup null 기본값")
    void parserWithoutClassifier() {
        KakaoDispatchParser parser = new KakaoDispatchParser();

        String kakao = """
                8일착 야상입니다
                1. -인천 남동구 구월동(에스엠하나공조-214)아침8시
                1톤
                """;

        ParsedDispatch parsed = parser.parse(kakao, REFERENCE);
        ParsedDispatch.ParsedStop stop = parsed.vehicles().get(0).stops().get(0);
        assertThat(stop.regionGroup()).isNull();
    }
}
