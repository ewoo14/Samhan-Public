package com.samhanair.logis.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.product.domain.MaterialKey;
import org.junit.jupiter.api.Test;

/**
 * VariableDiscountDetector 4 룰 단위 테스트 — Apps Script 출력값 1:1 비교 reference.
 *
 * <p>출처: DOMAIN-EXTENSIONS §1 매트릭스 + formulas.json grep 결과.
 */
class VariableDiscountDetectorTest {

    private final VariableDiscountDetector detector = new VariableDiscountDetector();

    // ---------- 룰 1: $L$2 (홈/상업 멀티 useK2) ----------

    @Test
    void 룰1_L2_절대참조_있으면_TRUE() {
        // 홈멀티 D 열 단가 수식 sample (Apps Script line 1742 useK2 발견 패턴)
        String formula = "=IF($L$2>0, D5*(1-$L$2), D5)";
        assertThat(detector.detectHasVariableDiscount(formula)).isTrue();
    }

    @Test
    void 룰1_L2_미포함_FALSE() {
        String formula = "=D5*0.55";
        assertThat(detector.detectHasVariableDiscount(formula)).isFalse();
    }

    @Test
    void 룰1_null_blank_FALSE() {
        assertThat(detector.detectHasVariableDiscount(null)).isFalse();
        assertThat(detector.detectHasVariableDiscount("")).isFalse();
        assertThat(detector.detectHasVariableDiscount("   ")).isFalse();
    }

    // ---------- 룰 2: $D$4 / $D$7 / $D$8 ----------

    @Test
    void 룰2_D4_master_매핑() {
        String formula = "=$D$4*0.5+$L$2";
        assertThat(detector.detectMaterialKey(formula)).contains(MaterialKey.D4);
    }

    @Test
    void 룰2_D7_미포함_매핑() {
        String formula = "=IF($D$7>0,$D$7,0)";
        assertThat(detector.detectMaterialKey(formula)).contains(MaterialKey.D7);
    }

    @Test
    void 룰2_D8_포함_매핑() {
        String formula = "=$D$8*1.1";
        assertThat(detector.detectMaterialKey(formula)).contains(MaterialKey.D8);
    }

    @Test
    void 룰2_D5_등_무관_empty() {
        // D5/D6/D9 등은 enum 외 (D4/D7/D8 만)
        String formula = "=$D$5*2";
        assertThat(detector.detectMaterialKey(formula)).isEmpty();
    }

    @Test
    void 룰2_priceFormula_없음_empty() {
        assertThat(detector.detectMaterialKey(null)).isEmpty();
        assertThat(detector.detectMaterialKey("")).isEmpty();
    }

    // ---------- 룰 3: $I$1 (구형 50%) ----------

    @Test
    void 룰3_구형_I1_TRUE() {
        String formula = "=D5*$I$1";
        assertThat(detector.detectLegacyDiscount(formula)).isTrue();
    }

    @Test
    void 룰3_legacyFixedRate_50퍼센트() {
        assertThat(detector.legacyFixedDiscountRate()).isEqualByComparingTo("0.5000");
    }

    // ---------- discountFlags 6 비트 ----------

    @Test
    void flags_360_매칭() {
        // is360 비트 첫 자리
        assertThat(detector.detectDiscountFlags("AC060CS6PBH1SY 360 CST"))
                .startsWith("1");
    }

    @Test
    void flags_4way_매칭() {
        // is4way 비트 두 번째
        assertThat(detector.detectDiscountFlags("DVM 4way Premium")).matches("01\\d{4}");
    }

    @Test
    void flags_1way_매칭() {
        assertThat(detector.detectDiscountFlags("DVM 1way Slim")).matches("\\d01\\d{3}");
    }

    @Test
    void flags_stand_매칭() {
        assertThat(detector.detectDiscountFlags("스탠드형 18평")).matches("\\d{3}1\\d{2}");
    }

    @Test
    void flags_deluxe_매칭() {
        assertThat(detector.detectDiscountFlags("디럭스 모델")).matches("\\d{4}1\\d");
    }

    @Test
    void flags_grade1_매칭() {
        assertThat(detector.detectDiscountFlags("1등급 인증")).matches("\\d{5}1");
    }

    @Test
    void flags_매치_없음_000000() {
        assertThat(detector.detectDiscountFlags("AJ060MXHNBC1")).isEqualTo("000000");
    }

    @Test
    void flags_null_blank_000000() {
        assertThat(detector.detectDiscountFlags(null)).isEqualTo("000000");
        assertThat(detector.detectDiscountFlags("")).isEqualTo("000000");
    }
}
