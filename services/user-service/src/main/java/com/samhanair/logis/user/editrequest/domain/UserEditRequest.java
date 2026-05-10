package com.samhanair.logis.user.editrequest.domain;

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
 * user-service 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>shared:realtime-abstraction 의 {@link EditRequestRecord} {@code @MappedSuperclass} 를 상속
 * 하여 13 audit + BaseEntity 7 audit 자동 보유.
 *
 * <p><b>잠금 정책</b> ({@link com.samhanair.logis.user.lock.UserEditLockPolicy}):
 * Employee 의 {@code terminationDate != null} = DEACTIVATED → APPROVED 1회 소진 후 mutation 가능.
 * Department 는 활성/비활성 구분 없음 — 향후 정책 확장 여지.
 *
 * <p><b>target_role</b>: 사용자 task 명시 = MANAGER (인사 권한 그룹).
 */
@Entity
@Getter
@Table(name = "user_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class UserEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private UserEditRequest(UUID entityId, UUID requesterId, String requesterName,
                            EditRequestType requestType, String reason,
                            EditTargetRole targetRole, LocalDateTime expiresAt) {
        init(entityId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
    }

    /**
     * 신규 PENDING 요청 정적 factory.
     *
     * @param entityId 대상 Employee.id 또는 Department.id
     * @param requesterId 요청자 UUID
     * @param requesterName 요청자 표시명 (UUID 비공개 가드)
     * @param requestType EDIT / DELETE
     * @param reason 요청 사유 (선택, ≤500자)
     * @param targetRole 수락 권한자 그룹 (MANAGER 권장)
     * @param expiresAt 자동 만료 시각 (선택)
     * @return 영속화 전 신규 UserEditRequest (status = PENDING)
     */
    public static UserEditRequest open(UUID entityId, UUID requesterId, String requesterName,
                                       EditRequestType requestType, String reason,
                                       EditTargetRole targetRole, LocalDateTime expiresAt) {
        return new UserEditRequest(entityId, requesterId, requesterName, requestType, reason,
                targetRole, expiresAt);
    }
}
