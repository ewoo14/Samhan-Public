package com.samhanair.logis.product.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * F1.5 품목 attribute 분류기.
 *
 * <p>panelType 은 F4 옵션 매칭 버킷(pickPanelRow/옵션 셀렉터 기준)이며,
 * GAS classifyHome_ 의 catM(WIFI/미내장/인피니트 세분) 이 아니다.
 * BundleExpander 는 F4부터 이 attribute 를 우선 사용하고, 누락/불일치 시 기존
 * 정규식 fallback 으로 견적 출력 parity 를 보존한다.
 */
@Service
public class ProductAttributeClassifier {

    private static final Pattern PANEL = Pattern.compile("판넬|판널|패널", flags());
    private static final Pattern PANEL_MODEL = Pattern.compile("PC[0-9].*", flags());
    private static final Pattern AIR_CLEAN = Pattern.compile("공기청정|공청", flags());
    private static final Pattern BLACK_PANEL = Pattern.compile("블랙", flags());
    private static final Pattern LIFT_PANEL = Pattern.compile("자동승강|승강", flags());
    private static final Pattern PANEL_360 = Pattern.compile("360", flags());
    private static final Pattern REMOTE = Pattern.compile("리모[컨콘]", flags());
    private static final Pattern COLOR_WIRED_REMOTE = Pattern.compile("컬러.*유선.*리모[컨콘]|유선컬러", flags());
    private static final Pattern WIRED_REMOTE = Pattern.compile("유선.*리모[컨콘]", flags());
    private static final Pattern COLOR = Pattern.compile("컬러", flags());

    public String classifyPanelType(String name, String modelCode) {
        String n = normalize(name);
        String m = normalize(modelCode);
        String hay = n + " " + m;
        if (!PANEL.matcher(hay).find() && !PANEL_MODEL.matcher(m).matches()) {
            return null;
        }
        if (AIR_CLEAN.matcher(hay).find()) {
            return "공청";
        }
        if (BLACK_PANEL.matcher(hay).find()) {
            return "블랙";
        }
        if (LIFT_PANEL.matcher(hay).find()) {
            return "승강";
        }
        if (PANEL_360.matcher(hay).find()) {
            return "360";
        }
        return "일반";
    }

    public String classifyRemoteType(String name) {
        String n = normalize(name);
        if (!REMOTE.matcher(n).find()) {
            return null;
        }
        if (COLOR_WIRED_REMOTE.matcher(n).find()) {
            return "컬러유선";
        }
        if (WIRED_REMOTE.matcher(n).find() && !COLOR.matcher(n).find()) {
            return "유선";
        }
        return "무선";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int flags() {
        return Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    }
}
