package com.samhanair.logis.auth.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * 결재라인 역할 순서 변경 요청 DTO.
 *
 * <p>{@code orderedIds} 는 해당 전표 종류(documentType)의 활성 역할 전체를 순서대로 전달해야 한다.
 * 누락/잉여/타 documentType 혼입 시 서비스 레이어에서 거부(부분요청 가드).
 *
 * @param orderedIds 변경 후 순서에 따른 역할 UUID 목록(첫 번째=작성자 고정)
 */
public record ReorderApprovalLineRequest(@NotEmpty List<UUID> orderedIds) {}
