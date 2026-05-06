package com.samhanair.logis.partner.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 거래처 신용 거래 이력. {@link Partner#outstandingBalance} / {@link Partner#creditLimit}
 * 의 변경 사실을 append-only 로 누적 기록한다.
 *
 * <p>3 종 event_type:
 * <ul>
 *   <li>{@link CreditEventType#SLIP_ISSUED} — amount 양수, 잔액 증가, reference_no = slip 번호</li>
 *   <li>{@link CreditEventType#PAYMENT} — amount 양수, 잔액 차감 (서비스에서 처리), reference_no = 입금 번호</li>
 *   <li>{@link CreditEventType#CREDIT_LIMIT_CHANGE} — amount=0, delta_credit_limit 양/음수</li>
 * </ul>
 *
 * <p>{@link #balanceAfter} / {@link #creditLimitAfter} 스냅샷으로 시점별 잔액 / 한도 추적.
 */
@Entity
@Getter
@Table(name = "partner_credit_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerCreditHistory extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private CreditEventType eventType;

    /** 잔액 변동 금액 (양수). 차감/증가 방향은 eventType 으로 구분. CREDIT_LIMIT_CHANGE 시 0. */
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    /** 한도 변경 delta (양/음수). SLIP_ISSUED / PAYMENT 시 0. */
    @Column(name = "delta_credit_limit", precision = 15, scale = 2, nullable = false)
    private BigDecimal deltaCreditLimit;

    /** 처리 직후 미수금 잔액 스냅샷. */
    @Column(name = "balance_after", precision = 15, scale = 2, nullable = false)
    private BigDecimal balanceAfter;

    /** 처리 직후 신용한도 스냅샷. */
    @Column(name = "credit_limit_after", precision = 15, scale = 2, nullable = false)
    private BigDecimal creditLimitAfter;

    /** 외부 reference (slip 번호 / 입금 번호 등, 선택). */
    @Column(name = "reference_no", length = 50)
    private String referenceNo;

    /** 메모 (선택). */
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    private PartnerCreditHistory(Partner partner, CreditEventType eventType, BigDecimal amount,
                                 BigDecimal deltaCreditLimit, BigDecimal balanceAfter,
                                 BigDecimal creditLimitAfter, String referenceNo, String note) {
        this.partner = partner;
        this.eventType = eventType;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.deltaCreditLimit = deltaCreditLimit == null ? BigDecimal.ZERO : deltaCreditLimit;
        this.balanceAfter = balanceAfter;
        this.creditLimitAfter = creditLimitAfter;
        this.referenceNo = referenceNo;
        this.note = note;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 슬립 발행 이력 생성.
     *
     * @param partner 대상 거래처 (이미 {@link Partner#increaseBalance} 호출 완료한 상태)
     * @param amount 슬립 금액 (양수)
     * @param slipNo slip 번호 (reference_no 로 적재)
     */
    public static PartnerCreditHistory slipIssued(Partner partner, BigDecimal amount, String slipNo) {
        return new PartnerCreditHistory(partner, CreditEventType.SLIP_ISSUED, amount, BigDecimal.ZERO,
                partner.getOutstandingBalance(), partner.getCreditLimit(), slipNo, null);
    }

    /**
     * 결제 이력 생성.
     *
     * @param partner 대상 거래처 (이미 {@link Partner#decreaseBalance} 호출 완료한 상태)
     * @param amount 결제 금액 (양수)
     * @param paymentNo 입금/영수증 번호
     * @param note 비고 (선택)
     */
    public static PartnerCreditHistory payment(Partner partner, BigDecimal amount, String paymentNo, String note) {
        return new PartnerCreditHistory(partner, CreditEventType.PAYMENT, amount, BigDecimal.ZERO,
                partner.getOutstandingBalance(), partner.getCreditLimit(), paymentNo, note);
    }

    /**
     * 신용한도 변경 이력 생성.
     *
     * @param partner 대상 거래처 (이미 {@link Partner#changeCreditLimit} 호출 완료한 상태)
     * @param delta 변경 delta (양/음수)
     * @param note 사유 (선택)
     */
    public static PartnerCreditHistory creditLimitChange(Partner partner, BigDecimal delta, String note) {
        return new PartnerCreditHistory(partner, CreditEventType.CREDIT_LIMIT_CHANGE, BigDecimal.ZERO, delta,
                partner.getOutstandingBalance(), partner.getCreditLimit(), null, note);
    }
}
