package com.samhanair.logis.user.domain;

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
 * 사원 서명 등록/무효화 감사 이력 - C1a (slip slip_signature_audit 미러).
 *
 * <p>전자서명 무결성 입증을 위해 별도 테이블로 분리. RECORD/INVALIDATE 2종 action 만 적재.
 * Slip 패턴과 동일하게 entity 는 정적 factory 만 제공하며, 도메인 mutation 직후 서비스 레이어가
 * repository 로 영속한다(직접 INSERT).
 */
@Entity
@Getter
@Table(name = "employee_signature_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class EmployeeSignatureAudit extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private SignatureAuditAction action;

    @Column(name = "signature_hash", length = 64)
    private String signatureHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_channel", length = 20)
    private SignatureChannel signatureChannel;

    @Column(name = "reason", length = 500)
    private String reason;

    /** 처리자 user-id - 모바일 RECORD 시 NULL 가능(인증 없는 공개 경로), 관리자 작업 시 X-User-Id. */
    @Column(name = "actor_user_id", length = 50)
    private String actorUserId;

    private EmployeeSignatureAudit(UUID employeeId, SignatureAuditAction action, String signatureHash,
                                   SignatureChannel signatureChannel, String reason, String actorUserId) {
        this.employeeId = employeeId;
        this.action = action;
        this.signatureHash = signatureHash;
        this.signatureChannel = signatureChannel;
        this.reason = reason;
        this.actorUserId = actorUserId;
    }

    /**
     * RECORD 이력 생성 - 서명 신규/재등록 시 적재.
     *
     * @param employeeId 대상 사원 UUID (필수)
     * @param signatureHash SHA-256 hex 64자 (필수)
     * @param channel 입력 채널 (필수)
     * @param actorUserId 처리자 user-id (모바일 공개 경로는 NULL 가능)
     */
    public static EmployeeSignatureAudit record(UUID employeeId, String signatureHash,
                                                SignatureChannel channel, String actorUserId) {
        return new EmployeeSignatureAudit(employeeId, SignatureAuditAction.RECORD,
                signatureHash, channel, null, actorUserId);
    }

    /**
     * INVALIDATE 이력 생성 - 관리자(MASTER) 무효화 시 적재.
     *
     * @param employeeId 대상 사원 UUID (필수)
     * @param signatureHash 직전 SHA-256 hex 64자 snapshot
     * @param channel 직전 채널 snapshot
     * @param reason 무효화 사유 (필수, 500자 이하)
     * @param actorUserId 처리자 user-id (필수)
     */
    public static EmployeeSignatureAudit invalidate(UUID employeeId, String signatureHash,
                                                    SignatureChannel channel, String reason,
                                                    String actorUserId) {
        return new EmployeeSignatureAudit(employeeId, SignatureAuditAction.INVALIDATE,
                signatureHash, channel, reason, actorUserId);
    }
}
