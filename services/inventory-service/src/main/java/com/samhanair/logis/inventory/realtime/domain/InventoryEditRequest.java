package com.samhanair.logis.inventory.realtime.domain;

import com.samhanair.logis.shared.realtime.editrequest.EditRequestRecord;
import com.samhanair.logis.shared.realtime.editrequest.EditRequestType;
import com.samhanair.logis.shared.realtime.editrequest.EditTargetRole;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * inventory 도메인 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link EditRequestRecord} ({@code shared:realtime-abstraction} @MappedSuperclass) 상속.
 *
 * <p>{@code entity_id} 의미 = InventoryAudit UUID (회계 감사 대상 → COMPLETED 후 본문 수정 채널).
 *
 * <p><b>잠금 정책</b> (사용자 명시 — D-P12-04b):
 * <ul>
 *   <li>InventoryAudit PLANNED / IN_PROGRESS — 작성자 자유 mutation (요청 불필요)</li>
 *   <li>InventoryAudit COMPLETED — 본 요청 채널 + MANAGER 수락 1회 소진 후 mutation</li>
 *   <li>InventoryAudit CANCELLED — 종결 (요청 의미 없음)</li>
 * </ul>
 */
@Entity
@Table(name = "inventory_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class InventoryEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    public UUID getId() {
        return id;
    }

    /**
     * 신규 수정/삭제 요청 정적 factory — status PENDING.
     *
     * @param entityId InventoryAudit UUID (요청 대상)
     * @param requesterId 요청자 UUID
     * @param requesterName 요청자 표시명 (UUID 비공개 가드)
     * @param requestType EDIT / DELETE
     * @param reason 요청 사유 (선택, ≤500자)
     * @param targetRole 수락 권한자 그룹 (default MANAGER)
     * @param expiresAt 자동 만료 시각 (default null = 만료 없음)
     * @return 영속화 전 신규 InventoryEditRequest (status=PENDING)
     */
    public static InventoryEditRequest create(UUID entityId, UUID requesterId, String requesterName,
                                              EditRequestType requestType, String reason,
                                              EditTargetRole targetRole, LocalDateTime expiresAt) {
        InventoryEditRequest request = new InventoryEditRequest();
        request.init(entityId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
        return request;
    }
}
