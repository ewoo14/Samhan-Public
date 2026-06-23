package com.samhanair.logis.partnerorder.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.InternalAuthProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

/** SlipServiceClient — slip-service partner-order publish internal 계약 회귀 가드. */
class SlipServiceClientTest {

    private static final String TOKEN = "test-token";
    private static final String INTERNAL_CALLER_ID = "00000000-0000-0000-0000-000000000000";
    private static final String FROM_PARTNER_ORDER =
            "http://slip-service/api/v1/slips/from-partner-order";
    private static final String FROM_ORDERS_MERGE =
            "http://slip-service/api/v1/slips/from-orders-merge";

    private MockRestServiceServer server;
    private SlipServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        client = new SlipServiceClient(builder, props);
    }

    @Test
    void publishFromPartnerOrder_200은_경로_헤더_바디를_검증하고_published를_반환한다() {
        server.expect(requestTo(FROM_PARTNER_ORDER))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", INTERNAL_CALLER_ID))
                .andExpect(header("Idempotency-Key", "PO-CONF-P1-1"))
                .andExpect(jsonPath("$.partnerCode").value("P1"))
                .andExpect(jsonPath("$.lines[0].itemName").value("품목-1"))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"slipNo":"SLIP-20260623-001"}}
                        """, MediaType.APPLICATION_JSON));

        SlipServiceClient.PublishResult result =
                client.publishFromPartnerOrder(payload(), "PO-CONF-P1-1");

        assertThat(result.slipNo()).isEqualTo("SLIP-20260623-001");
        assertThat(result.duplicate()).isFalse();
        server.verify();
    }

    @Test
    void publishFromPartnerOrder_409는_body를_파싱해_duplicate를_반환한다() {
        server.expect(requestTo(FROM_PARTNER_ORDER))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", INTERNAL_CALLER_ID))
                .andExpect(header("Idempotency-Key", "PO-CONF-P1-1"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("""
                                {"success":false,"data":{"slipNo":"SLIP-20260623-001"}}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        SlipServiceClient.PublishResult result =
                client.publishFromPartnerOrder(payload(), "PO-CONF-P1-1");

        assertThat(result.slipNo()).isEqualTo("SLIP-20260623-001");
        assertThat(result.duplicate()).isTrue();
        server.verify();
    }

    @Test
    void publishFromPartnerOrder_5xx는_INTERNAL_ERROR() {
        server.expect(requestTo(FROM_PARTNER_ORDER))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertBusinessError(
                () -> client.publishFromPartnerOrder(payload(), "PO-CONF-P1-1"),
                ErrorCode.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void publishFromPartnerOrder_409가_아닌_4xx는_INVALID_INPUT() {
        server.expect(requestTo(FROM_PARTNER_ORDER))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertBusinessError(
                () -> client.publishFromPartnerOrder(payload(), "PO-CONF-P1-1"),
                ErrorCode.INVALID_INPUT);
        server.verify();
    }

    @Test
    void publishFromPartnerOrder_200인데_slipNo가_없으면_INTERNAL_ERROR() {
        server.expect(requestTo(FROM_PARTNER_ORDER))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"data":{}}
                        """, MediaType.APPLICATION_JSON));

        assertBusinessError(
                () -> client.publishFromPartnerOrder(payload(), "PO-CONF-P1-1"),
                ErrorCode.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void publishFromPartnerOrder_빈_payload는_INVALID_INPUT이고_HTTP를_호출하지_않는다() {
        assertBusinessError(
                () -> client.publishFromPartnerOrder(Map.of(), "PO-CONF-P1-1"),
                ErrorCode.INVALID_INPUT);
        server.verify();
    }

    @Test
    void publishFromPartnerOrder_blank_idempotencyKey는_INVALID_INPUT이고_HTTP를_호출하지_않는다() {
        assertBusinessError(
                () -> client.publishFromPartnerOrder(payload(), " "),
                ErrorCode.INVALID_INPUT);
        server.verify();
    }

    @Test
    void publishFromOrdersMerge_200은_병합_경로에서_published를_반환한다() {
        server.expect(requestTo(FROM_ORDERS_MERGE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", INTERNAL_CALLER_ID))
                .andExpect(header("Idempotency-Key", "PO-MRG-20260623-1"))
                .andExpect(MockRestRequestMatchers.content().string(containsString("sourceOrders")))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"slipNo":"SLIP-MRG-20260623-001"}}
                        """, MediaType.APPLICATION_JSON));

        SlipServiceClient.PublishResult result =
                client.publishFromOrdersMerge(mergePayload(), "PO-MRG-20260623-1");

        assertThat(result.slipNo()).isEqualTo("SLIP-MRG-20260623-001");
        assertThat(result.duplicate()).isFalse();
        server.verify();
    }

    @Test
    void publishFromOrdersMerge_409는_병합_경로에서_duplicate를_반환한다() {
        server.expect(requestTo(FROM_ORDERS_MERGE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", INTERNAL_CALLER_ID))
                .andExpect(header("Idempotency-Key", "PO-MRG-20260623-1"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .body("""
                                {"success":false,"data":{"slipNo":"SLIP-MRG-20260623-001"}}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        SlipServiceClient.PublishResult result =
                client.publishFromOrdersMerge(mergePayload(), "PO-MRG-20260623-1");

        assertThat(result.slipNo()).isEqualTo("SLIP-MRG-20260623-001");
        assertThat(result.duplicate()).isTrue();
        server.verify();
    }

    private static Map<String, Object> payload() {
        return Map.of(
                "partnerCode", "P1",
                "warehouseCode", "MAIN",
                "lines", List.of(Map.of(
                        "itemName", "품목-1",
                        "quantity", 2)));
    }

    private static Map<String, Object> mergePayload() {
        return Map.of(
                "sourceOrders", List.of("PO-1", "PO-2"),
                "warehouseCode", "MAIN",
                "lines", List.of(Map.of(
                        "itemName", "묶음품목",
                        "quantity", 3)));
    }

    private static void assertBusinessError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ErrorCode errorCode) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(errorCode));
    }
}
