package com.samhanair.logis.partnerorder.vendor.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * MockOcrEngine — preset / default / 빈 입력 / null 4 case.
 */
class MockOcrEngineTest {

    @Test
    void empty_bytes_returns_blank() {
        MockOcrEngine engine = new MockOcrEngine("default-text");
        assertThat(engine.extractText(new byte[0], "image/png")).isEmpty();
        assertThat(engine.extractText(null, "image/png")).isEmpty();
    }

    @Test
    void preset_match_returns_preset_text() {
        MockOcrEngine engine = new MockOcrEngine("default");
        engine.setPresetText("AIRD", "에어디자이너 발주서\n1. 헬로멀티 5kW [HM-5000] 2개 1,000,000원\n합계: 2,000,000원");
        byte[] bytes = "AIRD".getBytes(StandardCharsets.UTF_8);
        String text = engine.extractText(bytes, "image/png");
        assertThat(text).contains("에어디자이너").contains("HM-5000");
    }

    @Test
    void no_preset_returns_default() {
        MockOcrEngine engine = new MockOcrEngine("default-fallback");
        byte[] bytes = "UNKNOWN-INPUT-DATA".getBytes(StandardCharsets.UTF_8);
        String text = engine.extractText(bytes, "image/png");
        assertThat(text).isEqualTo("default-fallback");
    }

    @Test
    void no_default_returns_blank() {
        MockOcrEngine engine = new MockOcrEngine();
        byte[] bytes = "any".getBytes(StandardCharsets.UTF_8);
        assertThat(engine.extractText(bytes, "image/png")).isEmpty();
    }
}
