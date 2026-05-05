package com.samhanair.logis.product.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 동적 스펙 (1:N) — DOMAIN-EXTENSIONS §4 (사용자 명시 2026-05-05).
 *
 * <p>출처: Migration Plan §2.1.1.1 + estimate Code.js getSpecDetailMap_() (line 1006-1364)
 * 의 scanHome / scanSingle / scanComm 함수의 idx(H, [...]) 호출 인자 매트릭스.
 *
 * <p>unique constraint: (productId, specKey) — 동일 품목에 같은 키 중복 금지.
 */
@Entity
@Getter
@Table(name = "product_spec")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ProductSpec extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** 스펙 키 (예: 냉방성능(kW), 전원선, 규격). */
    @Column(name = "spec_key", nullable = false, length = 50)
    private String specKey;

    @Column(name = "spec_value", nullable = false, length = 255)
    private String specValue;

    /** 단위 (값에 단위 미포함 시만; kW/mm/m/kg/Kcal/h 등). */
    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    private ProductSpec(UUID productId, String specKey, String specValue, String unit, int displayOrder) {
        this.productId = productId;
        this.specKey = specKey;
        this.specValue = specValue;
        this.unit = unit;
        this.displayOrder = displayOrder;
    }

    public static ProductSpec create(UUID productId, String specKey, String specValue,
                                     String unit, int displayOrder) {
        if (productId == null) throw new IllegalArgumentException("productId 필수");
        if (specKey == null || specKey.isBlank()) throw new IllegalArgumentException("specKey 필수");
        if (specValue == null) throw new IllegalArgumentException("specValue 필수 (빈 문자 허용)");
        return new ProductSpec(productId, specKey, specValue, unit, displayOrder);
    }

    public void editValue(String specValue, String unit) {
        if (specValue != null) {
            this.specValue = specValue;
        }
        this.unit = unit;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
