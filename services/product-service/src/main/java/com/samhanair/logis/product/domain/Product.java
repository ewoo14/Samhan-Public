package com.samhanair.logis.product.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 제품 마스터 (plan §3.5 핵심). soft-deleted via {@link SQLRestriction}; 단종은
 * 별도 {@link ProductStatus} enum 으로 표현 (soft-delete 와 직교 — 개발책임자 결재).
 *
 * <p>가격은 {@link BigDecimal} (NUMERIC(15,2)) + {@code currency} CHAR(3) 으로 저장하며
 * 본 슬라이스부터 통화 컬럼을 추가한다 (KRW default).
 *
 * <p>태그는 PostgreSQL {@code jsonb} 컬럼으로 저장 — Hibernate 6 native
 * {@link JdbcTypeCode @JdbcTypeCode(SqlTypes.JSON)} 매핑을 사용해 별도 컨버터/
 * 외부 hibernate-types 의존성을 도입하지 않는다.
 */
@Entity
@Getter
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "selling_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "purchase_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private Map<String, String> tags;

    @Column(name = "description", length = 1000)
    private String description;

    private Product(String name, String modelName, Category category,
                    BigDecimal sellingPrice, BigDecimal purchasePrice, String currency,
                    Map<String, String> tags, String description) {
        this.name = name;
        this.modelName = modelName;
        this.category = category;
        this.sellingPrice = sellingPrice;
        this.purchasePrice = purchasePrice;
        this.currency = currency;
        this.status = ProductStatus.ACTIVE;
        this.tags = tags;
        this.description = description;
    }

    public static Product create(String name, String modelName, Category category,
                                 BigDecimal sellingPrice, BigDecimal purchasePrice, String currency,
                                 Map<String, String> tags, String description) {
        validateNonNegative(sellingPrice, "출고가");
        validateNonNegative(purchasePrice, "납품가");
        return new Product(name, modelName, category, sellingPrice, purchasePrice,
                normaliseCurrency(currency), tags, description);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeModelName(String modelName) {
        this.modelName = modelName;
    }

    public void changeCategory(Category category) {
        this.category = category;
    }

    public void repriceSelling(BigDecimal sellingPrice) {
        validateNonNegative(sellingPrice, "출고가");
        this.sellingPrice = sellingPrice;
    }

    public void repricePurchase(BigDecimal purchasePrice) {
        validateNonNegative(purchasePrice, "납품가");
        this.purchasePrice = purchasePrice;
    }

    public void changeCurrency(String currency) {
        this.currency = normaliseCurrency(currency);
    }

    public void replaceTags(Map<String, String> tags) {
        this.tags = tags == null ? null : new HashMap<>(tags);
    }

    public void putTag(String key, String value) {
        if (this.tags == null) {
            this.tags = new HashMap<>();
        }
        this.tags.put(key, value);
    }

    public void removeTag(String key) {
        if (this.tags != null) {
            this.tags.remove(key);
        }
    }

    public void discontinue() {
        this.status = ProductStatus.DISCONTINUED;
    }

    public void reactivate() {
        this.status = ProductStatus.ACTIVE;
    }

    public void editDescription(String description) {
        this.description = description;
    }

    private static void validateNonNegative(BigDecimal value, String fieldLabel) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(fieldLabel + "는 0 이상이어야 합니다");
        }
    }

    private static String normaliseCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "KRW";
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("통화 코드는 ISO 4217 3자리여야 합니다: " + currency);
        }
        return currency.toUpperCase();
    }
}
