package com.samhanair.logis.partnerorder.vendor.ocr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 단위 테스트 / Tesseract 미설치 환경 fallback OCR 엔진 — 미리 정의된 text 반환.
 *
 * <p>{@link #setPresetText(String, String)} 로 mimeType 별 preset 등록.
 * 운영 사용 X (samhan.partner-order.ocr.engine = MOCK 시에만 활성).
 */
public class MockOcrEngine implements OcrEngine {

    /** byte 첫 16 hash → text 매핑. 테스트가 명시적으로 setPresetText 미호출 시 default 반환. */
    private final Map<String, String> presets = new ConcurrentHashMap<>();

    private String defaultText = "";

    public MockOcrEngine() {
    }

    public MockOcrEngine(String defaultText) {
        this.defaultText = defaultText == null ? "" : defaultText;
    }

    /** preset 등록 — key 는 임의 문자열 (예: 파일 첫 줄). */
    public void setPresetText(String key, String text) {
        presets.put(key, text == null ? "" : text);
    }

    public void setDefaultText(String text) {
        this.defaultText = text == null ? "" : text;
    }

    @Override
    public String extractText(byte[] imageOrPdfBytes, String mimeType) {
        if (imageOrPdfBytes == null || imageOrPdfBytes.length == 0) {
            return "";
        }
        // 단순 전략 — bytes 의 첫 32 바이트를 string 으로 변환하여 preset key 로 시도.
        // 매칭 실패 시 default.
        int len = Math.min(imageOrPdfBytes.length, 32);
        String key = new String(imageOrPdfBytes, 0, len);
        return presets.getOrDefault(key, defaultText);
    }
}
