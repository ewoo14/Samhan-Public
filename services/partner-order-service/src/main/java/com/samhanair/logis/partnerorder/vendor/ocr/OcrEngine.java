package com.samhanair.logis.partnerorder.vendor.ocr;

/**
 * 외부 vendor 발주서 (PDF / 이미지) → text 추출 추상화.
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link TesseractOcrEngine} — 운영 default (tess4j + 한국어/영어 데이터 파일)</li>
 *   <li>{@link MockOcrEngine} — 테스트 / Tesseract 미설치 fallback (preset text 반환)</li>
 * </ul>
 *
 * <p>legacy GAS #10 (에어디자이너) + #14 (제이시스템) Drive OCR 흐름의 자체 on-prem 대체.
 * Samhan Public 안에서 외부 시스템 의존 없이 native Tesseract 만 사용 (DevOps setup 의무).
 *
 * <p>입력 mimeType:
 * <ul>
 *   <li>{@code image/png}, {@code image/jpeg}, {@code image/tiff} — 이미지 직접 OCR</li>
 *   <li>{@code application/pdf} — PDF (구현체가 page rasterize 후 OCR)</li>
 * </ul>
 */
public interface OcrEngine {

    /**
     * 바이트 입력 → 추출 text 반환. 빈 결과는 빈 문자열 ("") 로 반환 (null X).
     *
     * @param imageOrPdfBytes 업로드 파일 바이트 (필수)
     * @param mimeType MIME (필수)
     * @return 추출 text (개행 보존). 추출 실패 또는 빈 인식 시 "" 반환
     * @throws OcrException OCR 엔진 native 호출 실패 (Tesseract 미설치 / 데이터 파일 누락 등)
     */
    String extractText(byte[] imageOrPdfBytes, String mimeType);
}
