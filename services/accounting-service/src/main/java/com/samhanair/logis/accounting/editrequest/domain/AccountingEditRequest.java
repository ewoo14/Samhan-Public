package com.samhanair.logis.accounting.editrequest.domain;

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
 * 회계 도메인 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>shared:realtime-abstraction 의 {@link EditRequestRecord} @MappedSuperclass 상속 — 13 필드
 * + BaseEntity 7 audit 필드 자동 보유.
 *
 * <p>잠금 정책 (사용자 명시 — feedback_role_naming_full):
 * <ul>
 *   <li>TaxInvoice DRAFT — 자유 mutation (본 요청 채널 사용 X)</li>
 *   <li>TaxInvoice ISSUED — 본 요청 채널 + MANAGER 수락 시 1회 mutation 가능 (취소 / 재발행)</li>
 *   <li>TaxInvoice CANCELLED — 종결 (mutation 의미 없음)</li>
 *   <li>Journal DRAFT — 자유</li>
 *   <li>Journal POSTED — 본 요청 채널 + MANAGER 수락 시 1회 (역분개 후 신규 입력 권장)</li>
 *   <li>Journal REVERSED — 종결</li>
 *   <li>AccountingPeriod OPEN — 자유</li>
 *   <li>AccountingPeriod CLOSED — 본 요청 채널 + MANAGER 수락 시 1회 (역마감 채널)</li>
 * </ul>
 */
@Entity
@Getter
@Table(name = "accounting_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class AccountingEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 PENDING 요청 정적 factory — shared {@link EditRequestRecord#init} 위임.
     *
     * @param entityId 대상 entity (TaxInvoice / Journal / AccountingPeriod) UUID
     * @param requesterId 요청자 UUID
     * @param requesterName 요청자 표시명 (UUID 비공개 가드)
     * @param requestType EDIT / DELETE
     * @param reason 요청 사유 (선택, ≤500자)
     * @param targetRole 수락 권한자 그룹 (회계는 MANAGER 우선)
     * @param expiresAt 자동 만료 시각 (선택)
     * @return 영속화 전 신규 AccountingEditRequest
     */
    public static AccountingEditRequest create(UUID entityId, UUID requesterId,
                                               String requesterName, EditRequestType requestType,
                                               String reason, EditTargetRole targetRole,
                                               LocalDateTime expiresAt) {
        AccountingEditRequest req = new AccountingEditRequest();
        req.init(entityId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
        return req;
    }
}
