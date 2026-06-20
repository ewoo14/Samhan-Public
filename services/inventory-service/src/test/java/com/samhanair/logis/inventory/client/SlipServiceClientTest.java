package com.samhanair.logis.inventory.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** inventory-service DPS 비교용 slip-service internal 출고전표 라인 조회 contract test. */
class SlipServiceClientTest {

    private static final String TOKEN = "test-token-xyz";
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);
    private static final String ENDPOINT =
            "http://slip-service/internal/slips/outbound?from=2026-06-01&to=2026-06-30";

    private MockRestServiceServer server;
    private SlipServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        client = new SlipServiceClient(builder, props, objectMapper);
    }

    @Test
    void getOutboundSlips_callsInternalEndpointWithToken_andParsesLines() {
        String json = """
                {
                  "success": true,
                  "code": "OK",
                  "message": "성공",
                  "data": [{
                    "slipNo": "2026/06/10-001",
                    "slipDate": "2026-06-10",
                    "partnerCode": "P001",
                    "partnerName": "삼한테스트",
                    "productCode": "MODEL-DPS",
                    "productName": "DPS 품목",
                    "quantity": 7
                  }]
                }
                """;

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<OutboundSlipLineSummary> result = client.getOutboundSlips(FROM, TO);

        assertThat(result).hasSize(1);
        OutboundSlipLineSummary line = result.get(0);
        assertThat(line.slipNo()).isEqualTo("2026/06/10-001");
        assertThat(line.slipDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(line.partnerCode()).isEqualTo("P001");
        assertThat(line.partnerName()).isEqualTo("삼한테스트");
        assertThat(line.productCode()).isEqualTo("MODEL-DPS");
        assertThat(line.productName()).isEqualTo("DPS 품목");
        assertThat(line.quantity()).isEqualTo(7);
        server.verify();
    }

    @Test
    void getOutboundSlips_4xx_mapsToInvalidInput() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.getOutboundSlips(FROM, TO))
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

        assertThatThrownBy(() -> client.getOutboundSlips(FROM, TO))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    @Test
    void getOutboundSlips_missingData_mapsToInternalError() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"code":"OK","message":"성공"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getOutboundSlips(FROM, TO))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    @Test
    void getOutboundSlips_nullDate_mapsToInvalidInputBeforeCallingServer() {
        assertThatThrownBy(() -> client.getOutboundSlips(null, TO))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    void getOutboundSlips_fromAfterTo_mapsToInvalidInputBeforeCallingServer() {
        assertThatThrownBy(() -> client.getOutboundSlips(TO, FROM))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
