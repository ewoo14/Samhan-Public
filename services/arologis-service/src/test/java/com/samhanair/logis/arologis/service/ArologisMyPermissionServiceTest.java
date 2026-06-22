package com.samhanair.logis.arologis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.samhanair.logis.arologis.client.AuthPermissionAdminClient;
import com.samhanair.logis.arologis.client.AuthPermissionAdminClient.RolePagePermissionView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** {@link ArologisMyPermissionService} 권한 변환/정규화 단위 테스트. */
class ArologisMyPermissionServiceTest {

    private AuthPermissionAdminClient client;
    private ArologisMyPermissionService service;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(AuthPermissionAdminClient.class);
        service = new ArologisMyPermissionService(client);
    }

    @Test
    void canView와_canEdit를_7개_action_name으로_변환한다() {
        when(client.getRoleMatrix("arologis.")).thenReturn(Map.of(
                "MASTER", Map.of(
                        "arologis.accounting.cashbook",
                        new RolePagePermissionView(
                                "MASTER",
                                "arologis.accounting.cashbook",
                                "현금출납장",
                                true,
                                true))));

        Map<String, List<String>> result = service.getMyPermissions("MASTER");

        assertThat(result.get("arologis.accounting.cashbook"))
                .containsExactly("VIEW", "CREATE", "UPDATE", "DELETE", "RESTORE", "DOWNLOAD", "PRINT");
    }

    @Test
    void arologis_접두_롤을_중앙_롤로_정규화해_해당_row를_조회한다() {
        when(client.getRoleMatrix("arologis.")).thenReturn(Map.of(
                "ACCOUNTANT", Map.of(
                        "arologis.accounting.accounts",
                        new RolePagePermissionView(
                                "ACCOUNTANT",
                                "arologis.accounting.accounts",
                                "계정과목",
                                true,
                                false))));

        Map<String, List<String>> result = service.getMyPermissions("AROLOGIS_ACCOUNTANT");

        assertThat(result)
                .containsOnlyKeys("arologis.accounting.accounts");
        assertThat(result.get("arologis.accounting.accounts"))
                .containsExactly("VIEW");
    }

    @Test
    void 정규화된_롤_row가_없으면_빈_map을_반환한다() {
        when(client.getRoleMatrix("arologis.")).thenReturn(Map.of(
                "MASTER", Map.of(
                        "arologis.admin.permissions",
                        new RolePagePermissionView(
                                "MASTER",
                                "arologis.admin.permissions",
                                "권한 관리",
                                true,
                                true))));

        Map<String, List<String>> result = service.getMyPermissions("AROLOGIS_DRIVER");

        assertThat(result).isEmpty();
    }
}
