package com.samhanair.logis.partnerorder.client;

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

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.partnerorder.client.SlipServiceClient.PublishResult;
import com.samhanair.logis.security.InternalAuthProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** slip-service 주문 전표 발행 internal client 실-HTTP 계약 테스트. */
class SlipServiceClientTest {

    private static final String TOKEN = "test-internal-token";
    private static final String INTERNAL_CALLER_ID = "00000000-0000-0000-0000-000000000000";
    private static final String PARTNER_ORDER_ENDPOINT =
            "http://slip-service/api/v1/slips/from-partner-order";
    private static final String ORDERS_MERGE_ENDPOINT =
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
    void from_partner_order는_경로_헤더_Idempotency_Key_바디를_보내고_200_slipNo를_추출한다() {
        server.expect(once(), requestTo(PARTNER_ORDER_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", INTERNAL_CALLER_ID))
                .andExpect(header("Idempotency-Key", "PO-CONF-P-SP0841-20260520-1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "partnerCode":"P-SP0841",
                          "warehouseCode":"WH-SEOUL",
                          "orderNo":"2026/05/20-1",
                          "lines":[{"modelCode":"AJ040RXH4BC1","quantity":2}]
                        }"""))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"slipNo":"2026/05/20-1"}}
                        """, MediaType.APPLICATION_JSON));

        PublishResult result = client.publishFromPartnerOrder(
                partnerOrderPayload(), "PO-CONF-P-SP0841-20260520-1");

        assertThat(result.slipNo()).isEqualTo("2026/05/20-1");
        assertThat(result.duplicate()).isFalse();
        server.verify();
    }

    @Test
    void from_partner_order_409는_duplicate로_통과하고_data_slipNo를_추출한다() {
        server.expect(once(), requestTo(PARTNER_ORDER_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "PO-CONF-P-SP0841-20260520-1"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"data\":{\"slipNo\":\"2026/05/20-1\"}}"));

        PublishResult result = client.publishFromPartnerOrder(
                partnerOrderPayload(), "PO-CONF-P-SP0841-20260520-1");

        assertThat(result.slipNo()).isEqualTo("2026/05/20-1");
        assertThat(result.duplicate()).isTrue();
        server.verify();
    }

    @Test
    void from_orders_merge는_경로_헤더_Idempotency_Key_바디를_보내고_direct_slipNo도_추출한다() {
        server.expect(once(), requestTo(ORDERS_MERGE_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(header("X-User-Id", INTERNAL_CALLER_ID))
                .andExpect(header("Idempotency-Key", "PO-MRG-P-SP0841-20260520"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "sourceOrders":["2026/05/20-1","2026/05/20-2"],
                          "warehouseCode":"WH-SEOUL",
                          "lines":[{"modelCode":"AR07B9350HZ","quantity":1}]
                        }"""))
                .andRespond(withSuccess("""
                        {"success":true,"slipNo":"2026/05/20-M"}
                        """, MediaType.APPLICATION_JSON));

        PublishResult result = client.publishFromOrdersMerge(
                mergePayload(), "PO-MRG-P-SP0841-20260520");

        assertThat(result.slipNo()).isEqualTo("2026/05/20-M");
        assertThat(result.duplicate()).isFalse();
        server.verify();
    }

    @Test
    void from_orders_merge_409는_duplicate로_통과하고_data_slipNo를_추출한다() {
        server.expect(once(), requestTo(ORDERS_MERGE_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "PO-MRG-P-SP0841-20260520"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"data\":{\"slipNo\":\"2026/05/20-M\"}}"));

        PublishResult result = client.publishFromOrdersMerge(
                mergePayload(), "PO-MRG-P-SP0841-20260520");

        assertThat(result.slipNo()).isEqualTo("2026/05/20-M");
        assertThat(result.duplicate()).isTrue();
        server.verify();
    }

    @Test
    void slip_service_5xx는_INTERNAL_ERROR로_전파한다() {
        server.expect(once(), requestTo(PARTNER_ORDER_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.publishFromPartnerOrder(
                partnerOrderPayload(), "PO-CONF-P-SP0841-20260520-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(be.getMessage()).startsWith("slip-service 5xx:");
                });
        server.verify();
    }

    @Test
    void slip_service_409에_slipNo가_없으면_INTERNAL_ERROR로_실패한다() {
        server.expect(once(), requestTo(PARTNER_ORDER_ENDPOINT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"data\":{}}"));

        assertThatThrownBy(() -> client.publishFromPartnerOrder(
                partnerOrderPayload(), "PO-CONF-P-SP0841-20260520-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }

    private Map<String, Object> partnerOrderPayload() {
        return Map.of(
                "partnerCode", "P-SP0841",
                "warehouseCode", "WH-SEOUL",
                "orderNo", "2026/05/20-1",
                "lines", List.of(Map.of("modelCode", "AJ040RXH4BC1", "quantity", 2)));
    }

    private Map<String, Object> mergePayload() {
        return Map.of(
                "sourceOrders", List.of("2026/05/20-1", "2026/05/20-2"),
                "warehouseCode", "WH-SEOUL",
                "lines", List.of(Map.of("modelCode", "AR07B9350HZ", "quantity", 1)));
    }
}
