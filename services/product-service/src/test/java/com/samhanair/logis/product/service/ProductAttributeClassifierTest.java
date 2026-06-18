package com.samhanair.logis.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductAttributeClassifierTest {

    private final ProductAttributeClassifier classifier = new ProductAttributeClassifier();

    @Test
    void classifyPanelType_GAS_공청_WIFI_미내장_인피니트_분기와_특수판넬을_분류한다() {
        assertThat(classifier.classifyPanelType("공청판넬", null)).isEqualTo("공청판넬");
        assertThat(classifier.classifyPanelType("공기청정 WIFI 판넬", null)).isEqualTo("공기청정 WIFI");
        assertThat(classifier.classifyPanelType("공기청정 미내장 판넬", null)).isEqualTo("공기청정 미내장");
        assertThat(classifier.classifyPanelType("WIFI 판넬", null)).isEqualTo("WIFI");
        assertThat(classifier.classifyPanelType("미내장판넬", null)).isEqualTo("미내장");
        assertThat(classifier.classifyPanelType("인피니트 판넬", null)).isEqualTo("인피니트");
        assertThat(classifier.classifyPanelType("블랙판넬", null)).isEqualTo("블랙판넬");
        assertThat(classifier.classifyPanelType("자동승강 판넬", null)).isEqualTo("승강판넬");
        assertThat(classifier.classifyPanelType("360 판넬 원형", null)).isEqualTo("360");
    }

    @Test
    void classifyPanelType_판넬이_아니면_null() {
        assertThat(classifier.classifyPanelType("Hi-Multi 4-Way 실내기", "AJ040RXH4BC1")).isNull();
        assertThat(classifier.classifyPanelType("일반 판넬", null)).isNull();
    }

    @Test
    void classifyRemoteType_BundleExpander_유선_컬러유선과_무선_fallback을_분류한다() {
        assertThat(classifier.classifyRemoteType("유선리모컨")).isEqualTo("유선리모컨");
        assertThat(classifier.classifyRemoteType("컬러유선리모컨")).isEqualTo("컬러유선리모컨");
        assertThat(classifier.classifyRemoteType("유선컬러 리모컨")).isEqualTo("컬러유선리모컨");
        assertThat(classifier.classifyRemoteType("무선리모컨")).isEqualTo("무선리모컨");
        assertThat(classifier.classifyRemoteType("리모콘")).isEqualTo("무선리모컨");
    }

    @Test
    void classifyRemoteType_리모컨이_아니면_null() {
        assertThat(classifier.classifyRemoteType("공기청정 WIFI 판넬")).isNull();
    }
}
