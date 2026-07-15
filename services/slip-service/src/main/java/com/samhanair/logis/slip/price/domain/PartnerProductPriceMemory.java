package com.samhanair.logis.slip.price.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 거래처+품목 최근 수동단가 기억.
 *
 * <p>partnerId/productId UUID 는 화면 표시 식별자가 아니라 hidden state/API payload 전용 내부 키다.
 * 사용자 화면에는 거래처명, 거래처코드, 품목명 같은 비즈니스 식별자만 표시해야 한다.
 *
 * <p>저장 단가는 전표/견적 입력 필드와 동일한 VAT 포함 단가다. 사용자가 마지막으로 저장한 라인
 * 단가를 그대로 재조회해 라운드트립 왜곡을 만들지 않는다.
 */
@Entity
@Getter
@Table(name = "partner_product_price_memory",
        uniqueConstraints = @UniqueConstraint(name = "ux_partner_product_price_memory_pair",
                columnNames = {"partner_id", "product_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerProductPriceMemory extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "source", nullable = false, length = 30)
    private String source;

    /** 최근 라인 저장 출처. */
    public static final String SOURCE_LINE_SAVE = "LINE_SAVE";

    /** 신규 가격기억 엔티티를 만든다. */
    public static PartnerProductPriceMemory create(
            UUID partnerId, UUID productId, BigDecimal unitPrice, String source) {
        PartnerProductPriceMemory memory = new PartnerProductPriceMemory();
        memory.partnerId = partnerId;
        memory.productId = productId;
        memory.unitPrice = unitPrice;
        memory.source = source;
        return memory;
    }
}
