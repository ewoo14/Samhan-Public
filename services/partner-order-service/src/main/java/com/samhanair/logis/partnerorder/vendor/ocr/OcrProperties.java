package com.samhanair.logis.partnerorder.vendor.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * vendor.ocr 설정 — {@code samhan.partner-order.ocr.*}.
 *
 * <ul>
 *   <li>{@code enabled} — false 시 controller 503 + Tesseract bean 비등록 (DevOps setup 미완 fallback)</li>
 *   <li>{@code engine} — TESSERACT (default) / MOCK (단위 테스트 용 — 운영 사용 X)</li>
 *   <li>{@code tesseract.dataPath} — tessdata 디렉토리 (kor.traineddata + eng.traineddata 위치)</li>
 *   <li>{@code tesseract.language} — Tesseract -l 옵션 (예: {@code kor+eng})</li>
 *   <li>{@code tesseract.pageSegMode} — Page Segmentation Mode (default 6 — single block of text)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "samhan.partner-order.ocr")
public class OcrProperties {

    /** OCR 활성화 여부. false 시 controller 가 503 반환 (DevOps Tesseract 미설치 fallback). */
    private boolean enabled = false;

    /** 엔진 종류. TESSERACT / MOCK. */
    private String engine = "TESSERACT";

    private final Tesseract tesseract = new Tesseract();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public Tesseract getTesseract() {
        return tesseract;
    }

    /** tess4j Tesseract4 native 옵션. */
    public static class Tesseract {
        /** tessdata 디렉토리 (kor.traineddata + eng.traineddata 위치). */
        private String dataPath = "/usr/share/tesseract-ocr/4.00/tessdata";

        /** Tesseract -l 옵션. 한국어 우선 + 영어 (legacy 발주서가 영문 model code 혼재). */
        private String language = "kor+eng";

        /** Page Segmentation Mode (3=auto, 6=single block, 11=sparse text). */
        private int pageSegMode = 6;

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public int getPageSegMode() {
            return pageSegMode;
        }

        public void setPageSegMode(int pageSegMode) {
            this.pageSegMode = pageSegMode;
        }
    }
}
