package com.samhanair.logis.arologis.security;

/**
 * 아로로지스 서비스가 사용하는 동적 권한 page-code 상수.
 *
 * <p>auth-service 의 PageCode enum 은 중앙 인증 도메인 소유이므로 arologis-service 에서는
 * import 하지 않는다. 컨트롤러 {@code @RequirePermission.page()} 는 본 compile-time 상수를
 * 사용해 오타가 리뷰와 테스트에서 즉시 드러나도록 한다.
 */
public final class ArologisPageCodes {

    public static final String ACCOUNTING_ACCOUNTS = "arologis.accounting.accounts";
    public static final String ACCOUNTING_CASHBOOK = "arologis.accounting.cashbook";
    public static final String ACCOUNTING_SUMMARY = "arologis.accounting.summary";
    public static final String ADMIN_PERMISSIONS = "arologis.admin.permissions";
    public static final String DISPATCH_ADMIN = "arologis.dispatch.admin";
    public static final String DISPATCH_OPS = "arologis.dispatch.ops";
    public static final String DRIVER = "arologis.driver";
    public static final String EDIT_REQUESTS = "arologis.edit-requests";
    public static final String EDIT_REQUESTS_DECIDE = "arologis.edit-requests.decide";
    public static final String HR_DEPARTMENTS = "arologis.hr.departments";
    public static final String HR_EMPLOYEES = "arologis.hr.employees";
    public static final String REGION = "arologis.region";
    public static final String REGION_MANAGE = "arologis.region.manage";

    private ArologisPageCodes() {
    }
}
