package com.samhanair.logis.slip.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.estimate.domain.EstimateLine;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * lineId 기반 BundleLineageResolver 계약 테스트.
 *
 * <p>수정된 값은 fingerprint 로 추정하지 않고 요청 lineId 로 기존 영속 라인과 직접 연결한다.
 * lineId 가 없는 라인은 신규 라인이므로 계보를 승계하지 않는다. UUID 는 payload 전용이며
 * 이 테스트도 화면 표시가 아닌 내부 요청 매핑만 검증한다.
 */
class BundleLineageResolverTest {

    private static final UUID PRODUCT_A = UUID.randomUUID();
    private static final UUID PRODUCT_B = UUID.randomUUID();
    private static final String SET_MODEL = "QA809-SET-01";

    @Test
    void modifiedSetHead_quantityOnly_stillPreservesLineageByLineId() {
        UUID headId = UUID.randomUUID();
        SlipLine persistedHead = slipLine(headId, PRODUCT_A, "구성품A", 2, "80000");
        persistedHead.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(persistedHead));

        SlipLine editedHead = slipLine(null, PRODUCT_A, "구성품A", 3, "80000");
        resolver.restoreSlipLines(List.of(editedHead), List.of(headId));

        assertThat(editedHead.isSetHead()).isTrue();
        assertThat(editedHead.getParentSetModel()).isEqualTo(SET_MODEL);
    }

    @Test
    void sameProductComponentAndPlainLine_keepTheirOwnLineageRegardlessOfRequestOrder() {
        UUID componentId = UUID.randomUUID();
        UUID plainId = UUID.randomUUID();
        SlipLine component = slipLine(componentId, PRODUCT_A, "같은품목", 1, "80000");
        component.assignBundleComponent(SET_MODEL, false);
        SlipLine plain = slipLine(plainId, PRODUCT_A, "같은품목", 3, "120000");
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(component, plain));

        SlipLine editedPlain = slipLine(null, PRODUCT_A, "같은품목", 4, "120000");
        SlipLine editedComponent = slipLine(null, PRODUCT_A, "같은품목", 2, "80000");
        resolver.restoreSlipLines(List.of(editedPlain, editedComponent), List.of(plainId, componentId));

        assertThat(editedPlain.getParentSetModel()).isNull();
        assertThat(editedComponent.getParentSetModel()).isEqualTo(SET_MODEL);
    }

    @Test
    void swappingRequestOrder_doesNotChangeLineageAssignment() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        SlipLine first = slipLine(firstId, PRODUCT_A, "A", 1, "10000");
        first.assignBundleComponent(SET_MODEL, true);
        SlipLine second = slipLine(secondId, PRODUCT_B, "B", 1, "20000");
        second.assignBundleComponent(SET_MODEL, false);

        SlipLine firstEdited = slipLine(null, PRODUCT_A, "A", 9, "99999");
        SlipLine secondEdited = slipLine(null, PRODUCT_B, "B", 8, "88888");
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(first, second));
        resolver.restoreSlipLines(List.of(secondEdited, firstEdited), List.of(secondId, firstId));

        assertThat(firstEdited.isSetHead()).isTrue();
        assertThat(secondEdited.isSetHead()).isFalse();
        assertThat(secondEdited.getParentSetModel()).isEqualTo(SET_MODEL);
    }

    @Test
    void nullLineId_isAlwaysTreatedAsNewPlainLine() {
        UUID persistedId = UUID.randomUUID();
        SlipLine persisted = slipLine(persistedId, PRODUCT_A, "품목A", 1, "50000");
        persisted.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(persisted));

        SlipLine newLine = slipLine(null, PRODUCT_A, "품목A", 1, "50000");
        resolver.restoreSlipLines(List.of(newLine), Arrays.asList((UUID) null));

        assertThat(newLine.isSetHead()).isFalse();
        assertThat(newLine.getParentSetModel()).isNull();
    }

    @Test
    void unknownLineId_doesNotInventLineage() {
        BundleLineageResolver resolver = BundleLineageResolver.empty();
        SlipLine newLine = slipLine(null, PRODUCT_A, "품목A", 1, "50000");

        resolver.restoreSlipLines(List.of(newLine), List.of(UUID.randomUUID()));

        assertThat(newLine.isSetHead()).isFalse();
        assertThat(newLine.getParentSetModel()).isNull();
    }

    @Test
    void estimateLine_usesTheSameLineIdContract() {
        UUID lineId = UUID.randomUUID();
        EstimateLine persisted = estimateLine(lineId, PRODUCT_A, "구성품A", 2, "60000");
        persisted.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromEstimateLines(List.of(persisted));

        EstimateLine edited = estimateLine(null, PRODUCT_A, "구성품A", 3, "60000");
        resolver.restoreEstimateLines(List.of(edited), List.of(lineId));

        assertThat(edited.isSetHead()).isTrue();
        assertThat(edited.getParentSetModel()).isEqualTo(SET_MODEL);
    }

    @Test
    void emptyResolver_keepsAllLinesFlat() {
        SlipLine first = slipLine(null, PRODUCT_A, "A", 1, "10000");
        SlipLine second = slipLine(null, PRODUCT_B, "B", 1, "20000");

        BundleLineageResolver.empty().restoreSlipLines(List.of(first, second),
                Arrays.asList(null, null));

        assertThat(first.getParentSetModel()).isNull();
        assertThat(second.getParentSetModel()).isNull();
    }

    private static SlipLine slipLine(UUID id, UUID productId, String name, int quantity, String unitPrice) {
        SlipLine line = SlipLine.create(null, productId, name, name, null,
                quantity, new BigDecimal(unitPrice), null);
        if (id != null) {
            ReflectionTestUtils.setField(line, "id", id);
        }
        return line;
    }

    private static EstimateLine estimateLine(UUID id, UUID productId, String name,
                                             int quantity, String unitPrice) {
        EstimateLine line = EstimateLine.create(null, 1, productId, name, name, null,
                quantity, new BigDecimal(unitPrice), null);
        if (id != null) {
            ReflectionTestUtils.setField(line, "id", id);
        }
        return line;
    }
}
