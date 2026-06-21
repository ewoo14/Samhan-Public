package com.samhanair.logis.auth.domain;

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

/** 결재 역할에 연결된 결재자 1건. 역할 1개는 그룹/개인 결재자를 N개 보유할 수 있다. */
@Entity
@Getter
@Table(name = "approval_line_approver")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ApprovalLineApprover extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 부모 결재 역할 ID(approval_line_config.id). */
    @Column(name = "config_role_id", nullable = false, updatable = false)
    private UUID configRoleId;

    /** 결재자 유형 — GROUP=권한그룹, USER=개인 계정. */
    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, updatable = false, length = 10)
    private ApproverType approverType;

    /** GROUP이면 permission_groups.id, USER이면 accounts.id. */
    @Column(name = "approver_ref_id", nullable = false, updatable = false)
    private UUID approverRefId;

    /** 결재자 생성 팩토리. */
    public static ApprovalLineApprover create(UUID configRoleId, ApproverType approverType, UUID approverRefId) {
        ApprovalLineApprover approver = new ApprovalLineApprover();
        approver.configRoleId = configRoleId;
        approver.approverType = approverType;
        approver.approverRefId = approverRefId;
        return approver;
    }
}
