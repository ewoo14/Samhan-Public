package com.samhanair.logis.slip.service;

import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.estimate.domain.EstimateLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
 * <p><b>매칭 알고리즘 — 2-패스 전역 매칭 (R6-H1 재설계).</b> 종전의 per-line greedy 는
 * "수정/신규 라인이 요청 앞쪽에 있으면 뒤쪽 무수정 라인의 exact 매칭 대상(특히 head 엔트리)을
 * 먼저 소비"해 계보를 오귀속시켰다 — 라이브 CONFIRMED ×2 (신규 라인의 head 탈취 + 진짜 구성품
 * 평면화 → 배분가 각인 루프 / 단품 라인의 head 오귀속 → 사용자 최신 단가 침묵 억제).
 * <ol>
 *   <li><b>1-패스 (exact 전역 선매칭)</b> — 모든 요청 라인에 대해 full fingerprint
 *       (productId+품목명+모델명+규격+수량+단가+메모) 완전 일치 엔트리를 먼저 소비한다.
 *       무수정 라인은 요청 내 위치와 무관하게 자기 계보를 보존한다.</li>
 *   <li><b>2-패스 (fallback 전역 스코어 그리디)</b> — 미매칭 라인 × 미소비 동일 productId
 *       엔트리의 모든 조합을 fingerprint 거리(식별 필드 차이 수 → 수량 차 → 단가 차)
 *       오름차순으로 정렬해 가까운 조합부터 소비한다. 거리 동률이면 <b>빈 계보(비구성품)
 *       엔트리 우선</b>. per-line 순차가 아닌 전역 정렬이므로 요청 라인 순서에 따른
 *       오귀속이 없다.</li>
 *   <li><b>head 엔트리는 exact 전용 (1-패스 한정)</b> — setHead 엔트리는 2-패스 후보에서
 *       제외한다. head 오귀속은 세트 표시 자체를 다른 라인에 옮기고 진짜 구성품을 평면화해
 *       이후 모든 저장마다 배분가를 재기억하는 복구 불능 루프(spec §24 위배)를 만들므로,
 *       head 는 완전 일치일 때만 승계한다.</li>
 * </ol>
 *
 * <p><b>리뷰 권고와의 편차 (근거)</b> — R6 권고는 "빈 계보 우선 → 남으면 거리 최소" 순이지만,
 * 빈 계보를 거리보다 우선하면 [세트 구성품 수량 수정 + 같은 품목 단품 삭제] 케이스에서 수정된
 * 구성품이 값이 동떨어진 단품 엔트리에 결합해 일반 라인이 되고, 그 순간 배분가가 LINE_SAVE 로
 * 각인된다(§24 위배 재생산). 거리 우선 + 빈 계보 동률 tie-break 는 두 라이브 변형과 위 대칭
 * 케이스 모두에서 §24 invariant 를 보존한다.
 *
 * <p><b>원리적 한계</b> — DTO 에 영속 lineId 가 없는 현 계약에서 "신규 라인"과 "값이 수정된
 * 기존 라인"은 서버가 구분할 수 없다. 본 매칭은 클라이언트를 신뢰하지 않으면서 무수정 저장을
 * 완전 보존하고 수정 저장의 오귀속을 최소화하는 서버 권위 규칙이며, 완전한 해소는 lineId
 * 왕복 계약 도입이 필요하다.
 */
public final class BundleLineageResolver {

    /** 단가 파싱 불가/편측 null 시 사용하는 최대 거리 sentinel. */
    private static final BigDecimal PRICE_DISTANCE_MAX = new BigDecimal(Long.MAX_VALUE);

    /**
     * 2-패스 fallback 전역 정렬 순서 — fingerprint 거리 오름차순, 동률 시 빈 계보 우선,
     * 그래도 동률이면 요청 순서·캡처 순서로 결정성을 고정한다.
     */
    private static final Comparator<FallbackCandidate> FALLBACK_ORDER = Comparator
            .comparingLong(FallbackCandidate::identityDistance)
            .thenComparingLong(FallbackCandidate::quantityDistance)
            .thenComparing(FallbackCandidate::priceDistance)
            .thenComparingInt(FallbackCandidate::lineagePenalty)
            .thenComparingInt(FallbackCandidate::lineIndex)
            .thenComparingInt(FallbackCandidate::entryOrder);

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

    /**
     * 전체 교체될 새 전표 라인 전량에 기존 계보를 2-패스 전역 매칭으로 복원한다.
     *
     * <p>요청의 <b>모든</b> 라인을 선구성한 뒤 1회 호출해야 한다 — 부분 리스트로 나눠 호출하면
     * 1-패스 exact 선매칭의 전역성이 깨져 per-line greedy 와 같은 오귀속이 재발한다.
     *
     * @param lines 교체 라인 전량 (요청 순서 그대로)
     */
    public void restoreSlipLines(List<SlipLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<BundleLineage> assigned = assignAll(lines.stream()
                .map(BundleLineageResolver::fingerprint)
                .toList());
        for (int i = 0; i < lines.size(); i++) {
            BundleLineage lineage = assigned.get(i);
            if (lineage.isBundleComponent()) {
                lines.get(i).assignBundleComponent(lineage.parentSetModel(), lineage.setHead());
            }
        }
    }

    /**
     * 전체 교체될 새 견적 라인 전량에 기존 계보를 2-패스 전역 매칭으로 복원한다.
     *
     * <p>{@link #restoreSlipLines(List)} 와 동일 규칙 — 전 라인 선구성 후 1회 호출.
     *
     * @param lines 교체 라인 전량 (요청 순서 그대로, 세트 전개로 직접 계보가 부여된 라인 제외)
     */
    public void restoreEstimateLines(List<EstimateLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<BundleLineage> assigned = assignAll(lines.stream()
                .map(BundleLineageResolver::fingerprint)
                .toList());
        for (int i = 0; i < lines.size(); i++) {
            BundleLineage lineage = assigned.get(i);
            if (lineage.isBundleComponent()) {
                lines.get(i).assignBundleComponent(lineage.parentSetModel(), lineage.setHead());
            }
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

    private void capture(LineFingerprint fingerprint, String parentSetModel, boolean setHead) {
        if (fingerprint.productId() == null) {
            return;
        }
        entries.add(new LineageEntry(fingerprint, new BundleLineage(parentSetModel, setHead)));
    }

    /**
     * 2-패스 전역 매칭 본체. 입력 fingerprint 순서와 1:1 정렬된 계보 리스트를 반환한다
     * (미매칭 = {@link BundleLineage#NONE}).
     */
    private List<BundleLineage> assignAll(List<LineFingerprint> fingerprints) {
        BundleLineage[] result = new BundleLineage[fingerprints.size()];

        // [1-패스] 전 요청 라인의 full-fingerprint 전역 선매칭 — 무수정 라인이 요청 내 위치와
        // 무관하게 자기 엔트리를 먼저 확보한다. 동일 fingerprint 다건은 캡처 순서대로 소비한다
        // (상세 응답 재전송 시 라인 순서가 보존되므로 순서 기반 대응이 원 계보와 일치한다).
        for (int i = 0; i < fingerprints.size(); i++) {
            LineFingerprint fingerprint = fingerprints.get(i);
            if (fingerprint.productId() == null) {
                continue;
            }
            for (LineageEntry entry : entries) {
                if (!entry.used && entry.fingerprint.equals(fingerprint)) {
                    entry.used = true;
                    result[i] = entry.lineage;
                    break;
                }
            }
        }

        // [2-패스] 미매칭 라인 × 미소비 동일 productId 엔트리 전역 스코어 그리디.
        // head 엔트리는 exact 전용이므로 후보에서 제외한다.
        List<FallbackCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < fingerprints.size(); i++) {
            if (result[i] != null) {
                continue;
            }
            LineFingerprint fingerprint = fingerprints.get(i);
            if (fingerprint.productId() == null) {
                continue;
            }
            for (int entryOrder = 0; entryOrder < entries.size(); entryOrder++) {
                LineageEntry entry = entries.get(entryOrder);
                if (entry.used || entry.lineage.setHead()
                        || !Objects.equals(entry.fingerprint.productId(), fingerprint.productId())) {
                    continue;
                }
                candidates.add(new FallbackCandidate(
                        i, entry, entryOrder,
                        identityDistance(fingerprint, entry.fingerprint),
                        quantityDistance(fingerprint, entry.fingerprint),
                        priceDistance(fingerprint, entry.fingerprint),
                        entry.lineage.isBundleComponent() ? 1 : 0));
            }
        }
        candidates.sort(FALLBACK_ORDER);
        for (FallbackCandidate candidate : candidates) {
            if (result[candidate.lineIndex()] != null || candidate.entry().used) {
                continue;
            }
            candidate.entry().used = true;
            result[candidate.lineIndex()] = candidate.entry().lineage;
        }

        List<BundleLineage> assigned = new ArrayList<>(fingerprints.size());
        for (BundleLineage lineage : result) {
            assigned.add(lineage == null ? BundleLineage.NONE : lineage);
        }
        return assigned;
    }

    /** 식별 필드(품목명/모델명/규격/메모) 불일치 수 — 0(동일)~4. */
    private static long identityDistance(LineFingerprint a, LineFingerprint b) {
        long distance = 0;
        if (!Objects.equals(a.productName(), b.productName())) {
            distance++;
        }
        if (!Objects.equals(a.modelName(), b.modelName())) {
            distance++;
        }
        if (!Objects.equals(a.specification(), b.specification())) {
            distance++;
        }
        if (!Objects.equals(a.note(), b.note())) {
            distance++;
        }
        return distance;
    }

    private static long quantityDistance(LineFingerprint a, LineFingerprint b) {
        return Math.abs((long) a.quantity() - (long) b.quantity());
    }

    /** 단가 절대 차. 편측 null 은 최대 거리로 강등한다 (양측 null 은 0). */
    private static BigDecimal priceDistance(LineFingerprint a, LineFingerprint b) {
        if (a.unitPrice() == null || b.unitPrice() == null) {
            return Objects.equals(a.unitPrice(), b.unitPrice()) ? BigDecimal.ZERO : PRICE_DISTANCE_MAX;
        }
        return new BigDecimal(a.unitPrice()).subtract(new BigDecimal(b.unitPrice())).abs();
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

    /** 2-패스 fallback 후보 — (미매칭 라인, 미소비 엔트리) 조합 1건과 그 거리 스코어. */
    private record FallbackCandidate(
            int lineIndex,
            LineageEntry entry,
            int entryOrder,
            long identityDistance,
            long quantityDistance,
            BigDecimal priceDistance,
            int lineagePenalty) {
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
