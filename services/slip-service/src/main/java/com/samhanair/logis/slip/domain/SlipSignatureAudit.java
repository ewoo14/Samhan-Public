package com.samhanair.logis.slip.domain;

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
 * 전자서명 감사 이력 — Slice C (signature-slice-C Plan §3.1).
 *
 * <p>전자서명법 시행령 §17 무결성 입증 의무로 별도 테이블로 분리. RECORD/INVALIDATE 2종 action 만
 * 적재되며, hash + signerName + reason + actorUserId 4 컬럼이 핵심.
 *
 * <p>적재 규칙 (Layer 4):
 * <ul>
 *   <li>{@link Slip#recordSignature} — RECORD 행 1건 INSERT (actorUserId=null, 공개 endpoint 시)</li>
 *   <li>{@link Slip#invalidateSignature} — INVALIDATE 행 1건 INSERT (actorUserId=관리자 user-id)</li>
 * </ul>
 *
 * <p>본 entity 자체는 도메인 메서드를 갖지 않고 정적 factory 만 제공 — Slip 도메인 메서드가
 * 직접 인스턴스를 반환하면 service 레이어가 repository 로 영속.
 */
@Entity
@Getter
@Table(name = "slip_signature_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SlipSignatureAudit extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "slip_id", nullable = false)
    private UUID slipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private SignatureAuditAction action;

    @Column(name = "signer_name", length = 50)
    private String signerName;

    @Column(name = "signature_hash", length = 64)
    private String signatureHash;

    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * 무효화/등록 actor user-id — 공개 mobile RECORD 시 NULL (인증 없음),
     * 관리자 INVALIDATE 시 호출자 X-User-Id.
     */
    @Column(name = "actor_user_id", length = 50)
    private String actorUserId;

    private SlipSignatureAudit(UUID slipId, SignatureAuditAction action,
                               String signerName, String signatureHash,
                               String reason, String actorUserId) {
        this.slipId = slipId;
        this.action = action;
        this.signerName = signerName;
        this.signatureHash = signatureHash;
        this.reason = reason;
        this.actorUserId = actorUserId;
    }

    /**
     * RECORD 이력 생성 — 모바일 공개 서명 endpoint 호출 결과로 적재.
     *
     * @param slipId 슬립 UUID (필수)
     * @param signerName 인수자명 (필수)
     * @param signatureHash SHA-256 hex 64자 (필수)
     * @return 신규 RECORD 이력 (id 는 save 후 채번)
     */
    public static SlipSignatureAudit record(UUID slipId, String signerName, String signatureHash) {
        return new SlipSignatureAudit(slipId, SignatureAuditAction.RECORD,
                signerName, signatureHash, null, null);
    }

    /**
     * INVALIDATE 이력 생성 — 관리자(MASTER) 무효화 시 적재.
     *
     * @param slipId 슬립 UUID (필수)
     * @param signerName 직전 서명자명 snapshot
     * @param signatureHash 직전 SHA-256 hex 64자 snapshot
     * @param reason 무효화 사유 (필수, ≤500자)
     * @param actorUserId 무효화 처리자 user-id (필수)
     * @return 신규 INVALIDATE 이력
     */
    public static SlipSignatureAudit invalidate(UUID slipId, String signerName, String signatureHash,
                                                String reason, String actorUserId) {
        return new SlipSignatureAudit(slipId, SignatureAuditAction.INVALIDATE,
                signerName, signatureHash, reason, actorUserId);
    }
}
