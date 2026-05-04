package com.samhanair.logis.slip.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 전표 라인 — productId/이름 snapshot + 수량 + 단가 + lineTotal(=수량×단가) 자동 계산.
 * 라인 단위 mutation(수량/단가/메모 변경)은 서비스 레이어에서 헤더 상태 가드 후 호출되어야 한다.
 */
@Entity
@Getter
@Table(name = "slip_lines")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SlipLine extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slip_id", nullable = false)
    private Slip slip;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "model_name", length = 100)
    private String modelName;

    /**
     * 규격 — Slice A (sales-polish-2) 신규 필드 (사용자 피드백 #4).
     * 예: "220V", "4HP", "Φ80×L1200". 사용자 자유 입력 (제품 마스터에서 자동 추출 X).
     * 작업지시서 라인 표 7-col 의 "규격" 컬럼에 표시.
     */
    @Column(name = "specification", length = 50)
    private String specification;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 17, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "note", length = 200)
    private String note;

    private SlipLine(Slip slip, UUID productId, String productName, String modelName,
                     String specification, int quantity, BigDecimal unitPrice, String note) {
        validatePositive(quantity);
        validateUnitPrice(unitPrice);
        this.slip = slip;
        this.productId = productId;
        this.productName = productName;
        this.modelName = modelName;
        this.specification = specification;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.note = note;
        this.lineTotal = computeLineTotal(quantity, unitPrice);
    }

    /**
     * 라인 1건 생성. quantity 양수 + unitPrice 비음수 검증 후 lineTotal 자동 계산.
     *
     * <p>Slice A (sales-polish-2): {@code specification} 파라미터 신규 추가 (사용자 피드백 #4).
     * 호환성을 위해 {@code specification} 은 nullable.
     *
     * @param slip 헤더 (cascade ALL — 영속화 전이어도 무방)
     * @param productId 제품 UUID (서비스 레이어 ProductClient 사전 검증)
     * @param productName snapshot 명칭 (필수, 최대 200자)
     * @param modelName snapshot 모델명 (선택)
     * @param specification 규격 (선택, 최대 50자) — 예: "220V", "4HP"
     * @param quantity 수량 (1 이상)
     * @param unitPrice 단가 (0 이상)
     * @param note 라인 메모 (선택, 최대 200자)
     * @return 영속화 전 SlipLine 인스턴스
     * @throws IllegalArgumentException 수량 0 이하 또는 unitPrice 가 음수일 때
     */
    public static SlipLine create(Slip slip, UUID productId, String productName, String modelName,
                                  String specification, int quantity, BigDecimal unitPrice,
                                  String note) {
        return new SlipLine(slip, productId, productName, modelName, specification,
                quantity, unitPrice, note);
    }

    /**
     * 수량 변경 — 양수만 허용. lineTotal 재계산. 헤더 상태 가드는 서비스 레이어 책임.
     *
     * @param newQuantity 새 수량 (1 이상)
     * @throws IllegalArgumentException newQuantity 가 0 이하일 때
     */
    public void changeQuantity(int newQuantity) {
        validatePositive(newQuantity);
        this.quantity = newQuantity;
        this.lineTotal = computeLineTotal(newQuantity, this.unitPrice);
    }

    /**
     * 단가 변경 — 비음수만 허용. lineTotal 재계산.
     *
     * @param newUnitPrice 새 단가 (0 이상)
     * @throws IllegalArgumentException newUnitPrice 가 음수일 때
     */
    public void changeUnitPrice(BigDecimal newUnitPrice) {
        validateUnitPrice(newUnitPrice);
        this.unitPrice = newUnitPrice;
        this.lineTotal = computeLineTotal(this.quantity, newUnitPrice);
    }

    /**
     * 라인 메모 변경. null/공백 도 허용 (메모 제거).
     *
     * @param newNote 새 메모 (최대 200자, 길이 검증은 DB constraint)
     */
    public void changeNote(String newNote) {
        this.note = newNote;
    }

    /**
     * 규격 변경 — Slice A (sales-polish-2) 신규 mutator (사용자 피드백 #4).
     * null/공백 도 허용 (규격 제거). 헤더 상태 가드는 서비스 레이어 책임.
     *
     * @param newSpecification 새 규격 (최대 50자, 길이 검증은 DB constraint)
     */
    public void changeSpecification(String newSpecification) {
        this.specification = newSpecification;
    }

    private static BigDecimal computeLineTotal(int quantity, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }

    private static void validatePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 양수여야 합니다");
        }
    }

    private static void validateUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("단가는 0 이상이어야 합니다");
        }
    }
}
