package com.samhanair.logis.partnerorder.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.partnerorder.PartnerOrderServiceApplication;
import com.samhanair.logis.partnerorder.client.DcConfigClient;
import com.samhanair.logis.partnerorder.client.InventoryClient;
import com.samhanair.logis.partnerorder.client.PartnerAuthClient;
import com.samhanair.logis.partnerorder.client.ProductClient;
import com.samhanair.logis.partnerorder.client.SlipServiceClient;
import com.samhanair.logis.partnerorder.vendor.client.PartnerLookupClient;
import com.samhanair.logis.partnerorder.vendor.client.ProductCatalogLookupClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * ApplicationContext load 만 검증하는 경량 IT — bean 등록 충돌 / dependency injection 누락 즉시 탐지.
 *
 * <p><b>장기 가드 (PR #119 회귀 fix 후속, memory feedback_pm_integration_build_check).</b>
 * partner-order-service 의 Configuration class 들에서 {@code @Bean} 메서드 이름이 클래스 빈 이름과
 * 충돌하는 패턴을 사전에 차단. PR-F2 신규 {@link com.samhanair.logis.partnerorder.vendor.ocr.OcrEngineConfig}
 * 의 {@code tesseractOcrEngineBean} / {@code mockOcrEngineBean} suffix 도 본 IT 가 회귀 가드.
 *
 * <p>OCR 비활성 (default) 상태에서도 ApplicationContext 부팅 가능해야 함.
 *
 * <p>외부 client {@code @MockBean} 격리 — Eureka 비활성 환경 5xx 회피.
 */
@SpringBootTest(classes = PartnerOrderServiceApplication.class)
@TestPropertySource(properties = {
        // OCR default disabled — 본 IT 는 비활성 상태에서도 context 부팅 검증
        "samhan.partner-order.ocr.enabled=false"
})
class ApplicationContextLoadIT extends AbstractPostgresIT {

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private DcConfigClient dcConfigClient;
    @MockBean
    private ProductClient productClient;
    @MockBean
    private InventoryClient inventoryClient;
    @MockBean
    private SlipServiceClient slipServiceClient;
    @MockBean
    private PartnerAuthClient partnerAuthClient;
    @MockBean
    private PartnerLookupClient partnerLookupClient;
    @MockBean
    private ProductCatalogLookupClient catalogLookupClient;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        // VendorOrderService 는 OcrEngine bean 없이도 (ObjectProvider) 등록 가능해야 함
        assertThat(applicationContext.getBeansOfType(
                com.samhanair.logis.partnerorder.vendor.service.VendorOrderService.class))
                .as("VendorOrderService bean 등록 (OcrEngine disabled 상태에서도)")
                .isNotEmpty();
        // OCR 비활성 → OcrEngine bean 미등록 확인
        assertThat(applicationContext.getBeansOfType(
                com.samhanair.logis.partnerorder.vendor.ocr.OcrEngine.class))
                .as("OCR disabled → OcrEngine bean 미등록")
                .isEmpty();
    }
}
