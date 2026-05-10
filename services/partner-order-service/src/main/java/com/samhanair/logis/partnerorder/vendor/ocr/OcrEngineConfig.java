package com.samhanair.logis.partnerorder.vendor.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * vendor.ocr 패키지의 OCR engine bean 등록.
 *
 * <p>{@link OcrProperties#isEnabled()} = false (default) 시 어떤 engine bean 도 등록되지 않으며,
 * {@link com.samhanair.logis.partnerorder.vendor.web.VendorOrderController} 가 503 SERVICE_UNAVAILABLE
 * 로 응답 (DevOps 가 Tesseract native 미설치 fallback).
 *
 * <p><b>@Bean 메서드명 *Bean suffix</b> (PR #119 회귀 가드 — feedback_pm_integration_build_check):
 * 클래스명과 동일한 메서드명 사용 시 BeanDefinitionOverrideException 위험. {@code tesseractOcrEngineBean},
 * {@code mockOcrEngineBean} 형태로 suffix 강제.
 */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
@ConditionalOnProperty(prefix = "samhan.partner-order.ocr",
        name = "enabled", havingValue = "true", matchIfMissing = false)
public class OcrEngineConfig {

    /** TESSERACT 모드 — 운영 default. tess4j 기반 native OCR. */
    @Bean
    @ConditionalOnProperty(prefix = "samhan.partner-order.ocr",
            name = "engine", havingValue = "TESSERACT", matchIfMissing = true)
    @ConditionalOnMissingBean(OcrEngine.class)
    public OcrEngine tesseractOcrEngineBean(OcrProperties properties) {
        return new TesseractOcrEngine(properties);
    }

    /** MOCK 모드 — IT / Tesseract 미설치 환경에서만 사용. */
    @Bean
    @ConditionalOnProperty(prefix = "samhan.partner-order.ocr",
            name = "engine", havingValue = "MOCK")
    @ConditionalOnMissingBean(OcrEngine.class)
    public OcrEngine mockOcrEngineBean() {
        return new MockOcrEngine();
    }
}
