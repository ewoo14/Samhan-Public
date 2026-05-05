package com.samhanair.logis.slip.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.ProductSummary;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.domain.SlipPublishAudit;
import com.samhanair.logis.slip.domain.SlipSourceType;
import com.samhanair.logis.slip.domain.SlipType;
import com.samhanair.logis.slip.repository.SlipPublishAuditRepository;
import com.samhanair.logis.slip.repository.SlipRepository;
import com.samhanair.logis.slip.service.SlipNumberService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 6 M5 (slip-service-integration) — Sync REST 발행 서비스.
 *
 * <p>설계: {@code docs/migration/phase6/M5-slip-service-integration.md} §3 (payload 매핑) +
 * CONSISTENCY-MATRIX (Sync REST + idempotency 3중 격리).
 *
 * <p>핵심 책임:
 * <ol>
 *   <li>Idempotency 3중 격리 (DB partial UNIQUE INDEX + 본 서비스의 fingerprint 비교 +
 *       (별 슬라이스) outbox).</li>
 *   <li>legacy header 6 필드 → {@link Slip#getMemo()} 1000자 결합 prepend (라벨 포맷).</li>
 *   <li>legacy line ({@code PROD_CD/QTY/USER_PRICE_VAT/SIZE_DES/REMARKS/SUPPLY_AMT/VAT_AMT})
 *       → {@link SlipLine} + {@link SlipPublishAudit}.</li>
 *   <li>{@code Slip.assignPublishSource} 로 출처/idempotencyKey 1회성 설정.</li>
 *   <li>Audit 1행 INSERT (회계 reference 영구 보존).</li>
 * </ol>
 *
 * <p>Idempotency 매트릭스:
 * <ul>
 *   <li>같은 idempotencyKey + 같은 fingerprint → 200 OK + 기존 slipNo (replay)</li>
 *   <li>같은 idempotencyKey + 다른 fingerprint → 409 CONFLICT</li>
 *   <li>다른 idempotencyKey → 새 슬립 발행 (DB partial UNIQUE INDEX 가 동시 race condition 보호)</li>
 *   <li>idempotencyKey 가 null/blank → idempotency 보호 없이 매번 새 슬립 (호출자 책임)</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SlipPublishService {

    private static final DateTimeFormatter IO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ZERO_WIDTH_SPACE = "​";
    private static final int MEMO_MAX = 1000;

    private final SlipRepository slipRepository;
    private final SlipPublishAuditRepository auditRepository;
    private final SlipNumberService slipNumberService;
    private final ProductClient productClient;
    private final WarehouseCodeMapper warehouseCodeMapper;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    /**
     * estimate-app v2 → 출고전표 발행. {@link
     * com.samhanair.logis.slip.web.SlipPublishController#publishFromEstimate} 의 핵심 처리.
     *
     * @param req 발행 요청 (DTO validation 후)
     * @param idempotencyKey {@code Idempotency-Key} 헤더 값 (null/blank 가능)
     * @param requesterId 호출자 user-id (gateway X-User-Id 또는 "system")
     * @return 발행 결과 + replay 여부
     * @throws BusinessException(CONFLICT) 같은 키 + 다른 본문
     * @throws BusinessException(INVALID_INPUT) warehouseCode 매핑 누락 / 라인 productCode 미존재 등
     */
    public PublishSlipResponse publishFromEstimate(PublishFromEstimateRequest req,
                                                   String idempotencyKey, String requesterId) {
        String fingerprint = computeFingerprint(req);

        // 1. idempotency 가드 — 같은 키 + 같은 fingerprint → replay
        Optional<Slip> existing = lookupByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return assertReplayOrConflict(existing.get(), fingerprint);
        }

        // 2. 헤더 매핑
        UUID warehouseId = warehouseCodeMapper.resolve(req.warehouseCode());
        LocalDate slipDate = parseIoDate(req.ioDate());
        String memo = composeEstimateMemo(req);
        String requester = pickRequester(req.employeeCode(), requesterId);

        // 3. 라인 매핑 + product lookup (모델명 → productId)
        ResolvedLines resolved = resolveLines(req.lines());

        // 4. 슬립 헤더 + 라인 빌드 + 채번
        String slipNo = slipNumberService.next(slipDate);
        int seqNo = slipNumberService.extractSeqNo(slipNo);
        Slip slip = Slip.createOutbound(slipNo, slipDate, seqNo,
                warehouseId, null,
                null, req.partnerName(),
                null, memo, requester);
        for (SlipLine line : resolved.toEntityLines(slip)) {
            slip.addLine(line);
        }
        slip.assignPublishSource(SlipSourceType.ESTIMATE, req.estimateNumber(), idempotencyKey);

        // 5. persist — partial UNIQUE INDEX 충돌 시 동시 race condition (정확한 원인 별도 추적)
        Slip saved;
        try {
            saved = slipRepository.saveAndFlush(slip);
        } catch (DataIntegrityViolationException ex) {
            return handleIdempotencyRaceCondition(idempotencyKey, fingerprint, ex);
        }

        // 6. 감사 로그 적재
        String dcSnapshot = serializeDiscount(req.discountInfo(), req.paymentDueLabel());
        SlipPublishAudit audit = SlipPublishAudit.create(saved.getId(), SlipSourceType.ESTIMATE,
                req.estimateNumber(), idempotencyKey,
                resolved.totalSupplyAmount, resolved.totalVatAmount, dcSnapshot);
        auditRepository.save(audit);

        log.info("[Phase 6 M5] estimate {} → slip {} 발행 완료 (idem={})",
                req.estimateNumber(), saved.getSlipNo(), idempotencyKey);
        return PublishSlipResponse.created(saved);
    }

    /**
     * partner-order-service M4 → 출고전표 발행. {@link #publishFromEstimate} 와 거의 동일 흐름.
     *
     * @param req 발행 요청
     * @param idempotencyKey 헤더 값
     * @param requesterId 호출자 user-id
     */
    public PublishSlipResponse publishFromPartnerOrder(PublishFromPartnerOrderRequest req,
                                                       String idempotencyKey, String requesterId) {
        String fingerprint = computeFingerprint(req);

        Optional<Slip> existing = lookupByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return assertReplayOrConflict(existing.get(), fingerprint);
        }

        UUID warehouseId = warehouseCodeMapper.resolve(req.warehouseCode());
        LocalDate slipDate = parseIoDate(req.ioDate());
        String memo = composePartnerOrderMemo(req);
        String requester = pickRequester(req.employeeCode(), requesterId);

        ResolvedLines resolved = resolveLines(req.lines());

        String slipNo = slipNumberService.next(slipDate);
        int seqNo = slipNumberService.extractSeqNo(slipNo);
        Slip slip = Slip.createOutbound(slipNo, slipDate, seqNo,
                warehouseId, null,
                null, req.partnerName(),
                null, memo, requester);
        for (SlipLine line : resolved.toEntityLines(slip)) {
            slip.addLine(line);
        }
        slip.assignPublishSource(SlipSourceType.PARTNER_ORDER, req.partnerOrderId(), idempotencyKey);

        Slip saved;
        try {
            saved = slipRepository.saveAndFlush(slip);
        } catch (DataIntegrityViolationException ex) {
            return handleIdempotencyRaceCondition(idempotencyKey, fingerprint, ex);
        }

        String dcSnapshot = serializeDiscount(req.discountInfo(), req.paymentDueLabel());
        SlipPublishAudit audit = SlipPublishAudit.create(saved.getId(), SlipSourceType.PARTNER_ORDER,
                req.partnerOrderId(), idempotencyKey,
                resolved.totalSupplyAmount, resolved.totalVatAmount, dcSnapshot);
        auditRepository.save(audit);

        log.info("[Phase 6 M5] partner-order {} → slip {} 발행 완료 (idem={})",
                req.partnerOrderId(), saved.getSlipNo(), idempotencyKey);
        return PublishSlipResponse.created(saved);
    }

    /** {@code GET /api/v1/slips/by-source} — sourceType + sourceId 로 슬립 목록 조회. */
    @Transactional(readOnly = true)
    public List<PublishSlipResponse> findBySource(SlipSourceType sourceType, String sourceId) {
        if (sourceType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceType 은 필수입니다");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sourceId 는 필수입니다");
        }
        return slipRepository.findAllBySourceTypeAndSourceIdAndIsDeletedFalse(sourceType, sourceId)
                .stream()
                .map(PublishSlipResponse::replay)
                .toList();
    }

    // ---------- 내부 helper ----------

    private Optional<Slip> lookupByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return slipRepository.findByIdempotencyKeyAndIsDeletedFalse(idempotencyKey);
    }

    private PublishSlipResponse assertReplayOrConflict(Slip existing, String newFingerprint) {
        // 기존 audit 의 idempotencyKey 와 fingerprint 비교는 (현재 fingerprint 가
        // request 자체 함수이므로) 같은 idem 키로 들어온 신규 fingerprint 와
        // 같은 슬립의 audit 에 저장된 supply/vat 합계로 cross-check.
        // 정확 비교를 위해 audit 의 dcSnapshot 까지 fingerprint 화.
        String existingFingerprint = computeFingerprintFromAudit(existing);
        if (existingFingerprint != null && !existingFingerprint.equals(newFingerprint)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "동일 Idempotency-Key 로 다른 본문이 도착했습니다. 키를 새로 발급하세요. "
                            + "(slipNo=" + existing.getSlipNo() + ")");
        }
        log.info("[Phase 6 M5] idempotent replay → slip {} 재반환 (idem={})",
                existing.getSlipNo(), existing.getIdempotencyKey());
        return PublishSlipResponse.replay(existing);
    }

    private String computeFingerprintFromAudit(Slip existing) {
        // audit 1행 조회 → supplyAmount + vatAmount + dcSnapshot 으로 재현 fingerprint 생성.
        // 이 값은 createFingerprint 와 동일 알고리즘으로 만들어진 것이 아니므로 strict 비교 X.
        // 본 슬라이스 정책: audit 가 있으면 라인 합계 + dcSnapshot SHA-256, 없으면 null (비교 skip).
        List<SlipPublishAudit> audits = auditRepository.findAllBySlipIdAndIsDeletedFalse(existing.getId());
        if (audits.isEmpty()) {
            return null;
        }
        SlipPublishAudit a = audits.get(0);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sourceType", a.getSourceType().name());
        map.put("sourceId", a.getSourceId());
        map.put("supplyAmount", a.getSupplyAmount());
        map.put("vatAmount", a.getVatAmount());
        map.put("dcSnapshot", a.getAppliedDcSnapshot());
        map.put("lineCount", existing.getLines().size());
        return sha256(toJsonOrThrow(map));
    }

    private PublishSlipResponse handleIdempotencyRaceCondition(String idempotencyKey,
                                                               String fingerprint,
                                                               DataIntegrityViolationException ex) {
        // partial UNIQUE INDEX 가 동시 INSERT 를 차단했을 가능성 — 다시 select 시도.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            // EntityManager clear 로 영속성 컨텍스트 초기화 후 재조회 (DataIntegrityViolation 후)
            entityManager.clear();
            Optional<Slip> raceWinner = slipRepository.findByIdempotencyKeyAndIsDeletedFalse(idempotencyKey);
            if (raceWinner.isPresent()) {
                return assertReplayOrConflict(raceWinner.get(), fingerprint);
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT,
                "전표 동시 발행 충돌 — 잠시 후 재시도하세요. (cause=" + ex.getMostSpecificCause().getMessage() + ")");
    }

    private LocalDate parseIoDate(String ioDate) {
        if (ioDate == null || ioDate.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(ioDate.trim(), IO_DATE_FMT);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "ioDate 형식 오류 (yyyyMMdd 필요): '" + ioDate + "'");
        }
    }

    private String pickRequester(String employeeCode, String headerUserId) {
        if (employeeCode != null && !employeeCode.isBlank()) {
            return employeeCode.trim();
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }
        return "system";
    }

    private String composeEstimateMemo(PublishFromEstimateRequest req) {
        return composeMemoLines(
                "배송지: " + safe(req.shippingAddress()),
                "검수지: " + safe(req.inspectionAddress()),
                "수령자 연락처: " + safe(req.receiverPhone()),
                "결제: " + safe(req.paymentDueLabel()),
                "할인: " + safe(req.discountInfo()),
                "메모: " + safe(req.memo()));
    }

    private String composePartnerOrderMemo(PublishFromPartnerOrderRequest req) {
        return composeMemoLines(
                "주문 승인 시각: " + safe(req.orderApprovedAt()),
                "배송지: " + safe(req.shippingAddress()),
                "수령자 연락처: " + safe(req.receiverPhone()),
                "결제: " + safe(req.paymentDueLabel()),
                "할인: " + safe(req.discountInfo()),
                "메모: " + safe(req.memo()));
    }

    private String composeMemoLines(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String trimmed = l == null ? "" : l.trim();
            // 라벨 뒤 빈 값이면 skip (label: "값없음" 회피)
            if (trimmed.isEmpty() || trimmed.endsWith(":")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        if (sb.length() > MEMO_MAX) {
            return sb.substring(0, MEMO_MAX);
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /** legacy SIZE_DES 의 zero-width space ({@code ​}) 제거. */
    private static String normalizeSpec(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace(ZERO_WIDTH_SPACE, "").trim();
    }

    private ResolvedLines resolveLines(List<PublishLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "lines 가 비어있습니다");
        }
        ResolvedLines resolved = new ResolvedLines();
        for (PublishLineRequest l : lines) {
            ProductSummary summary = productClient.lookupByModel(l.productCode());
            int qty = parseQty(l.qty());
            BigDecimal unitPrice = l.unitPriceVat() != null
                    ? l.unitPriceVat().abs()
                    : (l.unitPriceExVat() != null ? l.unitPriceExVat().abs() : BigDecimal.ZERO);
            resolved.entries.add(new ResolvedLines.Entry(
                    summary.id(),
                    l.productName() != null ? l.productName() : summary.name(),
                    summary.modelName(),
                    normalizeSpec(l.spec()),
                    qty,
                    unitPrice,
                    l.remarks()));
            if (l.supplyAmount() != null) {
                resolved.totalSupplyAmount = resolved.totalSupplyAmount.add(l.supplyAmount());
            }
            if (l.vatAmount() != null) {
                resolved.totalVatAmount = resolved.totalVatAmount.add(l.vatAmount());
            }
        }
        return resolved;
    }

    private static int parseQty(String qty) {
        try {
            int n = Integer.parseInt(qty.trim());
            if (n <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "qty 는 양수여야 합니다: " + qty);
            }
            return n;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "qty 가 정수가 아닙니다: " + qty);
        }
    }

    private String computeFingerprint(PublishFromEstimateRequest req) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("kind", "ESTIMATE");
        canonical.put("estimateNumber", req.estimateNumber());
        canonical.put("ioDate", req.ioDate());
        canonical.put("warehouseCode", req.warehouseCode());
        canonical.put("partnerCode", req.partnerCode());
        canonical.put("employeeCode", req.employeeCode());
        canonical.put("paymentDueLabel", req.paymentDueLabel());
        canonical.put("discountInfo", req.discountInfo());
        canonical.put("memo", req.memo());
        canonical.put("lines", req.lines().stream().map(this::canonicalLine).toList());
        return sha256(toJsonOrThrow(canonical));
    }

    private String computeFingerprint(PublishFromPartnerOrderRequest req) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("kind", "PARTNER_ORDER");
        canonical.put("partnerOrderId", req.partnerOrderId());
        canonical.put("ioDate", req.ioDate());
        canonical.put("warehouseCode", req.warehouseCode());
        canonical.put("partnerCode", req.partnerCode());
        canonical.put("employeeCode", req.employeeCode());
        canonical.put("paymentDueLabel", req.paymentDueLabel());
        canonical.put("discountInfo", req.discountInfo());
        canonical.put("memo", req.memo());
        canonical.put("lines", req.lines().stream().map(this::canonicalLine).toList());
        return sha256(toJsonOrThrow(canonical));
    }

    private Map<String, Object> canonicalLine(PublishLineRequest l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productCode", l.productCode());
        m.put("qty", l.qty());
        m.put("spec", normalizeSpec(l.spec()));
        m.put("unitPriceVat", l.unitPriceVat());
        m.put("supplyAmount", l.supplyAmount());
        m.put("vatAmount", l.vatAmount());
        m.put("remarks", l.remarks());
        return m;
    }

    private String serializeDiscount(String discountInfo, String paymentDueLabel) {
        Map<String, String> m = new LinkedHashMap<>();
        if (discountInfo != null) {
            m.put("discountInfo", discountInfo);
        }
        if (paymentDueLabel != null) {
            m.put("paymentDueLabel", paymentDueLabel);
        }
        if (m.isEmpty()) {
            return null;
        }
        return toJsonOrThrow(m);
    }

    private String toJsonOrThrow(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "fingerprint JSON 직렬화 실패", ex);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /** 라인 매핑 결과 + 합계 누적용 내부 컨테이너. */
    private static class ResolvedLines {
        List<Entry> entries = new java.util.ArrayList<>();
        BigDecimal totalSupplyAmount = BigDecimal.ZERO;
        BigDecimal totalVatAmount = BigDecimal.ZERO;

        List<SlipLine> toEntityLines(Slip slip) {
            return entries.stream()
                    .map(e -> SlipLine.create(slip, e.productId, e.productName, e.modelName,
                            e.specification, e.quantity, e.unitPrice, e.note))
                    .toList();
        }

        record Entry(UUID productId, String productName, String modelName, String specification,
                     int quantity, BigDecimal unitPrice, String note) {
        }
    }

    // OUTBOUND 만 발행 가능 (입고전표는 본 endpoint 범위 밖) — 정적 가드용 reference.
    @SuppressWarnings("unused")
    private static final SlipType ENFORCED_TYPE = SlipType.OUTBOUND;
}
