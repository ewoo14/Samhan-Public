package com.samhanair.logis.arologis.util;

/**
 * 아로로지스 자체 JWT 롤 코드를 중앙 권한 롤 코드로 정규화한다.
 *
 * <p>아로로지스 AdminUserRole 은 {@code AROLOGIS_} 접두를 사용하지만 중앙
 * {@code role_page_permissions} 매트릭스는 {@code MASTER}, {@code MANAGER},
 * {@code ACCOUNTANT} 같은 공통 롤 코드를 사용한다. 접두 없는 중앙 코드는 변경하지 않는다.
 */
public final class ArologisRoleCodeNormalizer {

    private static final String AROLOGIS_ROLE_PREFIX = "AROLOGIS_";

    private ArologisRoleCodeNormalizer() {
    }

    /**
     * {@code AROLOGIS_*} 롤 코드의 접두를 제거하여 중앙 권한 롤 코드로 변환한다.
     *
     * <p>기존 {@code DynamicPermissionClientConfig} 의 정규화 규칙과 동일하게 동작한다.
     * null, blank, 접두 없는 값은 그대로 반환하여 호출부의 기존 fail-closed 흐름을 보존한다.
     *
     * @param roleCode 원본 X-User-Role 또는 중앙 롤 코드
     * @return 중앙 권한 롤 코드
     */
    public static String normalize(String roleCode) {
        if (roleCode != null && roleCode.startsWith(AROLOGIS_ROLE_PREFIX)) {
            return roleCode.substring(AROLOGIS_ROLE_PREFIX.length());
        }
        return roleCode;
    }
}
