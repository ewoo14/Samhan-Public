package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.Category;
import java.util.List;
import java.util.UUID;

/** 카테고리 트리 노드. {@code children} 은 재귀 nested. */
public record CategoryResponse(
        UUID id,
        String code,
        String name,
        UUID parentId,
        int displayOrder,
        List<CategoryResponse> children) {

    public static CategoryResponse leaf(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getParent() == null ? null : c.getParent().getId(),
                c.getDisplayOrder(),
                List.of());
    }

    public static CategoryResponse withChildren(Category c, List<CategoryResponse> children) {
        return new CategoryResponse(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getParent() == null ? null : c.getParent().getId(),
                c.getDisplayOrder(),
                children);
    }
}
