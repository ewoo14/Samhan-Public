package com.samhanair.logis.groupware.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 결재선 생성 요청 DTO.
 *
 * @param requesterId 요청자 user UUID
 * @param title 제목
 * @param content 본문 (선택)
 * @param approverIds 결재자 chain (sequence ASC, 1명 이상)
 */
public record ApprovalLineCreateRequest(
        @NotNull UUID requesterId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String content,
        @NotEmpty List<@NotNull UUID> approverIds
) {
}
