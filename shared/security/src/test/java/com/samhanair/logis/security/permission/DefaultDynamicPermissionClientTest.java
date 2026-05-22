package com.samhanair.logis.security.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DefaultDynamicPermissionClientTest {

    @Test
    void canEdit_calls_auth_internal_permission_check_with_internal_token() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DefaultDynamicPermissionClient client =
                new DefaultDynamicPermissionClient(builder, "test-internal-token", "accounting-service");

        server.expect(requestTo("http://auth-service/auth/internal/permissions/check"
                        + "?roleCode=ACCOUNTANT&pageCode=accounting.tax-invoice.emit-nts&type=EDIT"))
                .andExpect(header("X-Internal-Token", "test-internal-token"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"allowed\":true}}",
                        MediaType.APPLICATION_JSON));

        boolean allowed = client.canEdit("ACCOUNTANT", "accounting.tax-invoice.emit-nts");

        assertThat(allowed).isTrue();
        server.verify();
    }
}
