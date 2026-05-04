package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 카테고리 신규 등록. parentId 가 null 이면 루트. */
public record CreateCategoryRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        UUID parentId,
        int displayOrder) {
}
