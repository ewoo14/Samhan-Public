package com.samhanair.logis.product.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * F1.5 품목 attribute 분류기.
 *
 * <p>견적 출력/BundleExpander 런타임 매칭은 변경하지 않고, GAS classifyHome_ 판넬 분기와
 * BundleExpander 리모컨 옵션 정규식 결과만 Product 컬럼에 적재한다.
 */
@Service
public class ProductAttributeClassifier {

    private static final Pattern PANEL = Pattern.compile("판넬|판널|패널", flags());
    private static final Pattern PANEL_MODEL = Pattern.compile("PC[0-9].*", flags());
    private static final Pattern AIR_CLEAN = Pattern.compile("공기청정|공청", flags());
    private static final Pattern WIFI = Pattern.compile("WIFI", flags());
    private static final Pattern NON_BUILT_IN = Pattern.compile("미내장", flags());
    private static final Pattern INFINITE = Pattern.compile("인피니트", flags());
    private static final Pattern BLACK_PANEL = Pattern.compile("블랙판넬|블랙\\s*(판넬|판널|패널)", flags());
    private static final Pattern LIFT_PANEL = Pattern.compile("자동승강|승강", flags());
    private static final Pattern PANEL_360 = Pattern.compile("360", flags());
    private static final Pattern REMOTE = Pattern.compile("리모[컨콘]", flags());
    private static final Pattern COLOR_WIRED_REMOTE = Pattern.compile("컬러유선리모컨|유선컬러", flags());
    private static final Pattern WIRED_REMOTE = Pattern.compile("유선리모컨", flags());
    private static final Pattern COLOR = Pattern.compile("컬러", flags());

    public String classifyPanelType(String name, String modelCode) {
        String n = normalize(name);
        String m = normalize(modelCode);
        String hay = n + " " + m;
        if (!PANEL.matcher(hay).find() && !PANEL_MODEL.matcher(m).matches()) {
            return null;
        }
        if (AIR_CLEAN.matcher(hay).find() && WIFI.matcher(hay).find()) {
            return "공기청정 WIFI";
        }
        if (AIR_CLEAN.matcher(hay).find() && NON_BUILT_IN.matcher(hay).find()) {
            return "공기청정 미내장";
        }
        if (AIR_CLEAN.matcher(hay).find()) {
            return "공청판넬";
        }
        if (WIFI.matcher(hay).find()) {
            return "WIFI";
        }
        if (NON_BUILT_IN.matcher(hay).find()) {
            return "미내장";
        }
        if (INFINITE.matcher(hay).find()) {
            return "인피니트";
        }
        if (BLACK_PANEL.matcher(hay).find()) {
            return "블랙판넬";
        }
        if (LIFT_PANEL.matcher(hay).find()) {
            return "승강판넬";
        }
        if (PANEL_360.matcher(hay).find()) {
            return "360";
        }
        return null;
    }

    public String classifyRemoteType(String name) {
        String n = normalize(name);
        if (!REMOTE.matcher(n).find()) {
            return null;
        }
        if (COLOR_WIRED_REMOTE.matcher(n).find()) {
            return "컬러유선리모컨";
        }
        if (WIRED_REMOTE.matcher(n).find() && !COLOR.matcher(n).find()) {
            return "유선리모컨";
        }
        return "무선리모컨";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int flags() {
        return Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    }
}
