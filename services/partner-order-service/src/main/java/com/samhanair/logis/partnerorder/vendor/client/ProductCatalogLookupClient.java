package com.samhanair.logis.partnerorder.vendor.client;

import com.samhanair.logis.partnerorder.client.GoogleSheetsClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 종합견적서 시트 (product-service GoogleSheetsClient 1:1 복제 기 보유) 기반 modelCode → 단가 lookup.
 *
 * <p>사용자 명시 (memory project_arologis_phase10): legacy GAS 의 Notion 단가 마스터를 폐기하고
 * 우리 자체 종합견적서 시트로 일원화. partner-order-service 가 이미 PR-D 에서 시트 read 패턴을
 * 보유 중이므로 신규 client 는 sheetId/range/lookup 로직만 추가.
 *
 * <p>fail-soft: 시트 read 실패 / 매칭 없음 시 empty 반환 — controller 가 OCR 단가 fallback.
 *
 * <p>본 client 는 IT 에서 {@code @MockBean} 격리 의무.
 */
@Component
public class ProductCatalogLookupClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogLookupClient.class);

    private final GoogleSheetsClient sheetsClient;

    /** 종합견적서 시트 ID — 운영에서는 INTEGRATED_QUOTE_SHEET_ID 환경변수 override. */
    @Value("${samhan.partner-order.vendor.catalog-sheet-id:${BOOTSTRAP_SHEET_ID:1RJqO3jT-yJTi3NDBhL60o_cZWlVETGTU7UlvIKXuVNQ}}")
    private String catalogSheetId;

    /** 종합견적서 range — 컬럼 0=modelCode, 1=productName, 2=unitPrice 가정. */
    @Value("${samhan.partner-order.vendor.catalog-range:종합견적서!A2:C}")
    private String catalogRange;

    public ProductCatalogLookupClient(GoogleSheetsClient sheetsClient) {
        this.sheetsClient = sheetsClient;
    }

    /**
     * modelCode → CatalogEntry lookup. Caffeine 5분 TTL (GoogleSheetsClient 내부) 캐시 활용.
     *
     * @param modelCode 모델코드 (필수)
     * @return CatalogEntry (성공) / empty (시트 read 실패 / 미매칭)
     */
    public Optional<CatalogEntry> findByModelCode(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return Optional.empty();
        }
        Map<String, CatalogEntry> all = loadCatalog();
        return Optional.ofNullable(all.get(modelCode.trim()));
    }

    /** 다건 lookup — controller 가 한번에 여러 라인 처리할 때 호출 (시트 read 1회만). */
    public Map<String, CatalogEntry> findByModelCodes(List<String> modelCodes) {
        if (modelCodes == null || modelCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, CatalogEntry> all = loadCatalog();
        Map<String, CatalogEntry> result = new HashMap<>();
        for (String code : modelCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            CatalogEntry e = all.get(code.trim());
            if (e != null) {
                result.put(code.trim(), e);
            }
        }
        return result;
    }

    /** 종합견적서 전체 → modelCode 인덱스. fail-soft (read 실패 시 빈 map). */
    private Map<String, CatalogEntry> loadCatalog() {
        try {
            List<List<Object>> rows = sheetsClient.readSheet(catalogSheetId, catalogRange);
            Map<String, CatalogEntry> map = new HashMap<>();
            for (List<Object> row : rows) {
                List<String> cells = GoogleSheetsClient.toStringRow(row, 3);
                String modelCode = cells.get(0).trim();
                if (modelCode.isEmpty()) {
                    continue;
                }
                String productName = cells.get(1).trim();
                BigDecimal unitPrice = parsePrice(cells.get(2));
                map.put(modelCode, new CatalogEntry(modelCode, productName, unitPrice));
            }
            return map;
        } catch (IOException | GeneralSecurityException ex) {
            log.warn("ProductCatalogLookupClient — sheet read fail-soft: {}", ex.getMessage());
            return Map.of();
        } catch (RuntimeException ex) {
            log.warn("ProductCatalogLookupClient — 예상치 못한 오류 fail-soft: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.replace(",", "").replace("원", "").trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 시트 카탈로그 한 줄.
     *
     * @param modelCode 모델코드 (lookup key)
     * @param productName 사용자 표시 제품명
     * @param unitPrice 단가 (VAT 포함 — 종합견적서 표기 기준)
     */
    public record CatalogEntry(String modelCode, String productName, BigDecimal unitPrice) {
    }
}
