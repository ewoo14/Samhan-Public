package com.samhanair.logis.arologis.domain;

/**
 * 차량 톤수 — Phase 10 W10-1.
 *
 * <p>카톡 차량 그룹 끝 라인 ("1톤" / "2.5톤" / "1.4톤") 에서 파싱.
 *
 * <ul>
 *   <li>{@link #TONNAGE_1} — 1톤 (사용자 제공 13 차량 중 12 차량 해당)</li>
 *   <li>{@link #TONNAGE_1_4} — 1.4톤 (사용자 제공 13 차량 중 1 차량 해당)</li>
 *   <li>{@link #TONNAGE_2_5} — 2.5톤</li>
 *   <li>{@link #TONNAGE_5} — 5톤</li>
 *   <li>{@link #TONNAGE_BIG} — 11톤 / 25톤 등 대형 (확장)</li>
 * </ul>
 */
public enum VehicleTonnage {
    TONNAGE_1,
    TONNAGE_1_4,
    TONNAGE_2_5,
    TONNAGE_5,
    TONNAGE_BIG;

    /**
     * "1" / "1.4" / "2.5" / "5" 등 raw 톤수 문자열 → enum.
     * 미해석 시 {@link #TONNAGE_1} 으로 fallback (skeleton 단계 — 수동 보정 의무).
     */
    public static VehicleTonnage fromRaw(String raw) {
        if (raw == null) {
            return TONNAGE_1;
        }
        String trimmed = raw.trim();
        return switch (trimmed) {
            case "1" -> TONNAGE_1;
            case "1.4" -> TONNAGE_1_4;
            case "2.5" -> TONNAGE_2_5;
            case "5" -> TONNAGE_5;
            case "11", "25" -> TONNAGE_BIG;
            default -> TONNAGE_1;
        };
    }
}
