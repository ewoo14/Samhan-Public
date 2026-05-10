package com.samhanair.logis.partnerorder.editrequest.domain;

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
 * 거래처 주문 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b BE-C).
 *
 * <p>{@link EditRequestRecord} ({@code shared:realtime-abstraction}) 의 {@code @MappedSuperclass}
 * 상속으로 entity_id / requester_* / decided_* / status / target_role / requested_at / expires_at
 * + BaseEntity 7 자동 보유.
 *
 * <p>사용자 명시 잠금 정책 (개발책임자 결정 — 거래처 주문 도메인):
 * <ul>
 *   <li>DRAFT/CONFIRMING — 작성자 자유 mutation (본 도메인 사용 X).</li>
 *   <li>CONFIRMED (slip 발행 후) — 작성자 직접 차단 → 본 channel 요청 → MANAGER 수락 시 1회 가능.</li>
 *   <li>CANCELED — 종결됨, 요청 의미 없음.</li>
 * </ul>
 */
@Entity
@Getter
@Table(name = "partner_order_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class PartnerOrderEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 PENDING 요청 정적 factory.
     *
     * @param partnerOrderId 소속 PartnerOrder UUID (entity_id 컬럼)
     * @param requesterId 요청자 UUID
     * @param requesterName 요청자 표시명 (UUID 비공개 가드)
     * @param requestType EDIT / DELETE
     * @param reason 요청 사유 (선택, ≤500자)
     * @param targetRole 수락 권한자 그룹 (거래처 주문 default = MANAGER)
     * @param expiresAt 자동 만료 시각 (선택)
     * @return 영속화 전 신규 PartnerOrderEditRequest
     */
    public static PartnerOrderEditRequest create(UUID partnerOrderId, UUID requesterId,
                                                 String requesterName, EditRequestType requestType,
                                                 String reason, EditTargetRole targetRole,
                                                 LocalDateTime expiresAt) {
        PartnerOrderEditRequest row = new PartnerOrderEditRequest();
        row.init(partnerOrderId, requesterId, requesterName, requestType, reason, targetRole,
                expiresAt);
        return row;
    }

    /** entity_id alias — partnerOrderId 의 의미 명시. */
    public UUID getPartnerOrderId() {
        return getEntityId();
    }
}
