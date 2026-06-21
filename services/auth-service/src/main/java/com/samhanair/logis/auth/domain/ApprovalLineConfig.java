package com.samhanair.logis.auth.domain;

import com.samhanair.logis.approval.StepType;
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
 * 전표 종류별 결재 역할 1건(선언적 카탈로그). 결재라인 설정 메뉴가 역할에 권한 그룹/필수여부를 지정한다.
 *
 * <p>enforcement(게이트/명시 결재)는 본 config 를 소비하는 슬라이스(A2-2 등)가 수행한다. 본 엔티티는
 * {@code group_page_permissions} 를 건드리지 않는 선언적 정의만 보관한다(권한그룹 관리와 진실원 분리).
 */
@Entity
@Getter
@Table(name = "approval_line_config")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ApprovalLineConfig extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 전표 종류 — CollabDocumentType name (SLIP_OUTBOUND 등). */
    @Column(name = "document_type", nullable = false, updatable = false, length = 40)
    private String documentType;

    /** 역할 순서(0-base). */
    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    /** 역할 표시 명칭(작성자/출고인/검수인). */
    @Column(name = "label", nullable = false, length = 50)
    private String label;

    /** 결재자 식별 방식(CREATOR=전표 작성자 자동 / GROUP=권한 그룹 / USER). */
    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, updatable = false, length = 20)
    private StepType stepType;

    /** GROUP 역할의 지정 권한 그룹(nullable — 미지정 또는 CREATOR). */
    @Column(name = "approver_group_id")
    private UUID approverGroupId;

    /** 결재 필수여부(E11). */
    @Column(name = "required", nullable = false)
    private boolean required;

    /** GROUP 역할에 권한 그룹 지정. CREATOR 역할은 거부. */
    public void assignGroup(UUID groupId) {
        if (this.stepType != StepType.GROUP) {
            throw new IllegalStateException("권한 그룹은 GROUP 역할에만 지정할 수 있습니다: " + this.label);
        }
        this.approverGroupId = groupId;
    }

    /** 권한 그룹 해제. */
    public void clearGroup() {
        this.approverGroupId = null;
    }

    /** 필수여부 변경. */
    public void changeRequired(boolean required) {
        this.required = required;
    }
}
