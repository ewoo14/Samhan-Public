package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.Classification;
import com.samhanair.logis.product.domain.EstimateCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Classification 생성 요청. */
public record CreateClassificationRequest(
        @NotNull EstimateCategory estimateCategory,
        @NotNull Classification.CatLevel catLevel,
        UUID parentId,
        @NotBlank @Size(max = 100) String name,
        Integer displayOrder,
        Boolean active) {
}
