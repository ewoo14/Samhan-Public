package com.samhanair.logis.groupware.domain;

import com.samhanair.logis.approval.ApprovalStepStatus;
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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 결재선 chain 의 단일 단계 (1명 결재자 = 1 step). sequence ASC 로 chain 순서를 결정.
 *
 * <p>{@link ApprovalLine} 의 cascade ALL + orphanRemoval 로 라이프사이클 동기.
 */
@Entity
@Getter
@Table(name = "approval_steps")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ApprovalStep extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "approval_line_id", nullable = false, updatable = false)
    private ApprovalLine approvalLine;

    /** 결재자 user UUID. */
    @Column(name = "approver_id", nullable = false, updatable = false)
    private UUID approverId;

    /** chain 순서 (0-base ASC). */
    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStepStatus status;

    /** 처리 시각 (승인 또는 반려). */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** 반려 사유 (REJECTED 인 경우만 의미). */
    @Column(name = "reason", length = 500)
    private String reason;

    private ApprovalStep(ApprovalLine line, UUID approverId, int sequence) {
        this.approvalLine = line;
        this.approverId = approverId;
        this.sequence = sequence;
        this.status = ApprovalStepStatus.PENDING;
    }

    /**
     * chain 에 신규 step 생성 — caller = {@link ApprovalLine#appendStep}.
     */
    static ApprovalStep create(ApprovalLine line, UUID approverId, int sequence) {
        return new ApprovalStep(line, approverId, sequence);
    }

    /** 본 단계 승인 처리. {@link ApprovalLine#approve(UUID)} 가 호출 흐름 보장. */
    void approve() {
        ensurePending();
        this.status = ApprovalStepStatus.APPROVED;
        this.decidedAt = LocalDateTime.now();
    }

    /** 본 단계 반려 처리. {@link ApprovalLine#reject(UUID, String)} 가 호출 흐름 보장. */
    void reject(String reason) {
        ensurePending();
        this.status = ApprovalStepStatus.REJECTED;
        this.reason = reason;
        this.decidedAt = LocalDateTime.now();
    }

    private void ensurePending() {
        if (this.status != ApprovalStepStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 결재 단계입니다: " + this.status);
        }
    }
}
