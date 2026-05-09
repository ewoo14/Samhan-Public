package com.samhanair.logis.inventory.web.dto;

import com.samhanair.logis.inventory.domain.Warehouse;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 창고 admin 목록 응답 — Phase 10 P0-5.
 *
 * <p>frontend {@code /admin/warehouses} 페이지 backing. UUID 비공개 — items 는
 * {@link WarehouseResponse} (code / name / type 등 비즈니스 식별자만 노출).
 *
 * @param items 페이지 내 창고 요약 리스트
 * @param total 전체 매칭 건수
 * @param page 0-based 페이지 번호
 * @param size 페이지 크기
 */
public record AdminWarehouseListResponse(
        List<WarehouseResponse> items,
        long total,
        int page,
        int size
) {

    public static AdminWarehouseListResponse from(Page<Warehouse> page) {
        List<WarehouseResponse> items = page.getContent().stream()
                .map(WarehouseResponse::from)
                .toList();
        return new AdminWarehouseListResponse(
                items,
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }
}
