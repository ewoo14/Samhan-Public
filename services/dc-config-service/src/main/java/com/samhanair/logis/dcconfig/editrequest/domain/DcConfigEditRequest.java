package com.samhanair.logis.dcconfig.editrequest.domain;

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
 * dc-config-service 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b).
 *
 * <p>shared module 의 {@link EditRequestRecord} {@code @MappedSuperclass} 상속.
 *
 * <p><b>잠금 정책</b> ({@link com.samhanair.logis.dcconfig.lock.DcConfigEditLockPolicy}):
 * 정책 적용 후 변경 신중을 위한 채널 — APPROVED 1건 소진 후 mutation 가능.
 *
 * <p><b>target_role</b>: 사용자 task 명시 = MANAGER (DC 정책 권한 그룹).
 */
@Entity
@Getter
@Table(name = "dc_config_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class DcConfigEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private DcConfigEditRequest(UUID entityId, UUID requesterId, String requesterName,
                                EditRequestType requestType, String reason,
                                EditTargetRole targetRole, LocalDateTime expiresAt) {
        init(entityId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
    }

    /** 신규 PENDING 요청 정적 factory. */
    public static DcConfigEditRequest open(UUID entityId, UUID requesterId, String requesterName,
                                           EditRequestType requestType, String reason,
                                           EditTargetRole targetRole, LocalDateTime expiresAt) {
        return new DcConfigEditRequest(entityId, requesterId, requesterName, requestType, reason,
                targetRole, expiresAt);
    }
}
