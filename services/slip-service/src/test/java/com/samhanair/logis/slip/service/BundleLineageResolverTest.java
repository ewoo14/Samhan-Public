package com.samhanair.logis.slip.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.estimate.domain.EstimateLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * BundleLineageResolver 2-패스 전역 매칭 단위 테스트 (R6-H1).
 *
 * <p>라이브 CONFIRMED 오귀속 2변형(신규 라인의 head 탈취 / 단품의 head 오귀속)을 도메인
 * 레벨에서 그대로 재현하고, per-line greedy 에서 불가능했던 요청 순서 비의존성·head exact
 * 전용·빈 계보 tie-break 를 고정한다.
 */
class BundleLineageResolverTest {

    private static final UUID PRODUCT_A = UUID.randomUUID();
    private static final UUID PRODUCT_B = UUID.randomUUID();
    private static final String SET_MODEL = "QA797-SET-01";

    /**
     * [BE 변형 재현] 신규 A qty3 라인이 요청 맨 앞에 와도 head 를 탈취하지 못하고,
     * 무수정 재전송된 진짜 구성품 A/B 가 계보를 보존해야 한다.
     */
    @Test
    void newLineFirst_doesNotStealHeadFromUnchangedComponents() {
        SlipLine headComponent = slipLine(PRODUCT_A, "구성품A", 2, "80000");
        headComponent.assignBundleComponent(SET_MODEL, true);
        SlipLine component = slipLine(PRODUCT_B, "구성품B", 1, "50000");
        component.assignBundleComponent(SET_MODEL, false);
        BundleLineageResolver resolver =
                BundleLineageResolver.fromSlipLines(List.of(headComponent, component));

        SlipLine newLine = slipLine(PRODUCT_A, "구성품A", 3, "123000");
        SlipLine resentHead = slipLine(PRODUCT_A, "구성품A", 2, "80000");
        SlipLine resentComponent = slipLine(PRODUCT_B, "구성품B", 1, "50000");
        resolver.restoreSlipLines(List.of(newLine, resentHead, resentComponent));

        assertThat(newLine.isSetHead()).isFalse();
        assertThat(newLine.getParentSetModel()).isNull();
        assertThat(resentHead.isSetHead()).isTrue();
        assertThat(resentHead.getParentSetModel()).isEqualTo(SET_MODEL);
        assertThat(resentComponent.isSetHead()).isFalse();
        assertThat(resentComponent.getParentSetModel()).isEqualTo(SET_MODEL);
    }

    /**
     * [QA 변형 재현] 세트 head 삭제 + 같은 품목 단품 단가 수정(77,000→88,000) 시,
     * 단품이 head 로 오귀속되지 않고 일반 라인으로 남아야 한다 (head 는 exact 전용).
     */
    @Test
    void editedStandalone_afterHeadDeletion_staysPlainInsteadOfInheritingHead() {
        EstimateLine head = estimateLine(PRODUCT_A, "PART-01", 1, "60000");
        head.assignBundleComponent(SET_MODEL, true);
        EstimateLine component = estimateLine(PRODUCT_B, "PART-02", 1, "40000");
        component.assignBundleComponent(SET_MODEL, false);
        EstimateLine standalone = estimateLine(PRODUCT_A, "PART-01", 1, "77000");
        BundleLineageResolver resolver =
                BundleLineageResolver.fromEstimateLines(List.of(head, component, standalone));

        EstimateLine resentComponent = estimateLine(PRODUCT_B, "PART-02", 1, "40000");
        EstimateLine editedStandalone = estimateLine(PRODUCT_A, "PART-01", 1, "88000");
        resolver.restoreEstimateLines(List.of(resentComponent, editedStandalone));

        assertThat(resentComponent.getParentSetModel()).isEqualTo(SET_MODEL);
        assertThat(resentComponent.isSetHead()).isFalse();
        assertThat(editedStandalone.isSetHead()).isFalse();
        assertThat(editedStandalone.getParentSetModel()).isNull();
    }

    /**
     * [전역 매칭 순서 비의존] 수정된 구성품과 신규 라인이 공존하면, 요청 순서와 무관하게
     * fingerprint 거리가 가까운 수정 구성품이 계보를 승계해야 한다 (per-line greedy 는
     * 앞선 라인이 무조건 선소비했다).
     */
    @Test
    void fallbackAssignsClosestLineRegardlessOfRequestOrder() {
        SlipLine component = slipLine(PRODUCT_A, "구성품A", 2, "80000");
        component.assignBundleComponent(SET_MODEL, false);

        // 신규 라인이 먼저 오는 순서
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(component));
        SlipLine newLine = slipLine(PRODUCT_A, "구성품A", 1, "100000");
        SlipLine editedComponent = slipLine(PRODUCT_A, "구성품A", 3, "80000");
        resolver.restoreSlipLines(List.of(newLine, editedComponent));
        assertThat(newLine.getParentSetModel()).isNull();
        assertThat(editedComponent.getParentSetModel()).isEqualTo(SET_MODEL);

        // 반대 순서에서도 동일 결과
        BundleLineageResolver reversed = BundleLineageResolver.fromSlipLines(List.of(component));
        SlipLine editedFirst = slipLine(PRODUCT_A, "구성품A", 3, "80000");
        SlipLine newSecond = slipLine(PRODUCT_A, "구성품A", 1, "100000");
        reversed.restoreSlipLines(List.of(editedFirst, newSecond));
        assertThat(editedFirst.getParentSetModel()).isEqualTo(SET_MODEL);
        assertThat(newSecond.getParentSetModel()).isNull();
    }

    /** [head exact 전용] 값이 수정된 라인은 head 엔트리를 fallback 으로 승계할 수 없다. */
    @Test
    void headEntry_isNeverConsumedByFallback() {
        SlipLine head = slipLine(PRODUCT_A, "구성품A", 2, "80000");
        head.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(head));

        SlipLine editedLine = slipLine(PRODUCT_A, "구성품A", 5, "80000");
        resolver.restoreSlipLines(List.of(editedLine));

        assertThat(editedLine.isSetHead()).isFalse();
        assertThat(editedLine.getParentSetModel()).isNull();
    }

    /**
     * [빈 계보 tie-break] 거리 동률(동일 fingerprint 의 구성품/단품 엔트리 공존)이면
     * 빈 계보 엔트리를 우선 소비해 수정 라인이 구성품으로 오귀속되지 않는다.
     */
    @Test
    void equalDistanceCandidates_preferPlainEntryOverComponent() {
        SlipLine component = slipLine(PRODUCT_A, "품목A", 1, "50000");
        component.assignBundleComponent(SET_MODEL, false);
        SlipLine plain = slipLine(PRODUCT_A, "품목A", 1, "50000");
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(component, plain));

        SlipLine editedLine = slipLine(PRODUCT_A, "품목A", 1, "60000");
        resolver.restoreSlipLines(List.of(editedLine));

        assertThat(editedLine.getParentSetModel()).isNull();
    }

    /** [무수정 보존] 재전송 순서를 뒤섞어도 exact 1-패스가 각 라인의 계보를 정확히 되돌린다. */
    @Test
    void unchangedResend_preservesAllLineagesEvenWhenReordered() {
        SlipLine head = slipLine(PRODUCT_A, "구성품A", 2, "80000");
        head.assignBundleComponent(SET_MODEL, true);
        SlipLine component = slipLine(PRODUCT_B, "구성품B", 1, "50000");
        component.assignBundleComponent(SET_MODEL, false);
        SlipLine plain = slipLine(PRODUCT_A, "품목A단품", 1, "70000");
        BundleLineageResolver resolver =
                BundleLineageResolver.fromSlipLines(List.of(head, component, plain));

        SlipLine resentPlain = slipLine(PRODUCT_A, "품목A단품", 1, "70000");
        SlipLine resentComponent = slipLine(PRODUCT_B, "구성품B", 1, "50000");
        SlipLine resentHead = slipLine(PRODUCT_A, "구성품A", 2, "80000");
        resolver.restoreSlipLines(List.of(resentPlain, resentComponent, resentHead));

        assertThat(resentPlain.getParentSetModel()).isNull();
        assertThat(resentComponent.getParentSetModel()).isEqualTo(SET_MODEL);
        assertThat(resentHead.isSetHead()).isTrue();
        assertThat(resentHead.getParentSetModel()).isEqualTo(SET_MODEL);
    }

    /** [신규 productId] 기존 엔트리에 없는 품목은 어떤 계보도 승계하지 않는다. */
    @Test
    void unknownProduct_neverInheritsLineage() {
        SlipLine head = slipLine(PRODUCT_A, "구성품A", 2, "80000");
        head.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(head));

        SlipLine unknown = slipLine(UUID.randomUUID(), "신규품목", 2, "80000");
        resolver.restoreSlipLines(List.of(unknown));

        assertThat(unknown.isSetHead()).isFalse();
        assertThat(unknown.getParentSetModel()).isNull();
    }

    private static SlipLine slipLine(UUID productId, String name, int quantity, String unitPrice) {
        return SlipLine.create(null, productId, name, name, null,
                quantity, new BigDecimal(unitPrice), null);
    }

    private static EstimateLine estimateLine(UUID productId, String name, int quantity, String unitPrice) {
        return EstimateLine.create(null, 1, productId, name, name, null,
                quantity, new BigDecimal(unitPrice), null);
    }
}
