package com.samhanair.logis.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductAttributeClassifierTest {

    private final ProductAttributeClassifier classifier = new ProductAttributeClassifier();

    @Test
    void classifyPanelType_F4_pickPanelRow_옵션_버킷으로_분류한다() {
        // GAS ground-truth: views/index.ejs pickPanelRow/옵션 셀렉터
        // 공청판넬=/(공기청정|공청)/, 블랙판넬=/블랙/, 승강판넬=/(자동승강|승강)/, 360=componentVariant 형상.
        assertThat(classifier.classifyPanelType("공청판넬", null)).isEqualTo("공청");
        assertThat(classifier.classifyPanelType("공기청정 WIFI 판넬", null)).isEqualTo("공청");
        assertThat(classifier.classifyPanelType("공기청정 미내장 판넬", null)).isEqualTo("공청");
        assertThat(classifier.classifyPanelType("블랙판넬", null)).isEqualTo("블랙");
        assertThat(classifier.classifyPanelType("자동승강 판넬", null)).isEqualTo("승강");
        assertThat(classifier.classifyPanelType("360 판넬 원형", null)).isEqualTo("360");
        assertThat(classifier.classifyPanelType("일반 판넬", null)).isEqualTo("일반");
        assertThat(classifier.classifyPanelType("WIFI 판넬", null)).isEqualTo("일반");
        assertThat(classifier.classifyPanelType("미내장판넬", null)).isEqualTo("일반");
        assertThat(classifier.classifyPanelType("인피니트 판넬", null)).isEqualTo("일반");
    }

    @Test
    void classifyPanelType_판넬이_아니면_null() {
        assertThat(classifier.classifyPanelType("Hi-Multi 4-Way 실내기", "AJ040RXH4BC1")).isNull();
    }

    @Test
    void classifyRemoteType_F4_옵션_vocab으로_분류한다() {
        // GAS ground-truth: comm_remote 셀렉터 ['제외','무선','유선','컬러유선'] + /유선.*리모컨/ 옵션 감지.
        assertThat(classifier.classifyRemoteType("유선리모컨")).isEqualTo("유선");
        assertThat(classifier.classifyRemoteType("유선 리모컨")).isEqualTo("유선");
        assertThat(classifier.classifyRemoteType("컬러유선리모컨")).isEqualTo("컬러유선");
        assertThat(classifier.classifyRemoteType("유선컬러 리모컨")).isEqualTo("컬러유선");
        assertThat(classifier.classifyRemoteType("무선리모컨")).isEqualTo("무선");
        assertThat(classifier.classifyRemoteType("리모콘")).isEqualTo("무선");
    }

    @Test
    void classifyRemoteType_리모컨이_아니면_null() {
        assertThat(classifier.classifyRemoteType("공기청정 WIFI 판넬")).isNull();
    }
}
