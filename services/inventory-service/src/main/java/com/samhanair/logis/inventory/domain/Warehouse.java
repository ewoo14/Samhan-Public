package com.samhanair.logis.inventory.domain;

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
 * 창고 마스터 (plan §3.1). 4-tier 분류: 본사창고/차량재고/거래처위탁/가상창고.
 * soft-deleted via {@link SQLRestriction}; 가상창고는 이동전표 워크플로우의 IN_TRANSIT 단계를
 * 스킵 (StockTransfer 도메인 메서드 참조). {@link WarehouseType} 참고.
 */
@Entity
@Getter
@Table(name = "warehouses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Warehouse extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private WarehouseType type;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "description", length = 500)
    private String description;

    private Warehouse(String code, String name, WarehouseType type,
                      String address, int displayOrder, String description) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.address = address;
        this.displayOrder = displayOrder;
        this.description = description;
    }

    /**
     * 새 창고를 생성한다. code 는 서비스 레이어에서 중복 체크 후 호출.
     *
     * @param code 창고 코드 (서비스 레이어에서 unique 검증, 최대 50자)
     * @param name 창고 이름 (필수, 최대 100자)
     * @param type 창고 유형 (HEADQUARTERS/VEHICLE/CONSIGNMENT/VIRTUAL)
     * @param address 주소 (선택, 최대 255자)
     * @param displayOrder UI 표시 순서 (오름차순)
     * @param description 비고 (선택, 최대 500자)
     * @return 영속화 전 Warehouse 인스턴스
     */
    public static Warehouse create(String code, String name, WarehouseType type,
                                   String address, int displayOrder, String description) {
        return new Warehouse(code, name, type, address, displayOrder, description);
    }

    /**
     * 창고 이름을 변경한다.
     *
     * @param name 새 이름 (서비스 레이어에서 null 체크 후 호출)
     */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * 창고 유형을 변경한다. 가상창고로 전환되면 이후 신규 이동전표는 IN_TRANSIT 단계 스킵.
     *
     * @param type 새 유형 (HEADQUARTERS/VEHICLE/CONSIGNMENT/VIRTUAL)
     */
    public void changeType(WarehouseType type) {
        this.type = type;
    }

    /**
     * 창고 주소를 변경한다.
     *
     * @param address 새 주소 (최대 255자)
     */
    public void changeAddress(String address) {
        this.address = address;
    }

    /**
     * UI 표시 순서를 조정한다.
     *
     * @param displayOrder 새 순서값 (오름차순)
     */
    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    /**
     * 비고를 변경한다.
     *
     * @param description 새 비고 (최대 500자)
     */
    public void editDescription(String description) {
        this.description = description;
    }

    /**
     * 가상창고 여부 — 이동전표 워크플로우의 IN_TRANSIT 단계 스킵 판정에 사용.
     *
     * @return type 이 VIRTUAL 이면 true
     */
    public boolean isVirtual() {
        return this.type == WarehouseType.VIRTUAL;
    }
}
