package com.samhanair.logis.inventory.web.dto;

import com.samhanair.logis.inventory.domain.WarehouseType;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 창고 부분 수정. null 인 필드는 변경하지 않는다. */
public record UpdateWarehouseRequest(
        @Size(max = 100) String name,
        WarehouseType type,
        @Size(max = 255) String address,
        @PositiveOrZero Integer displayOrder,
        @Size(max = 500) String description) {
}
