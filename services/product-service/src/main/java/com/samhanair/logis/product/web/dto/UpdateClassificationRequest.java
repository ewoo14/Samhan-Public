package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Classification 부분 수정 요청. null 필드는 미변경한다. */
public record UpdateClassificationRequest(
        UUID parentId,
        @Size(max = 100) String name,
        Integer displayOrder,
        Boolean active) {
}
