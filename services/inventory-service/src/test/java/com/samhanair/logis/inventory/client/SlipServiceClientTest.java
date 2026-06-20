package com.samhanair.logis.inventory.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.InternalAuthProperties;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** inventory-service slip-service GET /slips/outbound RestClient contract test. */
class SlipServiceClientTest {

    private static final String TOKEN = "test-token-xyz";
    private static final String ENDPOINT = "http://slip-service/slips/outbound?from=2026-06-01&to=2026-06-30";

    private MockRestServiceServer server;
    private SlipServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        client = new SlipServiceClient(builder, props, objectMapper);
    }

    @Test
    void getOutboundSlips_sendsFromToQueryAndToken_andParsesFlattenItems() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "code": "OK",
                          "message": "성공",
                          "data": [{
                            "slipNo": "2026/06/20-001",
                            "slipDate": "2026-06-20",
                            "partnerCode": "P-2026-0001",
                            "partnerName": "삼한테스트",
                            "productCode": "PRD-001",
                            "productName": "테스트 품목",
                            "quantity": 3
                          }, {
                            "slipNo": "2026/06/20-002",
                            "slipDate": "2026-06-21",
                            "partnerCode": null,
                            "partnerName": "미등록거래처",
                            "productCode": "PRD-002",
                            "productName": "두번째 품목",
                            "quantity": 5
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<OutboundSlipLineSummary> result = client.getOutboundSlips(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).slipNo()).isEqualTo("2026/06/20-001");
        assertThat(result.get(0).slipDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(result.get(0).partnerCode()).isEqualTo("P-2026-0001");
        assertThat(result.get(0).productCode()).isEqualTo("PRD-001");
        assertThat(result.get(0).quantity()).isEqualTo(3);
        assertThat(result.get(1).partnerCode()).isNull();
        assertThat(result.get(1).quantity()).isEqualTo(5);
        server.verify();
    }

    @Test
    void getOutboundSlips_emptyData_returnsEmptyList() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"code":"OK","message":"성공","data":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getOutboundSlips(LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30))).isEmpty();
        server.verify();
    }

    @Test
    void getOutboundSlips_4xx_mapsToInvalidInput() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.getOutboundSlips(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
        server.verify();
    }

    @Test
    void getOutboundSlips_5xx_mapsToInternalError() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getOutboundSlips(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    @Test
    void getOutboundSlips_missingData_mapsToInternalError() {
        server.expect(requestTo("http://slip-service/slips/outbound?from=2026-06-01&to=2026-06-02"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"code":"OK","message":"성공"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOutboundSlips(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    @Test
    void getOutboundSlips_invalidRange_failsBeforeCallingServer() {
        assertThatThrownBy(() -> client.getOutboundSlips(
                        LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
