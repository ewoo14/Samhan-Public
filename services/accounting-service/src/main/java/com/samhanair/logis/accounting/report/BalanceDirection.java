package com.samhanair.logis.accounting.report;

/**
 * 계정 잔액의 정상 방향.
 *
 * <p>채권/자산성 계정은 차변잔액, 채무/부채성 계정은 대변잔액을 정상 방향으로 본다.
 */
public enum BalanceDirection {
    DEBIT("차변잔액"),
    CREDIT("대변잔액");

    private final String displayName;

    BalanceDirection(String displayName) {
        this.displayName = displayName;
    }

    /** 사용자 표시명. */
    public String getDisplayName() {
        return displayName;
    }
}
