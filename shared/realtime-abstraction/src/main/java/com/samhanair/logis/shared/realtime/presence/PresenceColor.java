package com.samhanair.logis.shared.realtime.presence;

/**
 * 협업 presence avatar 색상 팔레트.
 *
 * <p>Google Docs 식 동시 접속자 표시를 위해 서버가 userId hash 로 8색 중 하나를 결정한다.
 * 클라이언트는 enum name 만 화면 텍스트로 노출하지 않고 색상 렌더링에만 사용한다.
 */
public enum PresenceColor {
    BLUE("#2563EB"),
    GREEN("#15803D"),
    AMBER("#B45309"),
    ROSE("#E11D48"),
    VIOLET("#7C3AED"),
    CYAN("#0E7490"),
    LIME("#4D7C0F"),
    PINK("#DB2777");

    private final String hex;

    PresenceColor(String hex) {
        this.hex = hex;
    }

    public String hex() {
        return hex;
    }

    public static PresenceColor fromUserId(String userId) {
        PresenceColor[] palette = values();
        int index = Math.floorMod(userId == null ? 0 : userId.hashCode(), palette.length);
        return palette[index];
    }
}
