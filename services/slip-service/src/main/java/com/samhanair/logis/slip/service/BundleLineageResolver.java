package com.samhanair.logis.slip.service;

import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.estimate.domain.EstimateLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 전체 교체 편집에서 기존 영속 라인의 세트 계보를 새 라인으로 복원한다.
 *
 * <p>전표/견적 PUT DTO 는 {@code setHead}/{@code parentSetModel} 을 받지 않는다. 따라서
 * 클라이언트가 상세 응답을 그대로 다시 저장해도 일반 라인으로 재생성되는 문제를 막기 위해,
 * replace 전에 서버가 기존 라인의 값 fingerprint 와 세트 계보를 함께 캡처한다. 같은 품목이
 * 단품과 세트 구성품으로 함께 존재할 수 있으므로 일반 라인 자리도 빈 계보로 포함한다.
 *
 * <p>무수정 라인은 전체 fingerprint 로 먼저 매칭하고, 수량/단가 등 일부 값이 수정된 라인은
 * 같은 productId 의 아직 소비되지 않은 기존 라인으로 fallback 한다. 신규 productId 는 계보를
 * 상속하지 않는다. DTO 에 영속 lineId 가 없는 현 계약에서 클라이언트를 신뢰하지 않고 무수정
 * 저장을 보존할 수 있는 가장 좁은 서버 권위 규칙이다.
 */
public final class BundleLineageResolver {

    private final List<LineageEntry> entries = new ArrayList<>();

    private BundleLineageResolver() {
    }

    /** 빈 계보 복원기. 신규 생성 경로에서 사용한다. */
    public static BundleLineageResolver empty() {
        return new BundleLineageResolver();
    }

    /** 기존 전표 라인의 세트 계보를 캡처한다. */
    public static BundleLineageResolver fromSlipLines(List<SlipLine> lines) {
        BundleLineageResolver resolver = new BundleLineageResolver();
        if (lines != null) {
            lines.forEach(line -> resolver.capture(
                    fingerprint(line), line.getParentSetModel(), line.isSetHead()));
        }
        return resolver;
    }

    /** 기존 견적 라인의 세트 계보를 캡처한다. */
    public static BundleLineageResolver fromEstimateLines(List<EstimateLine> lines) {
        BundleLineageResolver resolver = new BundleLineageResolver();
        if (lines != null) {
            lines.forEach(line -> resolver.capture(
                    fingerprint(line), line.getParentSetModel(), line.isSetHead()));
        }
        return resolver;
    }

    /** 같은 productId 출현 순서의 기존 계보를 새 전표 라인에 복원한다. */
    public SlipLine restore(SlipLine line) {
        BundleLineage lineage = next(fingerprint(line));
        if (lineage.isBundleComponent()) {
            line.assignBundleComponent(lineage.parentSetModel(), lineage.setHead());
        }
        return line;
    }

    /** 같은 productId 출현 순서의 기존 계보를 새 견적 라인에 복원한다. */
    public EstimateLine restore(EstimateLine line) {
        BundleLineage lineage = next(fingerprint(line));
        if (lineage.isBundleComponent()) {
            line.assignBundleComponent(lineage.parentSetModel(), lineage.setHead());
        }
        return line;
    }

    /** 세트 구성품 여부. parent model 이 서버 영속 계보의 권위값이다. */
    public static boolean isBundleComponent(SlipLine line) {
        return line != null && line.getParentSetModel() != null
                && !line.getParentSetModel().isBlank();
    }

    /** 세트 구성품 여부. parent model 이 서버 영속 계보의 권위값이다. */
    public static boolean isBundleComponent(EstimateLine line) {
        return line != null && line.getParentSetModel() != null
                && !line.getParentSetModel().isBlank();
    }

    private void capture(LineFingerprint fingerprint, String parentSetModel, boolean setHead) {
        if (fingerprint.productId() == null) {
            return;
        }
        entries.add(new LineageEntry(fingerprint, new BundleLineage(parentSetModel, setHead)));
    }

    private BundleLineage next(LineFingerprint fingerprint) {
        LineageEntry matched = entries.stream()
                .filter(entry -> !entry.used && entry.fingerprint.equals(fingerprint))
                .findFirst()
                .orElseGet(() -> entries.stream()
                        .filter(entry -> !entry.used
                                && Objects.equals(entry.fingerprint.productId(), fingerprint.productId()))
                        .findFirst()
                        .orElse(null));
        if (matched == null) {
            return BundleLineage.NONE;
        }
        matched.used = true;
        return matched.lineage;
    }

    private static LineFingerprint fingerprint(SlipLine line) {
        return new LineFingerprint(
                line.getProductId(), line.getProductName(), line.getModelName(), line.getSpecification(),
                line.getQuantity(), normalize(line.getUnitPrice()), line.getNote());
    }

    private static LineFingerprint fingerprint(EstimateLine line) {
        return new LineFingerprint(
                line.getProductId(), line.getProductName(), line.getModelName(), line.getSpecification(),
                line.getQuantity(), normalize(line.getUnitPrice()), line.getNote());
    }

    private static String normalize(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private record BundleLineage(String parentSetModel, boolean setHead) {
        private static final BundleLineage NONE = new BundleLineage(null, false);

        private boolean isBundleComponent() {
            return parentSetModel != null && !parentSetModel.isBlank();
        }
    }

    private record LineFingerprint(
            UUID productId,
            String productName,
            String modelName,
            String specification,
            int quantity,
            String unitPrice,
            String note) {
    }

    private static final class LineageEntry {
        private final LineFingerprint fingerprint;
        private final BundleLineage lineage;
        private boolean used;

        private LineageEntry(LineFingerprint fingerprint, BundleLineage lineage) {
            this.fingerprint = fingerprint;
            this.lineage = lineage;
        }
    }
}
