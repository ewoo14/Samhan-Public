package com.samhanair.logis.slip.comment.repository;

import com.samhanair.logis.slip.comment.domain.SlipComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 슬립 댓글 — slipId 기반 조회. soft-delete 자동 제외 ({@code @SQLRestriction}).
 *
 * <p>최근 N건 백필 (SSE 구독 직전 표시) 용 {@link Pageable} 기반 메서드 제공.
 */
public interface SlipCommentRepository extends JpaRepository<SlipComment, UUID> {

    /** 슬립별 댓글 — 최근순 (createdAt desc). Pageable 로 limit 제어. */
    List<SlipComment> findBySlipIdOrderByCreatedAtDesc(UUID slipId, Pageable pageable);

    /** 슬립별 댓글 — 시간순 (createdAt asc), full list. 분쟁 추적/감사용. */
    List<SlipComment> findBySlipIdOrderByCreatedAtAsc(UUID slipId);
}
