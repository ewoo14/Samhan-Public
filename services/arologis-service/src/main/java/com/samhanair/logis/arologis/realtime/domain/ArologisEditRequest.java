package com.samhanair.logis.arologis.realtime.domain;

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
 * arologis 도메인 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@code entity_id} 의미 = Dispatch UUID (DISPATCHED/DELIVERED 후 본문 수정 채널).
 *
 * <p><b>잠금 정책</b> (사용자 명시 — D-P12-04b):
 * <ul>
 *   <li>PLANNED (모든 stop = PENDING/UNPARSED) — 작성자 자유 mutation</li>
 *   <li>DISPATCHED (어떤 stop = ARRIVED/DELIVERED) — 본 요청 채널 + MANAGER 수락</li>
 *   <li>DELIVERED (모든 stop = DELIVERED/FAILED) — 본 요청 채널 + MANAGER 수락</li>
 * </ul>
 */
@Entity
@Table(name = "arologis_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ArologisEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    public UUID getId() {
        return id;
    }

    /**
     * 신규 수정/삭제 요청 정적 factory — status PENDING.
     */
    public static ArologisEditRequest create(UUID entityId, UUID requesterId, String requesterName,
                                             EditRequestType requestType, String reason,
                                             EditTargetRole targetRole, LocalDateTime expiresAt) {
        ArologisEditRequest request = new ArologisEditRequest();
        request.init(entityId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
        return request;
    }
}
