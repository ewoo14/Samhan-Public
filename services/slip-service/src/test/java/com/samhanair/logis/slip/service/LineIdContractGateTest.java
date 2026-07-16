package com.samhanair.logis.slip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.estimate.web.dto.UpdateEstimateRequest;
import com.samhanair.logis.slip.web.dto.SlipUpdateRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * [D-R8-9] lineId 계약 마커 — <b>게이트 판정</b>과 <b>wire 전제</b>를 고정한다.
 *
 * <p>이 클래스가 잠그는 것은 마커 설계 전체가 딛고 선 <b>단 하나의 전제</b>다:
 * <i>"구 클라이언트는 이 필드를 보내지 않으므로, 부재가 곧 구 클라이언트다."</i>
 * 그 전제는 <b>Jackson 이 필드 부재를 {@code null} 로 넘겨준다</b>는 사실에 의존한다. 만약
 * Jackson 이 부재를 {@code true} 로 코어싱한다면(혹은 누군가 record 성분을 원시 {@code boolean}
 * 으로 바꿔 부재를 {@code false} 로 만들고 게이트의 극성을 뒤집는다면) 게이트는 조용히
 * 무력화되고 R8-QA-1 파괴 경로가 되살아난다. 서비스 층 테스트는 Java 객체를 직접 만들므로
 * 그 전제를 <b>검증하지 않는다</b> — 여기서만 잡힌다.
 */
class LineIdContractGateTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // ---------------------------------------------------------------- 게이트 판정

    @Test
    void rejectsAbsentMarker() {
        assertThatThrownBy(() -> LineIdContractGate.require(null))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void rejectsExplicitlyFalseMarker() {
        assertThatThrownBy(() -> LineIdContractGate.require(false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptsOnlyTrue() {
        assertThatCode(() -> LineIdContractGate.require(true)).doesNotThrowAnyException();
    }

    /**
     * 400 사유는 사용자가 <b>조치할 수 있는</b> 한국어여야 한다 — 원인 · 결과 · 조치.
     *
     * <p>상수 자기참조({@code contains(REJECTION_MESSAGE)})는 동어반복이라 문구가 기술 메시지로
     * 퇴화해도 green 이다. 사용자가 읽어야 할 낱말을 직접 단언한다.
     */
    @Test
    void rejectionMessageStatesCauseAndAction() {
        assertThatThrownBy(() -> LineIdContractGate.require(null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getMessage())
                            .as("원인 — 왜 거부됐는가")
                            .contains("구버전");
                    assertThat(ex.getMessage())
                            .as("결과 — 무엇을 잃을 뻔했는가")
                            .contains("세트 구성품");
                    assertThat(ex.getMessage())
                            .as("조치 — 사용자가 무엇을 해야 하는가")
                            .contains("앱을 업데이트");
                });
    }

    // -------------------------------------------------- [D-R8-13] 마커 vs 라인 내용 대조

    /** 🔴 부분 파괴 — 구성품 2개 중 하나를 익명 라인으로 재생성하면 400 이다. */
    @Test
    void requireLineIdsForLineage_oneComponentMissingAndAnonymousLinePresent_rejects() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThatThrownBy(() -> LineIdContractGate.requireLineIdsForLineage(
                Set.of(first, second), Arrays.asList(first, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(ex.getMessage())
                            .contains("세트 구성품의 기존 라인 정보")
                            .doesNotContain("일부 세트 구성품");
                });
    }

    /** 🔴 전량 파괴 — 모든 구성품을 익명 라인으로 재생성하면 400 이다. */
    @Test
    void requireLineIdsForLineage_allComponentsMissingAndAnonymousLinesPresent_rejects() {
        assertThatThrownBy(() -> LineIdContractGate.requireLineIdsForLineage(
                Set.of(UUID.randomUUID(), UUID.randomUUID()),
                Arrays.asList(null, null)))
                .isInstanceOf(BusinessException.class);
    }

    /** 🔴 오탐 방지 — 계보 없는 평면 문서의 익명 라인 전교체는 정상 허용. */
    @Test
    void requireLineIdsForLineage_plainDocFullReplacement_isAccepted() {
        assertThatCode(() -> LineIdContractGate.requireLineIdsForLineage(
                Set.of(), Arrays.asList(null, null)))
                .doesNotThrowAnyException();
    }

    /** 🔴 오탐 방지 — 모든 구성품 ID를 유지하면 신규 익명 라인을 함께 추가해도 허용. */
    @Test
    void requireLineIdsForLineage_allComponentsRetainedWithNewLine_isAccepted() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThatCode(() -> LineIdContractGate.requireLineIdsForLineage(
                Set.of(first, second), Arrays.asList(first, second, null)))
                .doesNotThrowAnyException();
    }

    /** 🔴 오탐 방지 — 빠진 구성품이 있고 익명 라인이 없으면 그 빠짐은 명시 삭제다. */
    @Test
    void requireLineIdsForLineage_missingComponentWithoutAnonymousLine_isExplicitDeletion() {
        UUID kept = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();

        assertThatCode(() -> LineIdContractGate.requireLineIdsForLineage(
                Set.of(kept, deleted), List.of(kept)))
                .doesNotThrowAnyException();
    }

    /** 🔴 오탐 방지 — 빈 요청은 모호한 재생성이 아니라 모든 라인의 명시 전체삭제다. */
    @Test
    void requireLineIdsForLineage_emptyRequest_isExplicitFullDeletion() {
        assertThatCode(() -> LineIdContractGate.requireLineIdsForLineage(
                Set.of(UUID.randomUUID(), UUID.randomUUID()), List.of()))
                .doesNotThrowAnyException();
    }

    /**
     * 거부 사유는 {@link #rejectionMessageStatesCauseAndAction} 의 마커 거부와 <b>다른</b> 조치를
     * 안내한다 — "앱 업데이트" 가 아니라 <b>화면 새로고침</b>이며 "세트 구성품" 을 포함한다.
     */
    @Test
    void requireLineIdsForLineage_rejectionMessageStatesRefreshAction() {
        assertThatThrownBy(() -> LineIdContractGate.requireLineIdsForLineage(
                Set.of(UUID.randomUUID()), Arrays.asList((UUID) null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getMessage())
                            .as("결과 — 무엇을 잃을 뻔했는가")
                            .contains("세트 구성품");
                    assertThat(ex.getMessage())
                            .as("조치 — 앱 업데이트가 아니라 화면 새로고침")
                            .contains("새로고침");
                    assertThat(ex.getMessage())
                            .as("전무·일부 누락 모두에 정확한 공통 문구")
                            .contains("세트 구성품의 기존 라인 정보")
                            .doesNotContain("일부 세트 구성품");
                });
    }

    // ---------------------------------------------------------------- wire 전제 (Jackson)

    /**
     * 🔴 <b>설계 전제</b> — 구 클라이언트가 보내는 payload(마커 필드 자체가 없음)를 역직렬화하면
     * {@code lineIdContract} 가 {@code null} 이어야 한다.
     *
     * <p>Jackson 은 record 를 canonical 생성자로 역직렬화하며 <b>부재 성분에 {@code null} 을
     * 넘긴다</b>(래퍼 타입 기준). 이 테스트가 red 로 바뀌는 순간 = 부재가 더 이상 거부로 수렴하지
     * 않는 순간이며, 그때 게이트는 통과하는데 계보는 파괴된다.
     */
    @Test
    void slipUpdateRequest_withoutMarkerField_deserializesToNullMarker() throws Exception {
        String staleClientPayload = """
                {
                  "updatedAt": "2026-07-16T09:00:00",
                  "partnerName": "구 클라이언트 거래처",
                  "lines": [
                    {
                      "productId": "aaaaaaaa-0000-0000-0000-000000000001",
                      "productName": "실내기",
                      "quantity": 1,
                      "unitPrice": 330000
                    }
                  ]
                }
                """;

        SlipUpdateRequest request = objectMapper.readValue(staleClientPayload, SlipUpdateRequest.class);

        assertThat(request.lineIdContract()).isNull();
        // 부재가 게이트에 도달하면 거부여야 완결된다 — 전제와 판정을 한 테스트에서 잇는다.
        assertThatThrownBy(() -> LineIdContractGate.require(request.lineIdContract()))
                .isInstanceOf(BusinessException.class);
    }

    /** 견적 미러 — 같은 전제가 견적 계약에도 성립해야 한다 (전표/견적 비대칭 가드). */
    @Test
    void updateEstimateRequest_withoutMarkerField_deserializesToNullMarker() throws Exception {
        String staleClientPayload = """
                {
                  "partnerName": "구 클라이언트 거래처",
                  "lines": [
                    {
                      "productId": "aaaaaaaa-0000-0000-0000-000000000001",
                      "productName": "실내기",
                      "quantity": 1,
                      "unitPrice": 330000
                    }
                  ]
                }
                """;

        UpdateEstimateRequest request =
                objectMapper.readValue(staleClientPayload, UpdateEstimateRequest.class);

        assertThat(request.lineIdContract()).isNull();
        assertThatThrownBy(() -> LineIdContractGate.require(request.lineIdContract()))
                .isInstanceOf(BusinessException.class);
    }

    /** 신 클라이언트 payload — 마커를 실으면 그대로 {@code true} 로 도착한다. */
    @Test
    void slipUpdateRequest_withMarkerField_deserializesToTrue() throws Exception {
        String currentClientPayload = """
                {
                  "updatedAt": "2026-07-16T09:00:00",
                  "lineIdContract": true,
                  "lines": [
                    {
                      "productId": "aaaaaaaa-0000-0000-0000-000000000001",
                      "quantity": 1,
                      "unitPrice": 330000,
                      "lineId": "bbbbbbbb-0000-0000-0000-000000000001"
                    }
                  ]
                }
                """;

        SlipUpdateRequest request =
                objectMapper.readValue(currentClientPayload, SlipUpdateRequest.class);

        assertThat(request.lineIdContract()).isTrue();
        assertThatCode(() -> LineIdContractGate.require(request.lineIdContract()))
                .doesNotThrowAnyException();
    }

    /**
     * 명시적 {@code null} 도 부재와 <b>같은</b> 값으로 도착한다 — 그래서 Jackson 이 둘을 구분하지
     * 못하는 것이 이 설계에서 무해하다. 둘 다 거부로 수렴하므로 구분할 필요 자체가 없다.
     */
    @Test
    void explicitNullMarker_isIndistinguishableFromAbsent_andBothReject() throws Exception {
        String explicitNull = """
                {
                  "updatedAt": "2026-07-16T09:00:00",
                  "lineIdContract": null,
                  "lines": [
                    {
                      "productId": "aaaaaaaa-0000-0000-0000-000000000001",
                      "quantity": 1,
                      "unitPrice": 330000
                    }
                  ]
                }
                """;

        SlipUpdateRequest request = objectMapper.readValue(explicitNull, SlipUpdateRequest.class);

        assertThat(request.lineIdContract()).isNull();
        assertThatThrownBy(() -> LineIdContractGate.require(request.lineIdContract()))
                .isInstanceOf(BusinessException.class);
    }
}
