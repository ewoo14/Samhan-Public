package com.samhanair.logis.slip.dispatch.collab;

import com.samhanair.logis.collab.CollabCommentRecord;
import com.samhanair.logis.collab.CollabDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 배차 협업 댓글.
 *
 * <p>shared/collab-core 의 {@link CollabCommentRecord} 를 DispatchTask 문서에 실배선하는
 * concrete entity. 본문/작성자/스레드/상태 필드는 collab-core base 가 소유하고, 본 entity 는
 * 영속화 테이블과 UUID PK 만 제공한다.
 */
@Entity
@Table(name = "dispatch_collab_comments")
@SQLRestriction("is_deleted = false")
public class DispatchCollabComment extends CollabCommentRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    protected DispatchCollabComment() {
    }

    private DispatchCollabComment(CollabDocumentType documentType, UUID documentId, String anchor,
                                  UUID authorId, String authorName, String body, UUID parentId) {
        init(documentType, documentId, anchor, authorId, authorName, body, parentId);
    }

    @Override
    public UUID getId() {
        return id;
    }

    /**
     * 신규 배차 협업 댓글 factory.
     *
     * @param documentType 협업 문서 유형. 배차 컨트롤러는 DISPATCH_TASK 로 고정한다.
     * @param documentId DispatchTask UUID
     * @param anchor 필드/행 anchor. 없으면 문서 전체 댓글.
     * @param authorId 작성자 UUID. 화면 노출 금지, 감사 추적용.
     * @param authorName 작성자 표시명.
     * @param body 본문.
     * @param parentId 부모 댓글 ID. 없으면 최상위 댓글.
     * @return 영속화 전 신규 댓글
     */
    public static DispatchCollabComment create(CollabDocumentType documentType, UUID documentId,
                                               String anchor, UUID authorId, String authorName,
                                               String body, UUID parentId) {
        return new DispatchCollabComment(
                documentType, documentId, anchor, authorId, authorName, body, parentId);
    }
}
