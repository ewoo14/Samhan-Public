package com.samhanair.logis.collab;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문서 댓글 공통 base.
 *
 * <p>소비 service 는 본 클래스를 상속한 concrete entity 에서 @Entity/@Table/@SQLRestriction 을
 * 선언한다. UUID 화면 노출 금지 원칙에 따라 사용자 표시에는 authorName 만 사용한다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class CollabCommentRecord extends BaseEntity {

    /** 하위 entity 의 UUID PK. 이벤트 payload 와 서비스 조회에 사용한다. */
    public abstract UUID getId();

    /** 본문 최대 길이. */
    public static final int MAX_BODY_LENGTH = 500;

    /** 작성자 표시명 최대 길이. */
    public static final int MAX_AUTHOR_NAME_LENGTH = 50;

    /** 필드/행 anchor 최대 길이. */
    public static final int MAX_ANCHOR_LENGTH = 120;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private CollabDocumentType documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** 필드/행 anchor. NULL 이면 문서 전체 댓글이다. */
    @Column(name = "anchor", length = MAX_ANCHOR_LENGTH)
    private String anchor;

    /** 작성자 UUID. 감사 추적용이며 사용자 화면 직접 노출 금지. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** 작성자 표시명. */
    @Column(name = "author_name", nullable = false, length = MAX_AUTHOR_NAME_LENGTH)
    private String authorName;

    @Column(name = "body", nullable = false, length = MAX_BODY_LENGTH)
    private String body;

    /** 스레드 부모 댓글 ID. NULL 이면 최상위 댓글이다. */
    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CollabCommentStatus status = CollabCommentStatus.OPEN;

    /** 하위 entity factory 가 호출하는 공통 초기화. */
    protected void init(CollabDocumentType documentType, UUID documentId, String anchor,
                        UUID authorId, String authorName, String body, UUID parentId) {
        if (documentType == null) {
            throw new IllegalArgumentException("documentType 은 필수입니다");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId 는 필수입니다");
        }
        if (authorId == null) {
            throw new IllegalArgumentException("authorId 는 필수입니다");
        }
        if (authorName == null || authorName.isBlank()) {
            throw new IllegalArgumentException("authorName 은 필수입니다");
        }
        if (authorName.length() > MAX_AUTHOR_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "authorName 은 최대 " + MAX_AUTHOR_NAME_LENGTH + "자까지 허용됩니다 (현재: "
                            + authorName.length() + ")");
        }
        if (anchor != null && anchor.length() > MAX_ANCHOR_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "anchor 는 최대 " + MAX_ANCHOR_LENGTH + "자까지 허용됩니다 (현재: "
                            + anchor.length() + ")");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body 는 필수입니다");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "body 는 최대 " + MAX_BODY_LENGTH + "자까지 허용됩니다 (현재: "
                            + body.length() + ")");
        }
        this.documentType = documentType;
        this.documentId = documentId;
        this.anchor = anchor;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
        this.parentId = parentId;
        this.status = CollabCommentStatus.OPEN;
    }

    /** 댓글 해결 처리. */
    public void resolve() {
        this.status = CollabCommentStatus.RESOLVED;
    }

    /** Soft-delete. BaseEntity.markDeleted 위임. */
    public void softDelete(String deleterUserId) {
        markDeleted(deleterUserId == null || deleterUserId.isBlank() ? "system" : deleterUserId);
    }
}
