package com.samhanair.logis.dcconfig.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/** 종합견적서 전역 가격 파라미터 싱글톤. */
@Entity
@Getter
@Table(name = "estimate_configs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class EstimateConfig extends BaseEntity {

    public static final BigDecimal DEFAULT_COMMON_HOME_DISCOUNT_RATE = new BigDecimal("0.4500");
    public static final BigDecimal DEFAULT_COMMON_COMMERCIAL_DISCOUNT_RATE = new BigDecimal("0.4500");
    public static final BigDecimal DEFAULT_OLD_PRODUCT_DISCOUNT_RATE = new BigDecimal("0.5000");
    public static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("0.1000");
    public static final BigDecimal DEFAULT_ZERO_RATE = new BigDecimal("0.0000");
    public static final String DEFAULT_FOOTER_NOTICE = """
            ※ 분기관은 임의 산정입니다.
            ※ 견적 내용 확정 시 재고확인 요청 부탁드립니다.
            ※ 본 견적은 견적일로부터 30일 이내에만 유효합니다.
            ※ 공공기관 발주 현장의 경우 본 견적은 무효이며, 별도의 검토가 필요합니다.
            """;

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "singleton_key", nullable = false)
    private Boolean singletonKey = Boolean.TRUE;

    @Column(name = "common_home_discount_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commonHomeDiscountRate = DEFAULT_COMMON_HOME_DISCOUNT_RATE;

    @Column(name = "common_commercial_discount_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commonCommercialDiscountRate = DEFAULT_COMMON_COMMERCIAL_DISCOUNT_RATE;

    @Column(name = "old_product_discount_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal oldProductDiscountRate = DEFAULT_OLD_PRODUCT_DISCOUNT_RATE;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal vatRate = DEFAULT_VAT_RATE;

    @Column(name = "card_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal cardFeeRate = DEFAULT_ZERO_RATE;

    @Column(name = "advance_discount_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal advanceDiscountRate = DEFAULT_ZERO_RATE;

    @Column(name = "combo_warn_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal comboWarnRate = DEFAULT_ZERO_RATE;

    @Column(name = "footer_notice", columnDefinition = "TEXT")
    private String footerNotice = DEFAULT_FOOTER_NOTICE;

    public static EstimateConfig defaults() {
        return new EstimateConfig();
    }

    public void update(
            BigDecimal commonHomeDiscountRate,
            BigDecimal commonCommercialDiscountRate,
            BigDecimal oldProductDiscountRate,
            BigDecimal vatRate,
            BigDecimal cardFeeRate,
            BigDecimal advanceDiscountRate,
            BigDecimal comboWarnRate,
            String footerNotice) {
        this.commonHomeDiscountRate = rateOrCurrent(commonHomeDiscountRate, this.commonHomeDiscountRate);
        this.commonCommercialDiscountRate = rateOrCurrent(commonCommercialDiscountRate, this.commonCommercialDiscountRate);
        this.oldProductDiscountRate = rateOrCurrent(oldProductDiscountRate, this.oldProductDiscountRate);
        this.vatRate = rateOrCurrent(vatRate, this.vatRate);
        this.cardFeeRate = rateOrCurrent(cardFeeRate, this.cardFeeRate);
        this.advanceDiscountRate = rateOrCurrent(advanceDiscountRate, this.advanceDiscountRate);
        this.comboWarnRate = rateOrCurrent(comboWarnRate, this.comboWarnRate);
        if (footerNotice != null) {
            this.footerNotice = footerNotice;
        }
    }

    private static BigDecimal rateOrCurrent(BigDecimal value, BigDecimal current) {
        if (value == null) {
            return current;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("요율은 0 이상이어야 합니다");
        }
        BigDecimal max = new BigDecimal("0.9999");
        return value.compareTo(max) > 0 ? max : value;
    }
}
