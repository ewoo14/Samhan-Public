package com.samhanair.logis.partner.editrequest.domain;

import com.samhanair.logis.shared.realtime.editrequest.EditRequestRecord;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestType;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 거래처 도메인 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>shared:realtime-abstraction 의 {@link EditRequestRecord} @MappedSuperclass 상속 — 13 필드
 * + BaseEntity 7 audit 필드 자동 보유.
 *
 * <p>잠금 정책 (사용자 명시):
 * <ul>
 *   <li>Partner.status = ACTIVE — 자유 mutation (본 요청 채널 사용 X)</li>
 *   <li>Partner.status = SUSPENDED — 자유 (재개 가능 단계)</li>
 *   <li>Partner.status = TERMINATED — 종결 (mutation 의미 없음)</li>
 *   <li>BlockedPartner — LOCKED_REQUIRES_APPROVAL (BLOCKED 상태 잠금 — MANAGER 수락 후 1회 mutation)</li>
 * </ul>
 */
@Entity
@Getter
@Table(name = "partner_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 PENDING 요청 정적 factory — shared {@link EditRequestRecord#init} 위임.
     */
    public static PartnerEditRequest create(UUID entityId, UUID requesterId, String requesterName,
                                            EditRequestType requestType, String reason,
                                            EditTargetRole targetRole, LocalDateTime expiresAt) {
        PartnerEditRequest req = new PartnerEditRequest();
        req.init(entityId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
        return req;
    }
}
