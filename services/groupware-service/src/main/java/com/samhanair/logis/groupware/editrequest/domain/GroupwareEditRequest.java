package com.samhanair.logis.groupware.editrequest.domain;

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
 * Groupware 도메인 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b) BE-E.
 *
 * <p>{@link EditRequestRecord} (shared:realtime-abstraction @MappedSuperclass) 를 상속하여 13 필드
 * + BaseEntity 7 audit 필드 자동 보유. 본 entity 는 id 컬럼 + factory 만 추가.
 *
 * <p><b>잠금 정책</b> (slip-service PR-H3 패턴 일관):
 * <ul>
 *   <li>ApprovalLine — IN_PROGRESS/APPROVED 시 본 요청 채널 필요. WITHDRAWN/REJECTED 는 자유.</li>
 *   <li>Message — 발송 직후 잠시 자유, READ 후 본 요청 채널 필요.</li>
 *   <li>Schedule — 공유 후 본 요청 채널 필요 (참여자 통보 정합).</li>
 * </ul>
 *
 * <p>실 mutation 가드 통합 (도메인 service.editX 에서 lookup APPROVED → consume) 은 향후 PR.
 *
 * <p><b>UUID 비공개 가드</b>: requesterName / decidedByName 만 사용자 화면 노출 식별자.
 */
@Entity
@Getter
@Table(name = "groupware_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class GroupwareEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 PENDING 요청 정적 factory — shared {@link EditRequestRecord#init} 위임.
     *
     * @param entityId 소속 도메인 entity UUID
     * @param requesterId 요청자 UUID (audit/감사 추적용)
     * @param requesterName 요청자 표시명 (UUID 비공개 가드)
     * @param requestType EDIT / DELETE
     * @param reason 요청 사유 (선택, ≤500자)
     * @param targetRole 수락 권한자 그룹 (WAREHOUSE / MANAGER)
     * @param expiresAt 자동 만료 시각 (선택)
     * @return 영속화 전 신규 GroupwareEditRequest
     */
    public static GroupwareEditRequest create(UUID entityId, UUID requesterId, String requesterName,
                                              EditRequestType requestType, String reason,
                                              EditTargetRole targetRole, LocalDateTime expiresAt) {
        GroupwareEditRequest req = new GroupwareEditRequest();
        req.init(entityId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
        return req;
    }
}
