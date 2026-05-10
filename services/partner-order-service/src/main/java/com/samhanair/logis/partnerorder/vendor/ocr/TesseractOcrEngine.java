package com.samhanair.logis.partnerorder.vendor.ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * tess4j 기반 운영 OCR 엔진 — 한국어 ({@code kor.traineddata}) + 영어 ({@code eng.traineddata}).
 *
 * <p>외부 의존성: native Tesseract 4.x + tessdata. DevOps 가 setup script 로 설치 (별도 dispatch).
 *
 * <p>입력 처리:
 * <ul>
 *   <li>이미지 (PNG/JPEG/TIFF) → ImageIO 로 BufferedImage 후 OCR</li>
 *   <li>PDF → tess4j 가 PDFBox 의존성으로 page-by-page rasterize 후 OCR (단일 page 만 처리)</li>
 * </ul>
 *
 * <p>{@link OcrProperties#isEnabled()} = true 일 때만 bean 등록 (조건부 — controller 가 503 fallback).
 */
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    private final OcrProperties properties;

    public TesseractOcrEngine(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String extractText(byte[] imageOrPdfBytes, String mimeType) {
        if (imageOrPdfBytes == null || imageOrPdfBytes.length == 0) {
            return "";
        }
        try {
            ITesseract tesseract = newTesseract();
            if (mimeType != null && mimeType.toLowerCase().contains("pdf")) {
                // tess4j 의 doOCR(File) 만 PDF 지원 — bytes 는 임시 파일로 spool
                java.io.File tmp = java.io.File.createTempFile("vendor-pdf-", ".pdf");
                try {
                    java.nio.file.Files.write(tmp.toPath(), imageOrPdfBytes);
                    return safeText(tesseract.doOCR(tmp));
                } finally {
                    if (!tmp.delete()) {
                        log.debug("임시 PDF 삭제 실패: {}", tmp.getAbsolutePath());
                    }
                }
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageOrPdfBytes));
            if (image == null) {
                throw new OcrException("이미지 디코드 실패 — mimeType=" + mimeType);
            }
            return safeText(tesseract.doOCR(image));
        } catch (TesseractException ex) {
            throw new OcrException("Tesseract OCR 실패 — " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new OcrException("OCR 입력 IO 실패 — " + ex.getMessage(), ex);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
            // native libtesseract 미설치 / leptonica 누락
            throw new OcrException("Tesseract native 라이브러리 미설치 — " + ex.getMessage(), ex);
        }
    }

    /**
     * Tesseract 인스턴스 생성 — properties 기반 dataPath / language / pageSegMode 설정.
     * 매 호출 시 신규 (tess4j 의 ITesseract 는 thread-safe X — 동시 요청 안전성 우선).
     */
    private ITesseract newTesseract() {
        Tesseract t = new Tesseract();
        t.setDatapath(properties.getTesseract().getDataPath());
        t.setLanguage(properties.getTesseract().getLanguage());
        t.setPageSegMode(properties.getTesseract().getPageSegMode());
        return t;
    }

    private static String safeText(String raw) {
        return raw == null ? "" : raw;
    }
}
