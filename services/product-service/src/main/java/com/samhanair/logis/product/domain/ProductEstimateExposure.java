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
 * 품목 견적 카탈로그 노출 M:N 행.
 *
 * <p>한 품목은 여러 {@link EstimateCategory} 에 동시에 노출될 수 있고, 표시 순서는
 * 카테고리별로 독립 관리된다. {@link Product} 와 양방향 컬렉션을 만들지 않고
 * {@code productId} raw UUID 를 저장해 구성품/스펙과 동일하게 cascade 및 잠금 범위를
 * 좁게 유지한다.
 *
 * <p>삭제는 {@link BaseEntity#markDeleted(String)} 기반 soft-delete 만 허용하며,
 * 활성 행 조회는 {@link SQLRestriction} 으로 제한한다.
 */
@Entity
@Getter
@Table(name = "product_estimate_exposure")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ProductEstimateExposure extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 노출 대상 Product.id. FK 는 DB 에서만 강제하고 JPA 연관은 만들지 않는다. */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** 견적 카탈로그 카테고리. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estimate_category", nullable = false, length = 20)
    private EstimateCategory estimateCategory;

    /** 카테고리 내부 표시 순서. null 은 정렬 후순위다. */
    @Column(name = "display_order")
    private Integer displayOrder;

    private ProductEstimateExposure(UUID productId, EstimateCategory estimateCategory, Integer displayOrder) {
        this.productId = productId;
        this.estimateCategory = estimateCategory;
        this.displayOrder = displayOrder;
    }

    /**
     * 신규 노출 행을 생성한다.
     *
     * @param productId 대상 품목 ID
     * @param estimateCategory 견적 카테고리
     * @param displayOrder 카테고리별 표시 순서
     * @return 활성 노출 행
     */
    public static ProductEstimateExposure create(UUID productId,
                                                 EstimateCategory estimateCategory,
                                                 Integer displayOrder) {
        if (productId == null) {
            throw new IllegalArgumentException("productId 필수");
        }
        if (estimateCategory == null) {
            throw new IllegalArgumentException("estimateCategory 필수");
        }
        return new ProductEstimateExposure(productId, estimateCategory, displayOrder);
    }

    /**
     * 카테고리별 표시 순서를 갱신한다.
     *
     * @param displayOrder 1-based 표시 순서. null 은 정렬 후순위.
     */
    public void changeDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
