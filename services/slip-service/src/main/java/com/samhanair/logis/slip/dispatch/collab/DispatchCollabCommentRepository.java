package com.samhanair.logis.slip.dispatch.collab;

import com.samhanair.logis.collab.CollabCommentService;
import com.samhanair.logis.collab.CollabDocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 배차 협업 댓글 repository.
 *
 * <p>{@link CollabCommentService.CommentRepository} adapter 는 {@link DispatchCollabConfig} 에서
 * 본 JPA repository 로 위임한다. Spring Data generic save 시그니처와 adapter 시그니처 충돌을 피하기
 * 위한 명시적 분리다.
 */
public interface DispatchCollabCommentRepository extends JpaRepository<DispatchCollabComment, UUID> {

    /** 댓글 mutation 은 commentId 단독 조회 금지 — path task 와 같은 문서에 속한 댓글만 반환한다. */
    Optional<DispatchCollabComment> findByIdAndDocumentTypeAndDocumentId(
            UUID id, CollabDocumentType documentType, UUID documentId);

    /** DispatchTask 별 최근 댓글 백필. {@code @SQLRestriction} 으로 soft-deleted row 는 제외된다. */
    @Query("""
            select c
            from DispatchCollabComment c
            where c.documentType = :documentType
              and c.documentId = :documentId
            order by c.createdAt desc
            """)
    List<DispatchCollabComment> findRecent(
            @Param("documentType") CollabDocumentType documentType,
            @Param("documentId") UUID documentId,
            Pageable pageable);
}
