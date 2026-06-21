package com.samhanair.logis.approval;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결재 chain 단계의 공통 베이스(전 전표 공용). 컬럼과 단계 전이 로직만 보유하고,
 * 부모 결재선으로의 {@code @ManyToOne} 역참조·{@code @Id} 는 소비 서비스 concrete @Entity 가 소유한다
 * (Hibernate 가 @MappedSuperclass 의 per-service 관계 타입을 매핑하지 못하므로).
 *
 * <p>결재자 식별은 {@link StepType} 으로 분기한다 — A1 은 {@link StepType#USER}(approverUserId)만
 * 실배선하고, GROUP(approverGroupId/requiredPageCode)·CREATOR 는 컬럼만 nullable 로 선반영한다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class ApprovalStepBase extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", length = 20)
    private StepType stepType;

    /** USER 모드 결재자 사원 UUID. 기존 컬럼 {@code approver_id} 에 매핑(컬럼명 불변). */
    @Column(name = "approver_id")
    private UUID approverUserId;

    /** GROUP 모드 권한 그룹 UUID(표시·설정용, A2). */
    @Column(name = "approver_group_id")
    private UUID approverGroupId;

    /** GROUP 모드 결재 권한 page-code(enforce 용, A2). */
    @Column(name = "required_page_code", length = 100)
    private String requiredPageCode;

    /** 실제 승인 처리자 user UUID — approve 시 기록. */
    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    /** chain 순서(0-base ASC). */
    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovalStepStatus status;

    /** 처리 시각(승인/반려). */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** 반려 사유(REJECTED 인 경우만 의미). */
    @Column(name = "reason", length = 500)
    private String reason;

    /** 결재 시점 동결 서명 PNG(A3 에서 채움). list 조회 부하 회피 위해 LAZY. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "signature_png_snapshot")
    private byte[] signaturePngSnapshot;

    /** 서명 동결 시각(A3). */
    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** USER 모드 단계 초기화 — concrete create 가 호출. */
    protected void initUserStep(UUID approverUserId, int sequence) {
        if (approverUserId == null) {
            throw new IllegalArgumentException("approverUserId 필수");
        }
        this.stepType = StepType.USER;
        this.approverUserId = approverUserId;
        this.sequence = sequence;
        this.status = ApprovalStepStatus.PENDING;
    }

    /** 액터가 본 단계의 결재 권한자인지(A1=USER 모드 동일성). GROUP/CREATOR 는 A2/A4. */
    boolean matchesActor(UUID actorUserId) {
        return this.stepType == StepType.USER
                && this.approverUserId != null
                && this.approverUserId.equals(actorUserId);
    }

    /** 본 단계 승인. 호출 흐름은 {@link ApprovalLineBase#approve(UUID)} 가 보장. */
    void approve(UUID actorUserId) {
        ensurePending();
        this.status = ApprovalStepStatus.APPROVED;
        this.approvedByUserId = actorUserId;
        this.decidedAt = LocalDateTime.now();
    }

    /** 본 단계 반려. */
    void reject(UUID actorUserId, String reason) {
        ensurePending();
        this.status = ApprovalStepStatus.REJECTED;
        this.approvedByUserId = actorUserId;
        this.reason = reason;
        this.decidedAt = LocalDateTime.now();
    }

    private void ensurePending() {
        if (this.status != ApprovalStepStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 결재 단계입니다: " + this.status);
        }
    }
}
