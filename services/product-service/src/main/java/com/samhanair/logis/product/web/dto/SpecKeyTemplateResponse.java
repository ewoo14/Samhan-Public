package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.EstimateCategory;
import com.samhanair.logis.product.domain.SpecKeyTemplate;
import com.samhanair.logis.product.domain.SpecKeyValueType;
import java.util.UUID;

/** SpecKeyTemplate endpoint 응답 — 카테고리별 추천 스펙 키. */
public record SpecKeyTemplateResponse(
        UUID id,
        EstimateCategory estimateCategory,
        String specKey,
        String defaultUnit,
        SpecKeyValueType valueType,
        Integer displayOrder,
        boolean isRecommended
) {
    public static SpecKeyTemplateResponse from(SpecKeyTemplate t) {
        return new SpecKeyTemplateResponse(t.getId(), t.getEstimateCategory(), t.getSpecKey(),
                t.getDefaultUnit(), t.getValueType(), t.getDisplayOrder(),
                Boolean.TRUE.equals(t.getIsRecommended()));
    }
}
