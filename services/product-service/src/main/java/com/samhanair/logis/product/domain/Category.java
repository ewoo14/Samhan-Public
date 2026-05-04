package com.samhanair.logis.product.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 제품 카테고리 — 단일 부모 자기참조 트리 (개발책임자 결재). 깊이 무제한,
 * 강제 검사는 운영 정책으로 처리한다 (코드 강제 X). soft-deleted via {@link SQLRestriction}.
 */
@Entity
@Getter
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Category extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private Category(String code, String name, Category parent, int displayOrder) {
        this.code = code;
        this.name = name;
        this.parent = parent;
        this.displayOrder = displayOrder;
    }

    public static Category create(String code, String name, Category parent, int displayOrder) {
        return new Category(code, name, parent, displayOrder);
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeParent(Category parent) {
        this.parent = parent;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
