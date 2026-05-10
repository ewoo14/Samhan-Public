package com.samhanair.logis.partnerorder.vendor.parser;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * vendor 식별자 (사용자 명시 vendorName) 또는 OCR text keyword 휴리스틱으로
 * {@link VendorOrderParser} 매칭. 모든 등록된 parser bean 을 spring 이 주입.
 *
 * <p>매칭 우선순위:
 * <ol>
 *   <li>사용자가 vendorName 을 명시하면 정확 매칭 우선</li>
 *   <li>명시 없으면 각 parser 의 {@link VendorOrderParser#matches(String)} 휴리스틱 순회</li>
 * </ol>
 */
@Component
public class VendorParserRegistry {

    private final List<VendorOrderParser> parsers;

    public VendorParserRegistry(List<VendorOrderParser> parsers) {
        this.parsers = parsers == null ? List.of() : parsers;
    }

    /**
     * 명시된 vendorName 으로 parser 조회.
     *
     * @param vendorName 사용자 명시 vendor 식별자 (필수)
     * @return 매칭 parser
     */
    public Optional<VendorOrderParser> resolveByName(String vendorName) {
        if (vendorName == null || vendorName.isBlank()) {
            return Optional.empty();
        }
        return parsers.stream()
                .filter(p -> vendorName.equals(p.vendorName()))
                .findFirst();
    }

    /**
     * OCR text 휴리스틱으로 parser 자동 감지. 모호하면 첫 매칭 우선.
     *
     * @param ocrText 추출 text
     * @return 매칭 parser (감지 실패 시 empty)
     */
    public Optional<VendorOrderParser> autoDetect(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return Optional.empty();
        }
        return parsers.stream()
                .filter(p -> p.matches(ocrText))
                .findFirst();
    }

    /** 등록된 모든 vendor 이름 — controller / 사용자 노출용. */
    public List<String> registeredVendors() {
        return parsers.stream().map(VendorOrderParser::vendorName).toList();
    }
}
