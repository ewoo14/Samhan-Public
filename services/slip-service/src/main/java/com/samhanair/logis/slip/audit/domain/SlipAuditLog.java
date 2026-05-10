package com.samhanair.logis.slip.audit.domain;

import com.samhanair.logis.common.entity.BaseEntity;
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
 * 슬립 본문 수정 audit overlay — PR-H2 (Phase 12 Step 2).
 *
 * <p>슬립 1건의 필드별 변경(diff) 1행. 같은 트랜잭션의 다중 필드 변경은 같은 {@code revisionNo}
 * 를 공유 (예: editHeader 한 번 호출로 partnerName + memo 가 동시에 바뀌면 같은 revisionNo
 * 의 row 2개). FE timeline UI 는 {@code revisionNo} 그룹핑으로 "1번째 수정 (홍길동)" 표시.
 *
 * <p><b>UUID 비공개 가드</b> ({@code feedback_uuid_no_user_visibility}): 사용자 화면 노출 식별자
 * = {@link #actorName} 만. {@link #actorId} 는 audit/감사 추적용.
 *
 * <p><b>Soft-delete</b>: 회계 감사 / 분쟁 대응 — 본 row 는 BaseEntity.markDeleted 로만 비활성.
 * 실 DELETE 금지 (FE 가 "관리자 삭제" 표기 분기).
 *
 * <p><b>FK 미강제</b>: slip soft delete 후에도 audit row 보존. 슬립 삭제 = revert 가 아닌 별도
 * 라이프사이클.
 */
@Entity
@Getter
@Table(name = "slip_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SlipAuditLog extends BaseEntity {

    /** field_name 길이 한계 (V18 컬럼 정의 일관). */
    public static final int MAX_FIELD_NAME_LENGTH = 50;

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 소속 Slip FK ({@link com.samhanair.logis.slip.domain.Slip#getId()}) — FK 미강제. */
    @Column(name = "slip_id", nullable = false)
    private UUID slipId;

    /**
     * 슬립별 단조 증가 수정 횟수 ({@code slips.revision_count} 와 동기화).
     * 같은 mutation 의 다중 필드 변경은 같은 값을 공유.
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

    /**
     * 변경된 필드 식별자.
     *
     * <ul>
     *   <li>헤더: {@code "memo"}, {@code "shippingAddress"}, {@code "partnerName"} 등</li>
     *   <li>라인: {@code "lines[0].quantity"}, {@code "lines[2].unitPrice"} 등 (현 PR-H2 시범 한정 헤더만)</li>
     * </ul>
     */
    @Column(name = "field_name", nullable = false, length = MAX_FIELD_NAME_LENGTH)
    private String fieldName;

    /** 이전 값 (취소선 표시용). NULL = 신규 필드/라인 (이전 값 없음). */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** 새 값. NULL = 라인 삭제 등. */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /**
     * 변경 시각 — BaseEntity.createdAt 과 동일하지만 명시 보존.
     * revert 정렬 정확성 보장 + audit 인쇄 양식에서 표시.
     */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    private SlipAuditLog(UUID slipId, int revisionNo, UUID actorId, String actorName,
                         String actorColor, String fieldName, String oldValue, String newValue,
                         LocalDateTime changedAt) {
        if (slipId == null) {
            throw new IllegalArgumentException("slipId 는 필수입니다");
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
        // oldValue/newValue 는 TEXT 컬럼 — DB 길이 한계 없음. 둘 다 null 인 경우는 의미 없으므로 가드.
        if (oldValue == null && newValue == null) {
            throw new IllegalArgumentException("oldValue/newValue 둘 다 null 인 audit 은 의미가 없습니다");
        }
        this.slipId = slipId;
        this.revisionNo = revisionNo;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorColor = actorColor;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedAt = changedAt == null ? LocalDateTime.now() : changedAt;
    }

    /**
     * 신규 audit log 정적 factory.
     *
     * @param slipId 소속 Slip UUID
     * @param revisionNo 단조 증가 수정 번호 (1 이상)
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자 (≤50자)
     * @param oldValue 이전 값 (선택, null 가능)
     * @param newValue 새 값 (선택, null 가능 — old/new 둘 다 null 은 거부)
     * @return 영속화 전 신규 SlipAuditLog
     */
    public static SlipAuditLog record(UUID slipId, int revisionNo, UUID actorId, String actorName,
                                      String actorColor, String fieldName,
                                      String oldValue, String newValue) {
        return new SlipAuditLog(slipId, revisionNo, actorId, actorName, actorColor,
                fieldName, oldValue, newValue, LocalDateTime.now());
    }

    /** Soft-delete. BaseEntity.markDeleted 위임. 회계 감사용 — FE 에서만 비표시. */
    public void softDelete(String deleterUserId) {
        markDeleted(deleterUserId);
    }
}
