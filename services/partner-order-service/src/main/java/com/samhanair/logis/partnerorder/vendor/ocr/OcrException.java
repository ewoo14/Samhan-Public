package com.samhanair.logis.partnerorder.vendor.ocr;

/**
 * OCR 엔진 native 호출 실패 예외 — Tesseract 미설치, 한국어 데이터 파일 부재, 손상된 입력 등.
 *
 * <p>controller 가 catch 후 503 SERVICE_UNAVAILABLE 로 변환 (DevOps setup 안내).
 */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
