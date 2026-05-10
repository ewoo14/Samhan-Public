package com.samhanair.logis.slip.comment.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 슬립 댓글 — PR-H1 (Phase 12 Step 1) SSE realtime smoke 도메인.
 *
 * <p>Phase 12 의 첫 실시간 협업 도메인. 슬립 1건당 N 댓글 (FK 미강제) — sales/창고원/검수자가
 * 진행 메모 / 검수 의견 / 인계 코멘트를 주고받는다. 본 row 가 INSERT 되면
 * {@link com.samhanair.logis.slip.realtime.SlipRealtimeBroker} 가 SSE event {@code comment.created}
 * 로 해당 슬립 구독자에게 push.
 *
 * <p><b>UUID 비공개 가드</b> ({@code feedback_uuid_no_user_visibility}): 사용자 화면 노출
 * 식별자는 {@link #authorName} 만. {@link #authorId} (UUID) 는 audit/감사 추적용.
 *
 * <p><b>Soft-delete</b>: {@code @SQLRestriction("is_deleted = false")} + BaseEntity.markDeleted.
 * 회계 감사 / 분쟁 대응 위해 row 자체는 보존.
 *
 * <p><b>본문 길이</b>: 최대 500자 (단순 텍스트 메모). 이미지/파일은 slip_attachments 별도.
 */
@Entity
@Getter
@Table(name = "slip_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class SlipComment extends BaseEntity {

    /** 본문 최대 길이 (V17 컬럼 정의 일관). */
    public static final int MAX_BODY_LENGTH = 500;

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 소속 Slip FK ({@link com.samhanair.logis.slip.domain.Slip#getId()}) — FK 미강제. */
    @Column(name = "slip_id", nullable = false)
    private UUID slipId;

    /** 작성자 UUID (audit/감사 추적용 — 사용자 화면 노출 금지). */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** 작성자 표시명 (사용자 화면 노출 식별자 — UUID 비공개 가드). */
    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    /** 본문 (≤500자, 단순 텍스트). */
    @Column(name = "body", nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    private SlipComment(UUID slipId, UUID authorId, String authorName, String body) {
        if (slipId == null) {
            throw new IllegalArgumentException("slipId 는 필수입니다");
        }
        if (authorId == null) {
            throw new IllegalArgumentException("authorId 는 필수입니다");
        }
        if (authorName == null || authorName.isBlank()) {
            throw new IllegalArgumentException("authorName 은 필수입니다");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body 는 필수입니다");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "body 는 최대 " + MAX_BODY_LENGTH + "자까지 허용됩니다 (현재: " + body.length() + ")");
        }
        this.slipId = slipId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
    }

    /**
     * 신규 댓글 등록 정적 factory.
     *
     * @param slipId 소속 Slip UUID
     * @param authorId 작성자 UUID
     * @param authorName 작성자 표시명 (UUID 비공개 가드)
     * @param body 본문 (≤500자)
     * @return 영속화 전 신규 SlipComment
     */
    public static SlipComment create(UUID slipId, UUID authorId, String authorName, String body) {
        return new SlipComment(slipId, authorId, authorName, body);
    }

    /** Soft-delete. BaseEntity.markDeleted 위임. */
    public void softDelete(String deleterUserId) {
        markDeleted(deleterUserId);
    }
}
