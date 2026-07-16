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
 *
 * <p><b>R8-QA-8 회고 — 이 클래스가 결함을 정답으로 못박고 있었다</b>: 종전
 * {@code nullLineId_isAlwaysTreatedAsNewPlainLine()} 은 "계보 보유 문서를 lineId 없이 저장하면
 * 계보가 날아간다" 를 <b>바람직한 계약</b>으로 단언했고, 그래서 R8-QA-1 이 라이브에서 실증한
 * 데이터 손실에도 BE 스위트가 green 이었다. 또한 7건 전부 <b>올바른 lineId</b> 를 전제해
 * 품목 교체(R8-QA-6)·밀린 lineId(R8-QA-2) 반례 커버가 0 이었다.
 *
 * <p>현재 계약은 두 층으로 나뉜다 —
 * <ul>
 *   <li><b>resolver 층(이 클래스)</b>: {@code lineId == null} = 신규 라인 → 계보 미승계.
 *       이것 자체는 정상이며 <b>유지</b>한다(편집 중 행 추가). fingerprint 폴백으로 되돌아가면
 *       R1~R7 이 반증한 결함이 재발한다.</li>
 *   <li><b>문서 층(서비스)</b>: 계보 보유 문서인데 <b>전 라인</b>이 lineId 미전송이면 400 거부
 *       (D-R8-6). 데이터 손실 방어는 이 층의 책임이며
 *       {@code SlipUpdateLineIdContractTest} / {@code PartnerProductPriceMemoryIT} 가 가드한다.</li>
 * </ul>
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

    /**
     * [R8-QA-8 재작성] 종전 이름은 {@code nullLineId_isAlwaysTreatedAsNewPlainLine} 이었고, 같은
     * 품목의 기존 세트 구성품이 존재해도 lineId 없는 라인은 평면으로 남는다는 <b>"always"</b> 를
     * 단언했다. 단언 자체(신규 라인 → 계보 미승계)는 옳지만 그 이름과 프레이밍이 "계보 보유 문서를
     * lineId 없이 통째 PUT 해도 정상" 이라는 <b>데이터 손실 면허</b>로 읽혔다.
     *
     * <p>여기서 검증하는 것은 <b>진짜 신규 라인</b>이다 — 편집 중 사용자가 추가한 행은 기존 구성품과
     * 품목·수량·단가가 우연히 같아도 남의 계보를 상속해서는 안 된다(fingerprint 추정 금지).
     * 계보 보유 문서의 "전 라인 미전송" 차단은 문서 층의 책임이며 이 테스트의 관심사가 아니다.
     */
    @Test
    void newLineWithoutLineId_doesNotInheritLookalikeLineage() {
        UUID persistedId = UUID.randomUUID();
        SlipLine persisted = slipLine(persistedId, PRODUCT_A, "품목A", 1, "50000");
        persisted.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(persisted));

        // 기존 구성품과 품목/수량/단가가 완전히 동일한 신규 행 — fingerprint 로는 구분 불가
        SlipLine newLine = slipLine(null, PRODUCT_A, "품목A", 1, "50000");
        resolver.restoreSlipLines(List.of(newLine), Arrays.asList((UUID) null));

        assertThat(newLine.isSetHead()).isFalse();
        assertThat(newLine.getParentSetModel()).isNull();
    }

    /**
     * [R8-BE-1 / R8-QA-6 반례 — 현재 fix 이전 코드에서는 FAIL 해야 정상]
     *
     * <p>개발책임자 도메인 확정(D-R8-8): <i>"세트 구성품의 정체성은 품목에 묶여 있다. 품목을
     * 교체하면 그 라인은 더 이상 그 세트의 구성품이 아니다."</i>
     *
     * <p>라이브 실증: 세트 head 라인의 품목을 무관한 단품으로 교체하고 단가를 바꿔 저장 → 200 →
     * 그 단품이 {@code ACD-2558G:t:AF17B6474GZS} 로 <b>거짓 세트 head</b> 각인 + 구성품으로 오판되어
     * 사용자가 입력한 단가가 가격기억에서 <b>통째 누락</b>(기억행 NONE).
     */
    @Test
    void lineIdWithDifferentProduct_mustNotInheritLineage() {
        UUID headId = UUID.randomUUID();
        SlipLine persistedHead = slipLine(headId, PRODUCT_A, "구성품A", 1, "80000");
        persistedHead.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(persistedHead));

        // 같은 lineId 를 유지한 채 품목만 무관한 단품으로 교체 — 계보를 상속하면 안 된다
        SlipLine swapped = slipLine(null, PRODUCT_B, "무관한 단품", 1, "150000");
        resolver.restoreSlipLines(List.of(swapped), List.of(headId));

        assertThat(swapped.isSetHead()).isFalse();
        assertThat(swapped.getParentSetModel()).isNull();
        assertThat(BundleLineageResolver.isBundleComponent(swapped)).isFalse();
    }

    /** [R8-BE-1 견적 미러] 견적도 같은 품목 동일성 게이트를 따른다 (slip/estimate 비대칭 차단). */
    @Test
    void estimateLineIdWithDifferentProduct_mustNotInheritLineage() {
        UUID lineId = UUID.randomUUID();
        EstimateLine persisted = estimateLine(lineId, PRODUCT_A, "구성품A", 1, "60000");
        persisted.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromEstimateLines(List.of(persisted));

        EstimateLine swapped = estimateLine(null, PRODUCT_B, "무관한 단품", 1, "150000");
        resolver.restoreEstimateLines(List.of(swapped), List.of(lineId));

        assertThat(swapped.isSetHead()).isFalse();
        assertThat(swapped.getParentSetModel()).isNull();
        assertThat(BundleLineageResolver.isBundleComponent(swapped)).isFalse();
    }

    /**
     * [R8-QA-2 반례 — 밀린 lineId] 2창 coedit 에서 원격 삭제를 수신한 창이 <b>위치 복원</b>으로
     * lineId 를 한 칸 밀어 보내는 상황. 밀린 lineId 는 그 문서의 <b>유효한</b> lineId 이므로 소유권
     * 검증(400)을 통과한다 — 품목 동일성 게이트가 마지막 방어선이다.
     *
     * <p>구성품 B 의 데이터에 head(품목 A) 의 lineId 가 붙어 오면 품목이 달라 승계가 거부되고,
     * B 는 <b>거짓 head</b> 로 각인되지 않는다.
     *
     * <p>⚠️ <b>정직 고지</b>: 밀린 lineId 가 <b>같은 품목</b> 라인을 가리키는 경우는 이 게이트로
     * 막을 수 없다(서버가 구분할 정보가 없다). 근본 fix 는 FE 의 Y.Doc lineId 직독(R8-FE-1)이며
     * 이 게이트는 심층방어다 — 서로 대체하지 않는다.
     */
    @Test
    void shiftedLineIdFromRemoteDelete_doesNotImprintFalseHeadOnDifferentProduct() {
        UUID headId = UUID.randomUUID();
        UUID componentId = UUID.randomUUID();
        SlipLine head = slipLine(headId, PRODUCT_A, "실내기", 1, "330000");
        head.assignBundleComponent(SET_MODEL, true);
        SlipLine component = slipLine(componentId, PRODUCT_B, "실외기", 1, "220000");
        component.assignBundleComponent(SET_MODEL, false);
        BundleLineageResolver resolver =
                BundleLineageResolver.fromSlipLines(List.of(head, component));

        // 원격에서 head 가 삭제돼 남은 1행(실외기)이 위치 복원으로 head 의 lineId 를 물고 온다
        SlipLine survivor = slipLine(null, PRODUCT_B, "실외기", 1, "299000");
        resolver.restoreSlipLines(List.of(survivor), List.of(headId));

        assertThat(survivor.isSetHead()).isFalse();
        assertThat(survivor.getParentSetModel()).isNull();
    }

    /** 품목이 <b>그대로</b>면 밀림이 아니라 정상 수정이므로 계보를 유지한다 (게이트 오탐 방지). */
    @Test
    void sameProductLineId_stillInheritsLineageAfterProductGate() {
        UUID headId = UUID.randomUUID();
        SlipLine persistedHead = slipLine(headId, PRODUCT_A, "구성품A", 2, "80000");
        persistedHead.assignBundleComponent(SET_MODEL, true);
        BundleLineageResolver resolver = BundleLineageResolver.fromSlipLines(List.of(persistedHead));

        // 품목 동일 + 단가만 수정 — 품목 게이트가 정상 수정을 막으면 안 된다
        SlipLine repriced = slipLine(null, PRODUCT_A, "구성품A", 2, "95000");
        resolver.restoreSlipLines(List.of(repriced), List.of(headId));

        assertThat(repriced.isSetHead()).isTrue();
        assertThat(repriced.getParentSetModel()).isEqualTo(SET_MODEL);
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
