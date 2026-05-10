package com.samhanair.logis.product.editrequest.domain;

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
 * 제품 마스터 수정/삭제 요청 — PR-H4b (Phase 12 Step 4b BE-C).
 *
 * <p>{@link EditRequestRecord} ({@code shared:realtime-abstraction}) 의 {@code @MappedSuperclass}
 * 상속.
 *
 * <p>사용자 명시 잠금 정책 (개발책임자 결정 — 제품 마스터 도메인):
 * <ul>
 *   <li>ACTIVE — 자유 mutation (본 도메인 사용 X — admin 직접 가능).</li>
 *   <li>DISCONTINUED (단종 처리 후) — 작성자 직접 차단 → 본 channel 요청 → MANAGER 수락 시 1회 가능.</li>
 * </ul>
 */
@Entity
@Getter
@Table(name = "product_edit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class ProductEditRequest extends EditRequestRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 신규 PENDING 요청 정적 factory.
     */
    public static ProductEditRequest create(UUID productId, UUID requesterId, String requesterName,
                                            EditRequestType requestType, String reason,
                                            EditTargetRole targetRole, LocalDateTime expiresAt) {
        ProductEditRequest row = new ProductEditRequest();
        row.init(productId, requesterId, requesterName, requestType, reason, targetRole, expiresAt);
        return row;
    }

    /** entity_id alias — productId 의 의미 명시. */
    public UUID getProductId() {
        return getEntityId();
    }
}
