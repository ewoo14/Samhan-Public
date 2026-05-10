package com.samhanair.logis.slip.comment.web.dto;

import com.samhanair.logis.slip.comment.domain.SlipComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 등록 요청 — PR-H1.
 *
 * <p>authorId / authorName 은 controller 가 X-User-Id / X-User-Role 헤더 + (필요 시) user-service
 * lookup 으로 채움. 본 DTO 는 사용자 입력 본문만 담는다.
 *
 * @param body 본문 (필수, ≤{@link SlipComment#MAX_BODY_LENGTH}자)
 */
public record AddSlipCommentRequest(
        @NotBlank(message = "본문은 필수입니다")
        @Size(max = SlipComment.MAX_BODY_LENGTH, message = "본문은 최대 500자까지 허용됩니다")
        String body
) {
}
