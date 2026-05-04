package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 제품 부분 수정 — null 필드는 미변경. 가격/태그/단종은 별도 endpoint. */
public record UpdateProductRequest(
        @Size(max = 150) String name,
        @Size(max = 100) String modelName,
        UUID categoryId,
        @Size(max = 1000) String description) {
}
