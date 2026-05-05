package com.samhanair.logis.slip.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 발행 감사 로그 — Phase 6 M5 (slip-service-integration) 신규.
 *
 * <p>회계 reference 영구 보존이 목적. estimate-app/partner-order-service 가 호출한
 * {@code POST /api/v1/slips/from-estimate} / {@code from-partner-order} 의 결과를 1행씩 적재한다.
 *
 * <p>본 테이블은 soft-delete 만 적용 (실삭제 X) — 회계 감사 추적 영구 보존.
 *
 * <p>저장 컬럼:
 * <ul>
 *   <li>{@code slipId} — 발행된 Slip FK (logical, 외래키 제약은 두지 않음 — 도메인 격리)</li>
 *   <li>{@code sourceType / sourceId / idempotencyKey} — Slip 의 동일 컬럼을 snapshot</li>
 *   <li>{@code supplyAmount / vatAmount} — legacy SaleList 의 SUPPLY_AMT / VAT_AMT 합계
 *       (라인 합계로 회계 검증용 — Slip.lines.lineTotal 와 round-trip 비교 가능)</li>
 *   <li>{@code appliedDcSnapshot} — DC/할인 정보 jsonb 그대로 보존 (legacy ADD_TXT_06_T 등)</li>
 * </ul>
 *
 * <p>BaseEntity 의 {@code createdAt/createdBy} 가 발행 시각/발행자 자동 기입.
 */
@Entity
@Getter
@Table(name = "slip_publish_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SlipPublishAudit extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "slip_id", nullable = false)
    private UUID slipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SlipSourceType sourceType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "supply_amount", precision = 17, scale = 2)
    private BigDecimal supplyAmount;

    @Column(name = "vat_amount", precision = 17, scale = 2)
    private BigDecimal vatAmount;

    /**
     * DC/할인 등 legacy 파생 메타데이터 jsonb 보존.
     * Hibernate 6 + PostgreSQL JSONB 매핑은 {@code @JdbcTypeCode(SqlTypes.JSON)} 사용.
     * 자유형 JSON 문자열 — 호출자가 ObjectMapper 로 직렬화 후 전달.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applied_dc_snapshot", columnDefinition = "jsonb")
    private String appliedDcSnapshot;

    /**
     * 발행 요청 본문 SHA-256 fingerprint (hex). idempotency replay 비교 전용.
     *
     * <p>같은 idempotency-key 로 동일 본문이 다시 들어왔는지 정확히 판정한다 (PR #76 회고:
     * supplyAmount/vatAmount 합으로 만든 후행 fingerprint 와 사전 요청 본문 fingerprint 가
     * 다른 알고리즘이라 항상 충돌하던 문제 해결).
     */
    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    private SlipPublishAudit(UUID slipId, SlipSourceType sourceType, String sourceId,
                             String idempotencyKey, BigDecimal supplyAmount, BigDecimal vatAmount,
                             String appliedDcSnapshot, String requestFingerprint) {
        if (slipId == null) {
            throw new IllegalArgumentException("slipId 는 필수입니다");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType 은 필수입니다");
        }
        this.slipId = slipId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.idempotencyKey = idempotencyKey;
        this.supplyAmount = supplyAmount;
        this.vatAmount = vatAmount;
        this.appliedDcSnapshot = appliedDcSnapshot;
        this.requestFingerprint = requestFingerprint;
    }

    /**
     * 감사 로그 생성 — service 레이어에서 신규 Slip 저장 직후 호출.
     *
     * @param slipId 방금 저장된 Slip 의 ID (필수)
     * @param sourceType 발행 출처 (필수, MANUAL 도 허용 — 모든 발행 추적용)
     * @param sourceId 출처 비즈니스 식별자 (선택)
     * @param idempotencyKey 호출자 발급 키 (선택)
     * @param supplyAmount 공급가액 합계 (legacy SUPPLY_AMT 합)
     * @param vatAmount 세액 합계 (legacy VAT_AMT 합)
     * @param appliedDcSnapshot DC/할인 정보 jsonb 문자열 (선택)
     * @param requestFingerprint 발행 요청 본문 SHA-256 fingerprint (idempotency replay 비교용, 선택)
     * @return persist 직전 인스턴스
     */
    public static SlipPublishAudit create(UUID slipId, SlipSourceType sourceType, String sourceId,
                                          String idempotencyKey, BigDecimal supplyAmount,
                                          BigDecimal vatAmount, String appliedDcSnapshot,
                                          String requestFingerprint) {
        return new SlipPublishAudit(slipId, sourceType, sourceId, idempotencyKey,
                supplyAmount, vatAmount, appliedDcSnapshot, requestFingerprint);
    }
}
