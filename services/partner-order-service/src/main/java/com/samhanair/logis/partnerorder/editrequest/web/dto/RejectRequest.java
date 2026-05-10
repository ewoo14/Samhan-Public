package com.samhanair.logis.partnerorder.editrequest.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 거절 body — PR-H4b. 거절 사유 필수.
 */
public record RejectRequest(@NotBlank @Size(max = 500) String reason) { }
