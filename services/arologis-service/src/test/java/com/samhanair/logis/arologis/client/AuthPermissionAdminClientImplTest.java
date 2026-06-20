package com.samhanair.logis.arologis.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.arologis.client.AuthPermissionAdminClient.RolePagePermissionView;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** auth-service 권한 관리 internal client 실-HTTP 계약 테스트. */
class AuthPermissionAdminClientImplTest {

    private static final String BASE_URL = "http://auth-service-stub";
    private static final String TOKEN = "test-internal-token";
    private static final String CALLER = "arologis-service";
    private static final String MATRIX_ENDPOINT = BASE_URL
            + "/auth/internal/permissions/role-matrix?pagePrefix=arologis.";
    private static final String GRANT_ENDPOINT = BASE_URL
            + "/auth/internal/permissions/role-grant";

    private MockRestServiceServer server;
    private AuthPermissionAdminClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuthPermissionAdminClientImpl(
                builder, new ObjectMapper(), BASE_URL, TOKEN, CALLER);
    }

    @Test
    void role_matrix는_경로와_내부헤더를_보내고_data_matrix를_파싱한다() {
        server.expect(once(), requestTo(MATRIX_ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", "system-internal:" + CALLER))
                .andExpect(header("X-User-Role", CALLER))
                .andRespond(withSuccess("""
                        {"success":true,"data":{
                          "MASTER":{
                            "arologis.admin.permissions":{
                              "roleCode":"MASTER",
                              "pageCode":"arologis.admin.permissions",
                              "displayName":"권한 관리",
                              "canView":true,
                              "canEdit":true
                            }
                          },
                          "MANAGER":{
                            "arologis.dispatches":{
                              "roleCode":"MANAGER",
                              "pageCode":"arologis.dispatches",
                              "displayName":"배차",
                              "canView":true,
                              "canEdit":false
                            }
                          }
                        }}""", MediaType.APPLICATION_JSON));

        Map<String, Map<String, RolePagePermissionView>> matrix =
                client.getRoleMatrix("arologis.");

        assertThat(matrix).containsKeys("MASTER", "MANAGER");
        assertThat(matrix.get("MASTER").get("arologis.admin.permissions"))
                .isEqualTo(new RolePagePermissionView(
                        "MASTER", "arologis.admin.permissions", "권한 관리", true, true));
        assertThat(matrix.get("MANAGER").get("arologis.dispatches"))
                .isEqualTo(new RolePagePermissionView(
                        "MANAGER", "arologis.dispatches", "배차", true, false));
        server.verify();
    }

    @Test
    void role_grant는_요청바디와_actor_헤더를_보내고_data를_파싱한다() {
        server.expect(once(), requestTo(GRANT_ENDPOINT))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", "admin-001"))
                .andExpect(header("X-User-Role", CALLER))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "roleCode":"MANAGER",
                          "pageCode":"arologis.dispatches.manual",
                          "canView":true,
                          "canEdit":false
                        }"""))
                .andRespond(withSuccess("""
                        {"success":true,"data":{
                          "roleCode":"MANAGER",
                          "pageCode":"arologis.dispatches.manual",
                          "displayName":"수동 배차",
                          "canView":true,
                          "canEdit":false
                        }}""", MediaType.APPLICATION_JSON));

        RolePagePermissionView view = client.updateRoleGrant(
                "MANAGER", "arologis.dispatches.manual", true, false, "admin-001");

        assertThat(view).isEqualTo(new RolePagePermissionView(
                "MANAGER", "arologis.dispatches.manual", "수동 배차", true, false));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "BAD_REQUEST,INVALID_INPUT",
            "UNAUTHORIZED,UNAUTHORIZED",
            "FORBIDDEN,FORBIDDEN",
            "NOT_FOUND,NOT_FOUND",
            "CONFLICT,CONFLICT",
            "UNPROCESSABLE_ENTITY,UNPROCESSABLE_ENTITY",
            "TOO_MANY_REQUESTS,TOO_MANY_REQUESTS"
    })
    void auth_4xx는_status별_ErrorCode와_본문_message를_보존한다(
            HttpStatus status, ErrorCode expectedCode) {
        server.expect(once(), requestTo(MATRIX_ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"권한 요청 거부: " + status.value() + "\"}"));

        assertThatThrownBy(() -> client.getRoleMatrix("arologis."))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(expectedCode);
                    assertThat(be.getMessage()).isEqualTo("권한 요청 거부: " + status.value());
                });
        server.verify();
    }

    @Test
    void auth_4xx_error_message_envelope도_message로_보존한다() {
        server.expect(once(), requestTo(GRANT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"pageCode 가 아로로지스 범위가 아닙니다.\"}}"));

        assertThatThrownBy(() -> client.updateRoleGrant(
                "MANAGER", "sales.partner-order.edit", true, true, "admin-001"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
                    assertThat(be.getMessage()).isEqualTo("pageCode 가 아로로지스 범위가 아닙니다.");
                });
        server.verify();
    }

    @Test
    void auth_5xx는_INTERNAL_ERROR와_fallback_message로_매핑한다() {
        server.expect(once(), requestTo(GRANT_ENDPOINT))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.updateRoleGrant(
                "MANAGER", "arologis.dispatches.manual", true, false, "admin-001"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).isEqualTo("권한 할당에 실패했습니다.");
                });
        server.verify();
    }
}
