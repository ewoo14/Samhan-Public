package com.samhanair.logis.shared.realtime.audit;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도메인 audit overlay 공통 base — PR-H4a (Phase 12 Step 4a) 통합 abstraction.
 *
 * <p>14 service 가 자체 도메인 (slip / inventory-lot / dispatch / partner-order / dashboard-kpi 등)
 * 의 audit log 테이블을 만들 때 본 클래스를 {@code @MappedSuperclass} 상속 → BaseEntity 7 audit
 * 필드 + audit overlay 9 필드 자동 보유.
 *
 * <p><b>UUID 비공개 가드</b> ({@code feedback_uuid_no_user_visibility}): 사용자 화면 노출 식별자
 * = {@link #actorName} 만. {@link #actorId} 는 audit/감사 추적용.
 *
 * <p><b>Soft-delete</b>: 회계 감사 / 분쟁 대응 — 본 row 는 BaseEntity.markDeleted 로만 비활성.
 * 실 DELETE 금지 (FE 가 "관리자 삭제" 표기 분기). consumer entity 가 {@code @SQLRestriction("is_deleted = false")}
 * 명시 필수.
 *
 * <p><b>FK 미강제</b>: 도메인 entity (Slip 등) soft-delete 후에도 audit row 보존. 도메인 삭제 = revert
 * 가 아닌 별도 라이프사이클.
 *
 * <p><b>적용 예</b> ({@code SlipAuditLog}):
 * <pre>
 * &#64;Entity
 * &#64;Table(name = "slip_audit_logs")
 * &#64;SQLRestriction("is_deleted = false")
 * public class SlipAuditLog extends AuditLogEntry {
 *     // entityId = slipId 의미 — getter alias 만 추가
 *     public UUID getSlipId() { return getEntityId(); }
 * }
 * </pre>
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AuditLogEntry extends BaseEntity {

    /** field_name 길이 한계 — 모든 service 일관. */
    public static final int MAX_FIELD_NAME_LENGTH = 50;

    /** 소속 도메인 entity FK (slipId / lotId / dispatchId 등) — FK 미강제. */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * 도메인 entity 별 단조 증가 수정 횟수. 같은 mutation 의 다중 필드 변경은 같은 값을 공유.
     */
    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    /** 수정자 UUID (audit/감사 추적용 — 사용자 화면 노출 금지). */
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    /** 수정자 표시명 (사용자 화면 노출 식별자 — UUID 비공개 가드). */
    @Column(name = "actor_name", nullable = false, length = 50)
    private String actorName;

    /** FE userIdToColor 결과 backup (HSL hex, 예: "#3B82F6"). NULL 허용. */
    @Column(name = "actor_color", length = 20)
    private String actorColor;

    /** 변경된 필드 식별자 (예: "memo", "shippingAddress", "lines[0].quantity"). */
    @Column(name = "field_name", nullable = false, length = MAX_FIELD_NAME_LENGTH)
    private String fieldName;

    /** 이전 값 (취소선 표시용). NULL = 신규 필드/라인 (이전 값 없음). */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** 새 값. NULL = 라인 삭제 등. */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** 변경 시각 — BaseEntity.createdAt 과 동일하지만 명시 보존 (revert 정렬 + 인쇄 양식). */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /**
     * 공통 초기화 — 하위 entity 의 정적 factory 가 본 메서드를 호출하여 필드 값 검증/세팅.
     * BaseEntity 7 audit 필드는 JPA AuditingEntityListener 가 자동 채움.
     *
     * @param entityId 소속 도메인 entity UUID
     * @param revisionNo 단조 증가 수정 번호 (1 이상)
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자 (≤50자)
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택, old/new 둘 다 null 은 거부)
     * @param changedAt 변경 시각 (null 시 LocalDateTime.now)
     */
    protected void init(UUID entityId, int revisionNo, UUID actorId, String actorName,
                        String actorColor, String fieldName, String oldValue, String newValue,
                        LocalDateTime changedAt) {
        if (entityId == null) {
            throw new IllegalArgumentException("entityId 는 필수입니다");
        }
        if (revisionNo < 1) {
            throw new IllegalArgumentException("revisionNo 는 1 이상이어야 합니다: " + revisionNo);
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId 는 필수입니다");
        }
        if (actorName == null || actorName.isBlank()) {
            throw new IllegalArgumentException("actorName 은 필수입니다");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName 은 필수입니다");
        }
        if (fieldName.length() > MAX_FIELD_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "fieldName 은 최대 " + MAX_FIELD_NAME_LENGTH + "자입니다 (현재: "
                            + fieldName.length() + ")");
        }
        if (oldValue == null && newValue == null) {
            throw new IllegalArgumentException("oldValue/newValue 둘 다 null 인 audit 은 의미가 없습니다");
        }
        this.entityId = entityId;
        this.revisionNo = revisionNo;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorColor = actorColor;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedAt = changedAt == null ? LocalDateTime.now() : changedAt;
    }

    /** Soft-delete. BaseEntity.markDeleted 위임. 회계 감사용 — FE 에서만 비표시. */
    public void softDelete(String deleterUserId) {
        markDeleted(deleterUserId);
    }
}
