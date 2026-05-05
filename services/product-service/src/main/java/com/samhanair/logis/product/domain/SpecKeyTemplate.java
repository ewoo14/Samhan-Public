package com.samhanair.logis.product.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 카테고리별 추천 스펙 키 — DOMAIN-EXTENSIONS §4 (53 row 시드).
 *
 * <p>출처: Migration Plan §2.1.1.2. 신규 품목 등록 시 estimateCategory 선택 →
 * isRecommended=TRUE 키들이 자동 추가됨 (값은 빈 칸).
 *
 * <p>unique constraint: (estimateCategory, specKey).
 */
@Entity
@Getter
@Table(name = "spec_key_template")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SpecKeyTemplate extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "estimate_category", nullable = false, length = 20)
    private EstimateCategory estimateCategory;

    @Column(name = "spec_key", nullable = false, length = 50)
    private String specKey;

    @Column(name = "default_unit", length = 20)
    private String defaultUnit;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_recommended", nullable = false)
    private Boolean isRecommended = Boolean.FALSE;

    private SpecKeyTemplate(EstimateCategory estimateCategory, String specKey, String defaultUnit,
                            int displayOrder, boolean isRecommended) {
        this.estimateCategory = estimateCategory;
        this.specKey = specKey;
        this.defaultUnit = defaultUnit;
        this.displayOrder = displayOrder;
        this.isRecommended = isRecommended;
    }

    public static SpecKeyTemplate create(EstimateCategory estimateCategory, String specKey,
                                         String defaultUnit, int displayOrder, boolean isRecommended) {
        if (estimateCategory == null) throw new IllegalArgumentException("estimateCategory 필수");
        if (specKey == null || specKey.isBlank()) throw new IllegalArgumentException("specKey 필수");
        return new SpecKeyTemplate(estimateCategory, specKey, defaultUnit, displayOrder, isRecommended);
    }
}
