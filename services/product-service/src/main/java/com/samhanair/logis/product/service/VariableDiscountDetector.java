package com.samhanair.logis.product.service;

import com.samhanair.logis.product.domain.MaterialKey;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 변동DC 자동 감지기 — Apps Script 의 시트 수식 절대참조 매칭 룰을 Java 로 포팅.
 *
 * <p><b>출처</b>:
 * <ul>
 *     <li>estimate Code.js 라인 428 / 556 / 1742 — useK2 룰 1 ({@code $L$2})</li>
 *     <li>partner-order Code.js 라인 658 / 780 / 1906 — 동일 룰</li>
 *     <li>DOMAIN-EXTENSIONS §1 — 4 룰 매트릭스</li>
 *     <li>formulas.json grep 결과: $L$2 (홈/상업 멀티) / $D$4 (245 hits) / $D$7 (45 hits) / $D$8 (10 hits) / $I$1 (구형 50%)</li>
 * </ul>
 *
 * <p>본 detector 는 시드 1회 + 신규 등록 시 자동 판정. Layer 4 도메인 메서드 의미 정렬
 * (feedback_pm_integration_build_check.md):
 * <ul>
 *     <li>{@link #detectHasVariableDiscount(String)} = "useK2 활성 여부 판정" (홈/상업 한정)</li>
 *     <li>{@link #detectMaterialKey(String)} = "자재가격 시트의 어느 master cell (D4/D7/D8) 을 참조하는지 판정"</li>
 *     <li>{@link #detectLegacyDiscount(String)} = "구형 50% DC 트리거 여부 판정" (구형 시트만)</li>
 *     <li>{@link #detectDiscountFlags(String)} = "모델명 prefix 7-룰 매칭하여 6-비트 flag 사전계산"</li>
 * </ul>
 */
@Service
public class VariableDiscountDetector {

    /** 룰 1 — `$L$2` 절대참조 패턴 (홈/상업 멀티 useK2). */
    private static final Pattern RULE_L2 = Pattern.compile("\\$L\\$2");
    /** 룰 2 — `$D$4` / `$D$7` / `$D$8` 절대참조 (싱글 세트/구성품 자재 옵션). */
    private static final Pattern RULE_D_NUM = Pattern.compile("\\$D\\$([478])\\b");
    /** 룰 3 — `$I$1` 절대참조 (구형 50% DC). */
    private static final Pattern RULE_I1 = Pattern.compile("\\$I\\$1");

    /** 모델명 prefix 7-룰 정규식 (DOMAIN-EXTENSIONS §1 + getModelFlags). */
    private static final Pattern P_360 = Pattern.compile("^(?i).*360.*");
    private static final Pattern P_4WAY = Pattern.compile("^(?i).*4way.*");
    private static final Pattern P_1WAY = Pattern.compile("^(?i).*1way.*");
    private static final Pattern P_STAND = Pattern.compile("^(?i).*(stand|스탠드).*");
    private static final Pattern P_DELUXE = Pattern.compile("^(?i).*(deluxe|디럭스).*");
    private static final Pattern P_GRADE1 = Pattern.compile("^(?i).*(1등급|grade.?1|G1).*");

    /**
     * 룰 1 — priceFormula (시트 D/E/F/G/H 단가 셀의 raw 수식) 에 `$L$2` 포함 시
     * hasVariableDiscount=TRUE.
     *
     * @param priceFormula formulas.json 에서 추출한 단가 컬럼 수식 (null/blank 허용)
     * @return 변동DC 적용 여부
     */
    public boolean detectHasVariableDiscount(String priceFormula) {
        if (priceFormula == null || priceFormula.isBlank()) {
            return false;
        }
        return RULE_L2.matcher(priceFormula).find();
    }

    /**
     * 룰 2 — priceFormula 의 `$D$N` 패턴 (N ∈ {4, 7, 8}) → MaterialKey enum 매핑.
     * 가장 먼저 발견된 N 채택 (다중 매치 시 D4 우선 — 출현 빈도 245 hits).
     *
     * @param priceFormula 단가 컬럼 수식
     * @return D4/D7/D8 중 하나, 없으면 empty
     */
    public Optional<MaterialKey> detectMaterialKey(String priceFormula) {
        if (priceFormula == null || priceFormula.isBlank()) {
            return Optional.empty();
        }
        Matcher m = RULE_D_NUM.matcher(priceFormula);
        if (!m.find()) {
            return Optional.empty();
        }
        return switch (m.group(1)) {
            case "4" -> Optional.of(MaterialKey.D4);
            case "7" -> Optional.of(MaterialKey.D7);
            case "8" -> Optional.of(MaterialKey.D8);
            default -> Optional.empty();
        };
    }

    /**
     * 룰 3 — 구형 시트 F열 수식에 `$I$1` 포함 시 legacyDiscountFlag=TRUE +
     * fixedDiscountRate=50.00(%).
     *
     * @param formulaF F열 수식 (구형 시트 한정)
     * @return 구형 50% DC 트리거 여부
     */
    public boolean detectLegacyDiscount(String formulaF) {
        if (formulaF == null || formulaF.isBlank()) {
            return false;
        }
        return RULE_I1.matcher(formulaF).find();
    }

    /** 구형 50% DC fixed rate. */
    public BigDecimal legacyFixedDiscountRate() {
        return new BigDecimal("50.00");
    }

    /**
     * 모델명 prefix 7-룰 매칭 → 6-비트 flag 사전계산.
     * 비트 순서: is360, is4way, is1way, isStand, isDeluxe, isGrade1.
     *
     * @param modelCode 시트 B열 모델명
     * @return "010110" 형태의 6-자리 0/1 문자열
     */
    public String detectDiscountFlags(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) {
            return "000000";
        }
        StringBuilder sb = new StringBuilder(6);
        sb.append(P_360.matcher(modelCode).matches() ? '1' : '0');
        sb.append(P_4WAY.matcher(modelCode).matches() ? '1' : '0');
        sb.append(P_1WAY.matcher(modelCode).matches() ? '1' : '0');
        sb.append(P_STAND.matcher(modelCode).matches() ? '1' : '0');
        sb.append(P_DELUXE.matcher(modelCode).matches() ? '1' : '0');
        sb.append(P_GRADE1.matcher(modelCode).matches() ? '1' : '0');
        return sb.toString();
    }
}
