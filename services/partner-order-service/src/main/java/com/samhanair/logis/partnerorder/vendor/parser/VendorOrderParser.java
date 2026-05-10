package com.samhanair.logis.partnerorder.vendor.parser;

/**
 * vendor 별 OCR text → {@link ParsedVendorOrder} parser 추상화.
 *
 * <p>구현체:
 * <ul>
 *   <li>{@link AirDesignerOrderParser} — vendorName="에어디자이너" (legacy GAS #10)</li>
 *   <li>{@link JSystemOrderParser} — vendorName="제이시스템" (legacy GAS #14)</li>
 * </ul>
 *
 * <p>새 vendor 추가 시 {@link VendorParserRegistry} 에 등록하여 keyword 식별 가능하게 만듦.
 */
public interface VendorOrderParser {

    /** 본 parser 가 담당하는 vendor 식별자 (예: "에어디자이너"). */
    String vendorName();

    /**
     * OCR text 가 본 vendor 발주서로 보이는지 keyword 휴리스틱.
     * registry 가 자동 매칭에 사용 (사용자가 vendor 를 명시하지 않은 경우).
     */
    boolean matches(String ocrText);

    /**
     * OCR text → 구조화. text 가 vendor 형식이 아니거나 비어있으면 빈 lines 반환.
     */
    ParsedVendorOrder parse(String ocrText);
}
