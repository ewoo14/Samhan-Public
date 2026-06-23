package com.samhanair.logis.accounting.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.security.InternalAuthProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** JournalApprovalGateIT 전용 auth-service MockRestServiceServer 바인딩 config. */
@TestConfiguration
public class ApprovalLineAuthorizeTestConfig {

    @Bean
    public RestClientMockServerHolder approvalLineRestClientMockServerHolder() {
        return new RestClientMockServerHolder();
    }

    @Bean
    @Primary
    public ApprovalLineAuthorizeClient approvalLineAuthorizeClient(
            RestClientMockServerHolder holder,
            InternalAuthProperties internalAuthProperties,
            ObjectMapper objectMapper) {
        RestClient.Builder builder = RestClient.builder();
        holder.server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(false).build();
        return new ApprovalLineAuthorizeClient(
                builder.baseUrl("http://auth-service").build(),
                internalAuthProperties,
                objectMapper);
    }

    public static class RestClientMockServerHolder {
        private MockRestServiceServer server;

        public MockRestServiceServer server() {
            return server;
        }
    }
}
