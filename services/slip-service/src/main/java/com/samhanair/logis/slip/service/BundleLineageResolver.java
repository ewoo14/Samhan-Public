package com.samhanair.logis.slip.service;

import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.estimate.domain.EstimateLine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 전체 교체 편집에서 lineId 로 기존 영속 라인의 세트 계보를 결정적으로 승계한다.
 *
 * <p>수정 전 계약은 요청 라인에 영속 ID가 없어 fingerprint·거리·요청 순서 휴리스틱으로
 * 기존 라인을 추정했다. 그 방식은 신규 라인과 수정 라인을 구분할 수 없으므로 제거한다.
 * 현재 계약에서 {@code lineId != null} 인 라인은 해당 문서의 기존 라인으로 검증된 뒤 계보를
 * 승계하고, {@code lineId == null} 인 라인은 신규 평면 라인으로 남긴다.
 */
public final class BundleLineageResolver {

    private final Map<UUID, BundleLineage> lineagesById;

    private BundleLineageResolver(Map<UUID, BundleLineage> lineagesById) {
        this.lineagesById = lineagesById;
    }

    /** 기존 계보가 없는 신규 문서/신규 라인용 resolver. */
    public static BundleLineageResolver empty() {
        return new BundleLineageResolver(Map.of());
    }

    /** 기존 전표 라인의 영속 ID와 세트 계보를 캡처한다. */
    public static BundleLineageResolver fromSlipLines(List<SlipLine> lines) {
        Map<UUID, BundleLineage> lineages = new HashMap<>();
        if (lines != null) {
            for (SlipLine line : lines) {
                if (line != null && line.getId() != null) {
                    lineages.put(line.getId(), new BundleLineage(
                            line.getParentSetModel(), line.isSetHead()));
                }
            }
        }
        return new BundleLineageResolver(lineages);
    }

    /** 기존 견적 라인의 영속 ID와 세트 계보를 캡처한다. */
    public static BundleLineageResolver fromEstimateLines(List<EstimateLine> lines) {
        Map<UUID, BundleLineage> lineages = new HashMap<>();
        if (lines != null) {
            for (EstimateLine line : lines) {
                if (line != null && line.getId() != null) {
                    lineages.put(line.getId(), new BundleLineage(
                            line.getParentSetModel(), line.isSetHead()));
                }
            }
        }
        return new BundleLineageResolver(lineages);
    }

    /**
     * 새 전표 라인에 요청 lineId 순서대로 기존 계보를 승계한다.
     *
     * <p>lineIds 의 null 값은 신규 라인을 뜻한다. 소유권/존재 검증은 문서 서비스가 수행하며,
     * resolver 는 검증된 문서 라인 ID만 계보 map에서 조회한다.
     *
     * @param lines 전체 교체할 새 전표 라인
     * @param lineIds 각 새 라인이 승계할 기존 전표 라인 ID; 신규 라인은 null
     */
    public void restoreSlipLines(List<SlipLine> lines, List<UUID> lineIds) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        requireSameSize(lines.size(), lineIds);
        for (int i = 0; i < lines.size(); i++) {
            assign(lines.get(i), lineIds.get(i));
        }
    }

    /**
     * 새 견적 라인에 요청 lineId 순서대로 기존 계보를 승계한다.
     *
     * @param lines 전체 교체할 새 견적 라인
     * @param lineIds 각 새 라인이 승계할 기존 견적 라인 ID; 신규 라인은 null
     */
    public void restoreEstimateLines(List<EstimateLine> lines, List<UUID> lineIds) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        requireSameSize(lines.size(), lineIds);
        for (int i = 0; i < lines.size(); i++) {
            assign(lines.get(i), lineIds.get(i));
        }
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

    private void assign(SlipLine line, UUID lineId) {
        if (lineId == null) {
            return;
        }
        BundleLineage lineage = lineagesById.get(lineId);
        if (line != null && lineage != null && lineage.isBundleComponent()) {
            line.assignBundleComponent(lineage.parentSetModel(), lineage.setHead());
        }
    }

    private void assign(EstimateLine line, UUID lineId) {
        if (lineId == null) {
            return;
        }
        BundleLineage lineage = lineagesById.get(lineId);
        if (line != null && lineage != null && lineage.isBundleComponent()) {
            line.assignBundleComponent(lineage.parentSetModel(), lineage.setHead());
        }
    }

    private void requireSameSize(int lineCount, List<UUID> lineIds) {
        if (lineIds == null || lineIds.size() != lineCount) {
            throw new IllegalArgumentException("라인과 lineId 목록의 크기가 일치하지 않습니다");
        }
    }

    private record BundleLineage(String parentSetModel, boolean setHead) {
        private boolean isBundleComponent() {
            return parentSetModel != null && !parentSetModel.isBlank();
        }
    }
}
