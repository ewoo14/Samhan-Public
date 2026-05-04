package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 카테고리 부분 수정. null 필드는 미변경. parentId 변경 = 트리 이동. */
public record UpdateCategoryRequest(
        @Size(max = 100) String name,
        UUID parentId,
        Integer displayOrder) {
}
