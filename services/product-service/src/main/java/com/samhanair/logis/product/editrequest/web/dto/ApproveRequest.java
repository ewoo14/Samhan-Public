package com.samhanair.logis.product.editrequest.web.dto;

import jakarta.validation.constraints.Size;

/** 수락 body — PR-H4b. 수락 메모 (선택). */
public record ApproveRequest(@Size(max = 500) String note) { }
