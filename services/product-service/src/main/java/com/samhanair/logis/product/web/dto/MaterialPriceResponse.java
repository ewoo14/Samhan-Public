package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.MaterialPrice;
import com.samhanair.logis.product.domain.Product;
import java.math.BigDecimal;

/** 자재 단가 lookup 응답 — 라인 입력 참조용 비즈니스 필드만 노출한다. */
public record MaterialPriceResponse(
        String materialKey,
        String name,
        BigDecimal price,
        String optionLabel
) {

    /**
     * 자재 단가 엔티티를 lookup 응답으로 변환한다.
     *
     * @param materialPrice 자재 단가 엔티티
     * @return UUID/id 와 computedFormula 를 제외한 응답
     */
    public static MaterialPriceResponse from(MaterialPrice materialPrice) {
        return new MaterialPriceResponse(
                materialPrice.getMaterialKey(),
                materialPrice.getName(),
                materialPrice.getPrice(),
                materialPrice.getOptionLabel());
    }

    /**
     * Product(MATERIAL) 자재 품목을 기존 자재 lookup 응답 형태로 변환한다.
     *
     * <p>materialKey 필드는 공개 lookup 호환용으로 유지하되, Product 원천 전환 후에는 사용자가 보는
     * 품목 코드인 modelCode 를 담는다. estimate-app 은 name/price 만 사용하므로 기존 {name, price}
     * 매핑 형태를 유지한다.
     *
     * @param product MATERIAL 카테고리 Product
     * @return UUID/computedFormula 없이 기존 lookup 호환 필드만 담은 응답
     */
    public static MaterialPriceResponse from(Product product) {
        BigDecimal price = product.getDeliveryPrice() != null
                ? product.getDeliveryPrice()
                : product.getReleasePrice();
        return new MaterialPriceResponse(
                product.getModelCode() == null ? product.getModelName() : product.getModelCode(),
                product.getName(),
                price,
                null);
    }
}
