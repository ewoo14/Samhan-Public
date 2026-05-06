package com.samhanair.logis.partner.domain;

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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 거래처 마스터.
 *
 * <p>{@link #partnerCode} = 사용자 노출 식별자 (UUID 비공개 가드, memory feedback_uuid_no_user_visibility).
 * {@link #id} 는 form hidden field 또는 path variable 로만 사용. M5 slip-service 의
 * partnerCode → partnerId lookup 의존성 해소가 본 entity 도입의 1차 목적.
 *
 * <p>신용 거래 정보 ({@link #creditLimit} / {@link #outstandingBalance}) 는 {@link PartnerCreditHistory}
 * 의 누적과 본 row 의 캐시값이 일관 보존되어야 한다 (서비스 레이어에서 동일 transaction 으로 갱신).
 */
@Entity
@Getter
@Table(name = "partners")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Partner extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 사용자 노출 식별자 (예: P-2026-0001). UUID 비공개 가드. partial unique index 가 활성 행 중복 방지. */
    @Column(name = "partner_code", nullable = false, length = 50)
    private String partnerCode;

    /** 사업자등록번호 (한국 표준 10자리, '-' 포함 입력 가능). 활성 행 unique. */
    @Column(name = "biz_no", nullable = false, length = 20)
    private String bizNo;

    /** 거래처 상호. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** 거래처 주소 (선택). */
    @Column(name = "address", length = 500)
    private String address;

    /** 거래처 대표 연락처 (선택). */
    @Column(name = "phone", length = 30)
    private String phone;

    /** 신용한도 (원). 0 이면 신용거래 불가. */
    @Column(name = "credit_limit", precision = 15, scale = 2, nullable = false)
    private BigDecimal creditLimit;

    /** 현재 미수금 잔액 (원). {@link PartnerCreditHistory} 누적 값과 일관. */
    @Column(name = "outstanding_balance", precision = 15, scale = 2, nullable = false)
    private BigDecimal outstandingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PartnerStatus status;

    private Partner(String partnerCode, String bizNo, String name, String address, String phone,
                    BigDecimal creditLimit) {
        if (partnerCode == null || partnerCode.isBlank()) {
            throw new IllegalArgumentException("partnerCode 필수");
        }
        if (bizNo == null || bizNo.isBlank()) {
            throw new IllegalArgumentException("bizNo 필수");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 필수");
        }
        this.partnerCode = partnerCode;
        this.bizNo = bizNo;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
        this.outstandingBalance = BigDecimal.ZERO;
        this.status = PartnerStatus.ACTIVE;
    }

    /**
     * 신규 거래처 등록 (status=ACTIVE, outstandingBalance=0).
     *
     * @param partnerCode 사용자 노출 식별자
     * @param bizNo 사업자등록번호
     * @param name 거래처 상호
     * @param address 주소 (nullable)
     * @param phone 연락처 (nullable)
     * @param creditLimit 신용한도 (null → 0)
     * @return 영속화 전 신규 Partner
     */
    public static Partner register(String partnerCode, String bizNo, String name, String address,
                                   String phone, BigDecimal creditLimit) {
        return new Partner(partnerCode, bizNo, name, address, phone, creditLimit);
    }

    /**
     * 거래처 마스터 정보 갱신 (admin CRUD update). partnerCode / bizNo 는 식별자이므로
     * 변경 불가, name / address / phone 만 갱신.
     */
    public void updateProfile(String name, String address, String phone) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 필수");
        }
        this.name = name;
        this.address = address;
        this.phone = phone;
    }

    /**
     * 신용한도 변경 — {@link PartnerCreditService} 가 동일 transaction 에서
     * {@link PartnerCreditHistory} 와 함께 호출.
     *
     * @param newLimit 새 한도 (null/음수 거부)
     * @return 변경 delta (newLimit - 기존)
     */
    public BigDecimal changeCreditLimit(BigDecimal newLimit) {
        if (newLimit == null || newLimit.signum() < 0) {
            throw new IllegalArgumentException("creditLimit 은 0 이상 필수");
        }
        BigDecimal delta = newLimit.subtract(this.creditLimit);
        this.creditLimit = newLimit;
        return delta;
    }

    /**
     * 슬립 발행으로 미수금 증가. {@link PartnerCreditService} 가 동일 transaction 에서
     * {@link PartnerCreditHistory} 와 함께 호출.
     */
    public void increaseBalance(BigDecimal amount) {
        requirePositiveAmount(amount);
        this.outstandingBalance = this.outstandingBalance.add(amount);
    }

    /**
     * 결제 입금으로 미수금 차감. 차감 후 잔액이 음수가 되는 경우 거부 (선결제 = 별도 도메인).
     */
    public void decreaseBalance(BigDecimal amount) {
        requirePositiveAmount(amount);
        BigDecimal next = this.outstandingBalance.subtract(amount);
        if (next.signum() < 0) {
            throw new IllegalStateException(
                    "결제 금액이 미수금 잔액을 초과합니다: balance=" + this.outstandingBalance + ", amount=" + amount);
        }
        this.outstandingBalance = next;
    }

    /** 거래 일시 중지 (한도 초과 등). 신규 슬립 발행 차단, 결제는 허용. */
    public void suspend() {
        this.status = PartnerStatus.SUSPENDED;
    }

    /** 거래 재개. */
    public void activate() {
        this.status = PartnerStatus.ACTIVE;
    }

    /** 거래 종료 (계약 해지). 조회만 허용 — soft-delete 와 구분, 정산 / 회계 목적 보관. */
    public void terminate() {
        this.status = PartnerStatus.TERMINATED;
    }

    /**
     * 신규 슬립 발행 시 신용한도 가드. 미수금 + 추가 발행 금액 > creditLimit 이면 거부.
     *
     * @param additional 신규 슬립 금액
     * @return 한도 내면 {@code true}
     */
    public boolean canIssueSlip(BigDecimal additional) {
        if (this.status != PartnerStatus.ACTIVE) {
            return false;
        }
        if (additional == null || additional.signum() < 0) {
            return false;
        }
        return this.outstandingBalance.add(additional).compareTo(this.creditLimit) <= 0;
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount 는 양수 필수");
        }
    }
}
