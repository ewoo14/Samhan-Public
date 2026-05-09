package com.samhanair.logis.partner.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * Phase 10 PR-D Part B — BLOCK 발송금지 거래처 (Samhan Public 알림 발송 차단).
 *
 * <p>본 entity 는 PR-E 알림 발송 (chat / push) 진입점에서
 * {@code BlockedPartnerRepository.existsByPartnerCodeAndIsDeletedFalse(partnerCode)} 가드로
 * 사용. 차단 해제 = {@link BaseEntity#markDeleted(String)} (soft-delete) — partial unique index
 * 가 partnerCode 재차단 허용.
 *
 * <p>사용자 명시 — business_name 은 snapshot only (감사 / 추적 목적), 진실의 원천은 partner_code
 * (+ {@link Partner#getName()}). Notion CSV import 시점에는 partner-service 자체의
 * {@code PartnerService.findByName} 으로 partnerCode 를 역추적하며, partners.name 변경 후에도
 * 본 row 의 snapshot 은 import 시점 상호를 보존한다.
 */
@Entity
@Getter
@Table(name = "blocked_partners")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class BlockedPartner extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 차단 대상 거래처 코드 (사용자 노출 식별자). 활성 행 unique. */
    @Column(name = "partner_code", nullable = false, length = 50)
    private String partnerCode;

    /** 차단 시점 거래처 상호 snapshot (감사 / 이카운트 사업자명 보존, 진실의 원천은 partners.name). */
    @Column(name = "partner_business_name_snapshot", nullable = false, length = 200)
    private String partnerBusinessNameSnapshot;

    /** 차단 사유 (admin 운영 참고, nullable). */
    @Column(name = "block_reason", length = 500)
    private String blockReason;

    /** 차단 시점 (audit created_at 와 별도 — Notion export 의 "생성 일시" 보존). */
    @Column(name = "blocked_at", nullable = false)
    private LocalDateTime blockedAt;

    /** import source (NOTION_IMPORT / MANUAL / LEGACY_GAS). */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    private BlockedPartner(String partnerCode, String partnerBusinessNameSnapshot,
                           String blockReason, LocalDateTime blockedAt, String source) {
        if (partnerCode == null || partnerCode.isBlank()) {
            throw new IllegalArgumentException("partnerCode 필수");
        }
        if (partnerBusinessNameSnapshot == null || partnerBusinessNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("partnerBusinessNameSnapshot 필수");
        }
        this.partnerCode = partnerCode;
        this.partnerBusinessNameSnapshot = partnerBusinessNameSnapshot;
        this.blockReason = blockReason;
        this.blockedAt = blockedAt == null ? LocalDateTime.now() : blockedAt;
        this.source = source == null ? "MANUAL" : source;
    }

    /**
     * 신규 BLOCK 생성 (admin 단건 + CSV import 양쪽에서 사용).
     *
     * @param partnerCode 차단 대상 partnerCode
     * @param businessNameSnapshot 차단 시점 거래처 상호 (snapshot)
     * @param blockReason 차단 사유 (nullable)
     * @param blockedAt 차단 시점 (Notion CSV 의 "생성 일시" 또는 now())
     * @param source NOTION_IMPORT / MANUAL / LEGACY_GAS
     * @return 영속화 전 BlockedPartner
     */
    public static BlockedPartner create(String partnerCode, String businessNameSnapshot,
                                        String blockReason, LocalDateTime blockedAt, String source) {
        return new BlockedPartner(partnerCode, businessNameSnapshot, blockReason, blockedAt, source);
    }

    /** 차단 사유 갱신 (admin 운영). */
    public void updateReason(String blockReason) {
        this.blockReason = blockReason;
    }
}
