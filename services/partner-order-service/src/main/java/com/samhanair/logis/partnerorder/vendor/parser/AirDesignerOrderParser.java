package com.samhanair.logis.partnerorder.vendor.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 에어디자이너 발주서 parser (legacy GAS #10).
 *
 * <p>발주서 패턴 (휴리스틱 정규식, OCR 노이즈 허용):
 * <pre>
 *   [헤더] "에어디자이너" / "AIR DESIGNER" 키워드
 *   [거래처] "거래처: P-XXXX" 또는 "partnerCode: P-XXXX"
 *   [라인]  "1. 헬로멀티 5kW [HM-5000]  2개  1,000,000원"
 *   [총액]  "합계: 2,000,000원" 또는 "Total: 2,000,000"
 * </pre>
 *
 * <p>OCR 결과는 공백/개행 노이즈가 많으므로 라인 단위 split 후 line-level 정규식 매칭.
 * 단가/수량 누락 라인은 skip (cross-check 시 controller 가 시트 lookup 으로 보충).
 */
@Component
public class AirDesignerOrderParser implements VendorOrderParser {

    public static final String VENDOR_NAME = "에어디자이너";

    private static final Pattern PARTNER_CODE_PATTERN =
            Pattern.compile("(?:거래처|partnerCode)\\s*[:：]\\s*(P-[A-Za-z0-9-]+)");
    /** 라인 패턴: 번호. 제품명 [모델코드] 수량개 단가원. 단가는 천단위 콤마 허용. */
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*\\d+[\\.\\)]\\s*(?<name>[^\\[\\n]+?)\\s*\\[(?<model>[A-Za-z0-9\\-]+)\\]\\s*"
                    + "(?<qty>\\d+)\\s*(?:개|EA|ea)\\s+(?<price>[\\d,]+)\\s*원?",
            Pattern.MULTILINE);
    private static final Pattern TOTAL_PATTERN =
            Pattern.compile("(?:합계|Total|총액)\\s*[:：]?\\s*(?<total>[\\d,]+)\\s*원?",
                    Pattern.CASE_INSENSITIVE);

    @Override
    public String vendorName() {
        return VENDOR_NAME;
    }

    @Override
    public boolean matches(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return false;
        }
        String upper = ocrText.toUpperCase();
        return ocrText.contains(VENDOR_NAME) || upper.contains("AIR DESIGNER");
    }

    @Override
    public ParsedVendorOrder parse(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return ParsedVendorOrder.empty(VENDOR_NAME);
        }

        String partnerCode = null;
        Matcher pcMatch = PARTNER_CODE_PATTERN.matcher(ocrText);
        if (pcMatch.find()) {
            partnerCode = pcMatch.group(1);
        }

        List<ParsedVendorOrder.Line> lines = new ArrayList<>();
        Matcher lineMatch = LINE_PATTERN.matcher(ocrText);
        while (lineMatch.find()) {
            String name = lineMatch.group("name").trim();
            String model = lineMatch.group("model").trim();
            int qty;
            BigDecimal price;
            try {
                qty = Integer.parseInt(lineMatch.group("qty"));
                price = new BigDecimal(lineMatch.group("price").replace(",", ""));
            } catch (NumberFormatException ignore) {
                continue;
            }
            if (qty <= 0) {
                continue;
            }
            lines.add(new ParsedVendorOrder.Line(name, model, qty, price));
        }

        BigDecimal total = BigDecimal.ZERO;
        Matcher totalMatch = TOTAL_PATTERN.matcher(ocrText);
        if (totalMatch.find()) {
            try {
                total = new BigDecimal(totalMatch.group("total").replace(",", ""));
            } catch (NumberFormatException ignore) {
                // total 누락 — controller 가 라인 합산 사용
            }
        }

        return new ParsedVendorOrder(VENDOR_NAME, partnerCode, lines, total);
    }
}
