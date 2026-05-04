package com.samhanair.logis.inventory.web.dto;

import com.samhanair.logis.inventory.domain.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 창고 신규 등록 요청. {@code displayOrder} 기본 0. */
public record CreateWarehouseRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull WarehouseType type,
        @Size(max = 255) String address,
        @PositiveOrZero Integer displayOrder,
        @Size(max = 500) String description) {
}
