package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.client.AuthPermissionAdminClient;
import com.samhanair.logis.arologis.client.AuthPermissionAdminClient.RolePagePermissionView;
import com.samhanair.logis.arologis.util.ArologisRoleCodeNormalizer;
import com.samhanair.logis.security.permission.PermissionAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 현재 아로로지스 백오피스 사용자의 page-code 권한을 조회한다.
 *
 * <p>arologis-service 는 auth-service 를 우회해 직접 호출되는 desktop 클라이언트를 지원하므로,
 * 자체 {@code /admin/arologis/permissions/my} API 에서 중앙 auth-service 의 role matrix 를 조회하고
 * 현재 사용자 롤 행만 FE 가 소비하는 {@code pageCode -> action enum name 목록} 형태로 변환한다.
 */
@Service
public class ArologisMyPermissionService {

    private static final String AROLOGIS_PAGE_PREFIX = "arologis.";

    private static final List<String> EDIT_ACTION_NAMES = List.of(
            PermissionAction.CREATE.name(),
            PermissionAction.UPDATE.name(),
            PermissionAction.DELETE.name(),
            PermissionAction.RESTORE.name(),
            PermissionAction.DOWNLOAD.name(),
            PermissionAction.PRINT.name());

    private final AuthPermissionAdminClient authPermissionAdminClient;

    public ArologisMyPermissionService(AuthPermissionAdminClient authPermissionAdminClient) {
        this.authPermissionAdminClient = authPermissionAdminClient;
    }

    /**
     * 원본 {@code X-User-Role} 기준 현재 사용자 권한 목록을 반환한다.
     *
     * <p>{@code AROLOGIS_MASTER} 같은 자체 롤은 중앙 코드({@code MASTER})로 정규화한 뒤
     * {@code arologis.} prefix 매트릭스에서 해당 행만 선택한다. 행이 없으면 빈 map 을 반환하여
     * FE 접근을 fail-closed 로 유지한다.
     *
     * @param rawRoleCode 원본 {@code X-User-Role} 헤더 값
     * @return pageCode → 허용 action enum name 목록
     */
    public Map<String, List<String>> getMyPermissions(String rawRoleCode) {
        String normalizedRoleCode = ArologisRoleCodeNormalizer.normalize(rawRoleCode);
        Map<String, Map<String, RolePagePermissionView>> matrix =
                authPermissionAdminClient.getRoleMatrix(AROLOGIS_PAGE_PREFIX);
        Map<String, RolePagePermissionView> row = matrix.get(normalizedRoleCode);
        if (row == null || row.isEmpty()) {
            return Map.of();
        }
        return toActionNameMap(row);
    }

    /**
     * canView/canEdit 매트릭스 행을 7-action 권한 목록으로 변환한다.
     *
     * <p>{@code canView=true} 는 {@code VIEW} 를 부여하고, {@code canEdit=true} 는 생성·수정·삭제·복구·
     * 다운로드·인쇄 액션을 부여한다. 액션 문자열은 {@link PermissionAction#name()} 값과 동일하다.
     *
     * @param roleRow roleCode 에 해당하는 page-code 권한 행
     * @return pageCode → 허용 action enum name 목록
     */
    private Map<String, List<String>> toActionNameMap(Map<String, RolePagePermissionView> roleRow) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, RolePagePermissionView> entry : roleRow.entrySet()) {
            RolePagePermissionView permission = entry.getValue();
            result.put(entry.getKey(), toActionNames(permission));
        }
        return result;
    }

    private List<String> toActionNames(RolePagePermissionView permission) {
        java.util.ArrayList<String> actions = new java.util.ArrayList<>();
        if (permission.canView()) {
            actions.add(PermissionAction.VIEW.name());
        }
        if (permission.canEdit()) {
            actions.addAll(EDIT_ACTION_NAMES);
        }
        return List.copyOf(actions);
    }
}
