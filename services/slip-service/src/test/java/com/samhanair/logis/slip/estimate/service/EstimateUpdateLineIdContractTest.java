package com.samhanair.logis.slip.estimate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.shared.realtime.collection.CollectionRealtimePublisher;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.estimate.domain.Estimate;
import com.samhanair.logis.slip.estimate.repository.EstimateRepository;
import com.samhanair.logis.slip.estimate.revision.service.EstimateRevisionService;
import com.samhanair.logis.slip.estimate.web.dto.UpdateEstimateRequest;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [D-R8-9] 견적 수정의 lineId 계약 마커 게이트 — 전표 미러
 * ({@code SlipUpdateLineIdContractTest}) 의 견적측.
 *
 * <p><b>왜 견적을 따로 잠그나</b>: 이 PR 은 전표/견적 <b>비대칭</b>을 8라운드째 반복 적발했고,
 * 종전 견적 미러는 실제로 {@code requestedLines.isEmpty()} 면 게이트를 면제해 전표와 이미
 * 어긋나 있었다. 판정을 공용 {@code LineIdContractGate} 로 좁혔으므로 판정 자체는 드리프트할 수
 * 없지만, <b>게이트를 어디서 부르는지</b>는 서비스마다 다르므로 여기서 별도로 고정한다.
 *
 * <p>🔴 <b>견적 고유의 함정</b>: {@code EstimateService.update} 는 {@code validateLineIds} 보다
 * <b>먼저</b> {@code editHeader} 로 헤더를 바꾸고, {@code lines == null} 이면 라인 검증을 아예
 * 호출하지 않는다. 게이트를 라인 검증 안에 두면 (a) 구 클라이언트의 헤더 변경이 이미 적용된 뒤
 * 거부되거나 (b) 헤더 전용 수정이 게이트를 통째로 우회한다. 아래 두 테스트가 그 둘을 막는다.
 */
class EstimateUpdateLineIdContractTest {

    private static final UUID PARTNER_ID = UUID.randomUUID();
    private static final UUID NEW_PARTNER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private final EstimateRepository estimateRepository = mock(EstimateRepository.class);
    private final EstimateNumberService estimateNumberService = mock(EstimateNumberService.class);
    private final ProductClient productClient = mock(ProductClient.class);
    private final EstimateToSlipConverter slipConverter = mock(EstimateToSlipConverter.class);
    private final EstimateRevisionService estimateRevisionService =
            mock(EstimateRevisionService.class);
    private final CollectionRealtimePublisher collectionRealtimePublisher =
            mock(CollectionRealtimePublisher.class);
    private final PartnerProductPriceMemoryService priceMemoryService =
            mock(PartnerProductPriceMemoryService.class);

    private final EstimateService service = new EstimateService(
            estimateRepository, estimateNumberService, productClient, slipConverter,
            estimateRevisionService, collectionRealtimePublisher, priceMemoryService);

    @Test
    void update_withoutContractMarker_isRejectedAsBadRequest() {
        Estimate estimate = persistedEstimate();
        when(estimateRepository.findById(estimate.getId())).thenReturn(Optional.of(estimate));

        assertThatThrownBy(() -> service.update(estimate.getId(),
                staleClientRequest(), "user-old", "구 클라이언트"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    // 전표 미러와 <b>같은</b> 사유 문구여야 한다 — 드리프트 가드.
                    assertThat(ex.getMessage()).contains("구버전", "앱을 업데이트");
                });
    }

    /**
     * 🔴 거부는 <b>헤더 갱신보다 먼저</b>여야 한다 — 부분 적용 금지.
     *
     * <p>게이트가 {@code validateLineIds} 안에 있으면 이 시점에 이미 {@code editHeader} 가 돌아
     * 거래처가 바뀐 채로 400 이 난다. 트랜잭션 롤백이 대개 지워주겠지만, "거부된 요청이 상태를
     * 건드렸다"는 사실 자체가 계약 위반이며 롤백에 의존하는 방어는 방어가 아니다.
     */
    @Test
    void update_rejection_happensBeforeHeaderMutationAndAnySideEffect() {
        Estimate estimate = persistedEstimate();
        when(estimateRepository.findById(estimate.getId())).thenReturn(Optional.of(estimate));

        assertThatThrownBy(() -> service.update(estimate.getId(),
                staleClientRequest(), "user-old", "구 클라이언트"))
                .isInstanceOf(BusinessException.class);

        // 헤더가 원 거래처 그대로여야 한다 — 바뀌었다면 거부가 늦은 것이다.
        assertThat(estimate.getPartnerId()).isEqualTo(PARTNER_ID);
        assertThat(estimate.getPartnerName()).isEqualTo("원 거래처");
        assertThat(estimate.getLines()).isEmpty();
        verify(priceMemoryService, never()).rememberBatchAfterCommit(any(), any());
        verifyNoInteractions(estimateRevisionService, productClient);
    }

    /**
     * 🔴 <b>헤더 전용 수정</b>({@code lines == null}) 도 마커를 요구한다 — 게이트 우회 금지.
     *
     * <p>구 클라이언트는 {@code partnerId} 도 보내지 않으므로, 헤더만 바꾸는 저장이 통과하면
     * 거래처가 어긋난 채 가격기억이 원 거래처에 각인되는 R8-QA-3 경로가 그대로 열린다.
     * 라인을 건드리지 않는다고 안전한 요청이 아니다.
     */
    @Test
    void update_headerOnly_withoutContractMarker_isAlsoRejected() {
        Estimate estimate = persistedEstimate();
        when(estimateRepository.findById(estimate.getId())).thenReturn(Optional.of(estimate));

        UpdateEstimateRequest headerOnly = new UpdateEstimateRequest(
                NEW_PARTNER_ID, "구 클라이언트가 바꾼 거래처", null, null, null, "헤더만 수정",
                null, null);

        assertThatThrownBy(() -> service.update(estimate.getId(), headerOnly,
                "user-old", "구 클라이언트"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        assertThat(estimate.getPartnerId()).isEqualTo(PARTNER_ID);
        assertThat(estimate.getPartnerName()).isEqualTo("원 거래처");
    }

    @Test
    void update_withExplicitlyFalseMarker_isRejected() {
        Estimate estimate = persistedEstimate();
        when(estimateRepository.findById(estimate.getId())).thenReturn(Optional.of(estimate));

        assertThatThrownBy(() -> service.update(estimate.getId(),
                request(false), "user-old", "계약 거부 클라이언트"))
                .isInstanceOf(BusinessException.class);

        assertThat(estimate.getPartnerName()).isEqualTo("원 거래처");
    }

    // ---------------------------------------------------------------- 픽스처

    private Estimate persistedEstimate() {
        Estimate estimate = Estimate.create("2026/07/16-1", LocalDate.of(2026, 7, 16), 1,
                PARTNER_ID, "원 거래처", null, null, null, null, "user-1");
        ReflectionTestUtils.setField(estimate, "id", UUID.randomUUID());
        return estimate;
    }

    /** 구 클라이언트 재현 — 마커 필드를 모르므로 보내지 않는다(= null). */
    private UpdateEstimateRequest staleClientRequest() {
        return request(null);
    }

    private UpdateEstimateRequest request(Boolean lineIdContract) {
        return new UpdateEstimateRequest(
                NEW_PARTNER_ID, "구 클라이언트가 바꾼 거래처", null, null, null, "수정 메모",
                List.of(new UpdateEstimateRequest.EstimateLineUpdate(
                        PRODUCT_ID, "실내기", "COMP-1", null, 1,
                        new BigDecimal("330000"), null, null, null, null)),
                lineIdContract);
    }
}
