package com.samhanair.logis.product.editrequest.web.dto;

import com.samhanair.logis.shared.realtime.editrequest.EditRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제품 수정/삭제 요청 생성 body — PR-H4b.
 */
public record CreateEditRequestRequest(
        @NotNull EditRequestType type,
        @Size(max = 500) String reason
) { }
