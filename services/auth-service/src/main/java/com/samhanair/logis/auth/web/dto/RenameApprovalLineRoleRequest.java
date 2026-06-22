package com.samhanair.logis.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 결재라인 역할 라벨 변경 요청 DTO.
 *
 * <p>빈 문자열이나 null 라벨은 요청 수준에서도 거부한다(@NotBlank).
 * 도메인 레벨에서도 이중으로 검증(ApprovalLineConfig.rename).
 *
 * @param label 변경할 역할 표시 명칭(공백 불가)
 */
public record RenameApprovalLineRoleRequest(@NotBlank @Size(max = 50) String label) {}
