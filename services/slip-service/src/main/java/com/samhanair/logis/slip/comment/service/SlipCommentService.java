package com.samhanair.logis.slip.comment.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.comment.domain.SlipComment;
import com.samhanair.logis.slip.comment.repository.SlipCommentRepository;
import com.samhanair.logis.slip.realtime.SlipRealtimeBroker;
import com.samhanair.logis.slip.repository.SlipRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슬립 댓글 라이프사이클 — PR-H1 (Phase 12 Step 1) SSE realtime smoke.
 *
 * <p>책임 경계:
 * <ul>
 *   <li>{@link #add} — 댓글 INSERT 후 {@link SlipRealtimeBroker#publish} 로 SSE event push</li>
 *   <li>{@link #listRecent} — 최근 N건 백필 (SSE 구독 직전 클라이언트 표시)</li>
 *   <li>{@link #softDelete} — soft-delete + 동일 broker push (event {@code comment.deleted})</li>
 * </ul>
 *
 * <p>가드:
 * <ul>
 *   <li>slipId 미존재 → 404 NOT_FOUND</li>
 *   <li>body 길이/필수 검증 = entity factory 가 가드</li>
 * </ul>
 *
 * <p><b>UUID 비공개</b>: SSE payload + listRecent 응답은 {@code authorName} 노출.
 * {@code authorId} 는 audit/감사용 (controller 응답 DTO 에서 노출 정책 결정).
 */
@Service
@RequiredArgsConstructor
public class SlipCommentService {

    /** 최근 백필 기본 limit. */
    public static final int DEFAULT_RECENT_LIMIT = 20;

    /** 최대 limit (DOS 가드). */
    public static final int MAX_RECENT_LIMIT = 100;

    /** SSE event name — 신규 댓글. */
    public static final String EVENT_COMMENT_CREATED = "comment.created";

    /** SSE event name — 댓글 soft-delete. */
    public static final String EVENT_COMMENT_DELETED = "comment.deleted";

    private final SlipCommentRepository commentRepository;
    private final SlipRepository slipRepository;
    private final SlipRealtimeBroker broker;

    /**
     * 신규 댓글 등록 + SSE push.
     *
     * @param slipId 소속 Slip UUID
     * @param authorId 작성자 UUID
     * @param authorName 작성자 표시명 (UUID 비공개 가드)
     * @param body 본문 (≤500자)
     * @return 영속화된 SlipComment
     */
    @Transactional
    public SlipComment add(UUID slipId, UUID authorId, String authorName, String body) {
        if (!slipRepository.existsById(slipId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "슬립을 찾을 수 없습니다: " + slipId);
        }
        SlipComment saved = commentRepository.save(
                SlipComment.create(slipId, authorId, authorName, body));

        // SSE push — 동일 트랜잭션 내, broker 는 in-memory 라 commit 전 push 도 무방
        broker.publish(slipId, EVENT_COMMENT_CREATED, Map.of(
                "id", saved.getId().toString(),
                "slipId", slipId.toString(),
                "authorName", saved.getAuthorName(),
                "body", saved.getBody(),
                "createdAt", String.valueOf(saved.getCreatedAt())
        ));
        return saved;
    }

    /**
     * 최근 N건 백필 — SSE 구독 직전 클라이언트 초기 표시용. 최근순 정렬.
     *
     * @param slipId 대상 슬립
     * @param limit 1~{@value #MAX_RECENT_LIMIT}
     * @return 최근순 (createdAt desc) 댓글 목록
     */
    @Transactional(readOnly = true)
    public List<SlipComment> listRecent(UUID slipId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_RECENT_LIMIT));
        return commentRepository.findBySlipIdOrderByCreatedAtDesc(
                slipId, PageRequest.of(0, safeLimit));
    }

    /**
     * 댓글 soft-delete + SSE push.
     *
     * @param commentId 댓글 UUID
     * @param deleterUserId 삭제자 user-id (audit)
     */
    @Transactional
    public void softDelete(UUID commentId, String deleterUserId) {
        SlipComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "댓글을 찾을 수 없습니다: " + commentId));
        comment.softDelete(deleterUserId == null || deleterUserId.isBlank()
                ? "system" : deleterUserId);
        broker.publish(comment.getSlipId(), EVENT_COMMENT_DELETED, Map.of(
                "id", comment.getId().toString(),
                "slipId", comment.getSlipId().toString()
        ));
    }
}
