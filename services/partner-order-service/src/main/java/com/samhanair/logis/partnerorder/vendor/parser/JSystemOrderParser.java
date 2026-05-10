package com.samhanair.logis.partnerorder.vendor.parser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 제이시스템 발주서 parser (legacy GAS #14).
 *
 * <p>발주서 패턴 (table-style 휴리스틱):
 * <pre>
 *   [헤더] "제이시스템" / "JSYSTEM" / "J-SYSTEM" 키워드
 *   [거래처] "거래처코드 P-XXXX" 또는 "Partner P-XXXX"
 *   [라인]  "HM-7000  헬로멀티 7kW  3 EA  1,500,000"
 *   [총액]  "TOTAL  4,500,000"
 * </pre>
 *
 * <p>에어디자이너와 라인 순서가 반대 (모델코드 → 제품명) — table 형식 출력.
 */
@Component
public class JSystemOrderParser implements VendorOrderParser {

    public static final String VENDOR_NAME = "제이시스템";

    private static final Pattern PARTNER_CODE_PATTERN =
            Pattern.compile("(?:거래처코드|Partner|partnerCode)\\s*[:：]?\\s*(P-[A-Za-z0-9-]+)",
                    Pattern.CASE_INSENSITIVE);
    /** 라인 패턴: [모델코드] [제품명] [수량] EA [단가]. 제품명은 한글/영숫자 혼재 허용. */
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(?<model>[A-Z]{2,}-\\d{3,})\\s+(?<name>.+?)\\s+"
                    + "(?<qty>\\d+)\\s*(?:EA|ea|개)\\s+(?<price>[\\d,]+)",
            Pattern.MULTILINE);
    private static final Pattern TOTAL_PATTERN =
            Pattern.compile("(?:TOTAL|합계|총액)\\s*[:：]?\\s*(?<total>[\\d,]+)\\s*원?",
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
        return ocrText.contains(VENDOR_NAME)
                || upper.contains("JSYSTEM")
                || upper.contains("J-SYSTEM")
                || upper.contains("J SYSTEM");
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
            String model = lineMatch.group("model").trim();
            String name = lineMatch.group("name").trim();
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
