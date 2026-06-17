package com.samhanair.logis.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Product 도메인 — 변동DC 수동 override 메서드 단위 테스트.
 *
 * <p>검증 대상:
 * <ul>
 *   <li>{@code markVariableDiscountManual()} — hasVariableDiscount 변경 + variableDiscountManual=true</li>
 *   <li>{@code clearVariableDiscountManual()} — 플래그만 false, 변동DC 값 유지</li>
 * </ul>
 */
class ProductVariableDiscountManualTest {

    private Product product;

    @BeforeEach
    void setUp() {
        Category category = Category.create("INDOOR_WALL", "벽걸이형", null, 1);
        ReflectionTestUtils.setField(category, "id", java.util.UUID.randomUUID());

        product = Product.create("테스트 품목", "TEST-MODEL",
                category,
                new BigDecimal("1000000"),
                new BigDecimal("800000"),
                "KRW", null, null);
    }

    @Test
    @DisplayName("markVariableDiscountManual — true 지정 시 hasVariableDiscount=true 및 manual=true")
    void markVariableDiscountManual_true_setsValueAndManualTrue() {
        product.markVariableDiscountManual(true);

        assertThat(product.getHasVariableDiscount()).isTrue();
        assertThat(product.isVariableDiscountManual()).isTrue();
    }

    @Test
    @DisplayName("markVariableDiscountManual — false 지정 시 hasVariableDiscount=false 및 manual=true")
    void markVariableDiscountManual_false_setsValueAndManualTrue() {
        product.applyDiscountRules(true, null, false, null);

        product.markVariableDiscountManual(false);

        assertThat(product.getHasVariableDiscount()).isFalse();
        assertThat(product.isVariableDiscountManual()).isTrue();
    }

    @Test
    @DisplayName("clearVariableDiscountManual — 플래그만 false 로 복귀, 변동DC 값 유지")
    void clearVariableDiscountManual_onlyResetsFlagKeepsValue() {
        product.markVariableDiscountManual(true);

        product.clearVariableDiscountManual();

        assertThat(product.isVariableDiscountManual()).isFalse();
        assertThat(product.getHasVariableDiscount()).isTrue();
    }

    @Test
    @DisplayName("기본 variableDiscountManual — false (시트 자동 적재)")
    void defaultVariableDiscountManual_isFalse() {
        assertThat(product.isVariableDiscountManual()).isFalse();
    }
}
