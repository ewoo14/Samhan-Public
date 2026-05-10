package com.samhanair.logis.slip.editrequest.web.dto;

import com.samhanair.logis.slip.editrequest.domain.SlipEditRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 슬립 수정/삭제 요청 생성 DTO — PR-H3.
 *
 * @param type EDIT / DELETE (필수)
 * @param reason 요청 사유 (선택, ≤500자)
 */
public record CreateEditRequestRequest(
        @NotNull SlipEditRequestType type,
        @Size(max = 500) String reason
) {
}
