package com.samhanair.logis.partnerorder.mig8.client;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** AccountingMig8OrderClient — accounting-service MIG-8 export internal 계약 회귀 가드. */
class AccountingMig8OrderClientTest {

    private static final String TOKEN = "test-token";
    private static final String ENDPOINT =
            "http://accounting-service/internal/accounting/mig8-orders";

    private MockRestServiceServer server;
    private AccountingMig8OrderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        client = new AccountingMig8OrderClient(builder, props, new ObjectMapper());
    }

    @Test
    void fetchMig8Orders_200은_금액_UUID_날짜_content_last_lines를_파싱한다() {
        UUID partnerId = UUID.fromString("00000000-0000-0000-0000-000000000801");
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000901");

        // 통화 필드는 문자열 형태로 픽스처 — 클라 decimal() 헬퍼가 value.asText()→new BigDecimal(...)
        // 로 파싱하므로 문자열이 정밀 라운드트립 경로(고정밀 unitPrice 도 손실 없음). 실 wire 는
        // Jackson 기본 BigDecimal numeric 직렬화이나, 클라 parse 가 numeric/string 양립이라 계약상 무해
        // (numeric 픽스처는 readTree DoubleNode→asText 로 고정밀에서 flaky 위험이라 문자열 채택).
        server.expect(requestTo(ENDPOINT + "?page=1&size=50"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "content": [
                              {
                                "orderNo": "ORD-20260623-001",
                                "partnerId": "00000000-0000-0000-0000-000000000801",
                                "partnerName": "삼한거래처",
                                "managerName": "홍길동",
                                "progressStatus": "CONFIRMED",
                                "validUntil": "2026-07-31",
                                "paymentTerms": "월말",
                                "reference": "MIG8-REF",
                                "totalSupplyAmount": "1234567.89",
                                "totalVatAmount": "123456.78",
                                "linkedSlipNo": "SLIP-20260623-001",
                                "externalRef": "EC-ORD-1",
                                "lines": [
                                  {
                                    "lineNo": 1,
                                    "productId": "00000000-0000-0000-0000-000000000901",
                                    "itemName": "품목-1",
                                    "quantity": "10.5",
                                    "unitPrice": "117577.894285",
                                    "supplyAmount": "1234567.89",
                                    "vatAmount": "123456.78",
                                    "itemDueDate": "2026-08-05"
                                  }
                                ]
                              }
                            ],
                            "last": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Mig8OrderPage page = client.fetchMig8Orders(1, 50);

        assertThat(page.last()).isTrue();
        assertThat(page.content()).hasSize(1);
        Mig8OrderExport order = page.content().get(0);
        assertThat(order.orderNo()).isEqualTo("ORD-20260623-001");
        assertThat(order.partnerId()).isEqualTo(partnerId);
        assertThat(order.partnerName()).isEqualTo("삼한거래처");
        assertThat(order.managerName()).isEqualTo("홍길동");
        assertThat(order.progressStatus()).isEqualTo("CONFIRMED");
        assertThat(order.validUntil()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(order.paymentTerms()).isEqualTo("월말");
        assertThat(order.reference()).isEqualTo("MIG8-REF");
        assertThat(order.totalSupplyAmount()).isEqualByComparingTo(new BigDecimal("1234567.89"));
        assertThat(order.totalVatAmount()).isEqualByComparingTo(new BigDecimal("123456.78"));
        assertThat(order.linkedSlipNo()).isEqualTo("SLIP-20260623-001");
        assertThat(order.externalRef()).isEqualTo("EC-ORD-1");

        assertThat(order.lines()).hasSize(1);
        Mig8OrderLineExport line = order.lines().get(0);
        assertThat(line.lineNo()).isEqualTo(1);
        assertThat(line.productId()).isEqualTo(productId);
        assertThat(line.itemName()).isEqualTo("품목-1");
        assertThat(line.quantity()).isEqualByComparingTo(new BigDecimal("10.5"));
        assertThat(line.unitPrice()).isEqualByComparingTo(new BigDecimal("117577.894285"));
        assertThat(line.supplyAmount()).isEqualByComparingTo(new BigDecimal("1234567.89"));
        assertThat(line.vatAmount()).isEqualByComparingTo(new BigDecimal("123456.78"));
        assertThat(line.itemDueDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        server.verify();
    }

    @Test
    void fetchMig8Orders_401은_UNAUTHORIZED() {
        server.expect(requestTo(ENDPOINT + "?page=0&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertBusinessError(() -> client.fetchMig8Orders(0, 10), ErrorCode.UNAUTHORIZED);
        server.verify();
    }

    @Test
    void fetchMig8Orders_403은_FORBIDDEN() {
        server.expect(requestTo(ENDPOINT + "?page=0&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertBusinessError(() -> client.fetchMig8Orders(0, 10), ErrorCode.FORBIDDEN);
        server.verify();
    }

    @Test
    void fetchMig8Orders_500은_INTERNAL_ERROR() {
        server.expect(requestTo(ENDPOINT + "?page=0&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertBusinessError(() -> client.fetchMig8Orders(0, 10), ErrorCode.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void fetchMig8Orders_빈_body는_INTERNAL_ERROR() {
        server.expect(requestTo(ENDPOINT + "?page=0&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertBusinessError(() -> client.fetchMig8Orders(0, 10), ErrorCode.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void fetchMig8Orders_content가_배열이_아니면_INTERNAL_ERROR() {
        server.expect(requestTo(ENDPOINT + "?page=0&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"content":{},"last":false}}
                        """, MediaType.APPLICATION_JSON));

        assertBusinessError(() -> client.fetchMig8Orders(0, 10), ErrorCode.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void fetchMig8Orders_malformed_JSON은_INTERNAL_ERROR() {
        server.expect(requestTo(ENDPOINT + "?page=0&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        assertBusinessError(() -> client.fetchMig8Orders(0, 10), ErrorCode.INTERNAL_ERROR);
        server.verify();
    }

    @Test
    void fetchMig8Orders_page와_size를_하한값으로_정규화한다() {
        server.expect(requestTo(ENDPOINT + "?page=0&size=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"content":[],"last":true}}
                        """, MediaType.APPLICATION_JSON));

        Mig8OrderPage page = client.fetchMig8Orders(-1, 0);

        assertThat(page.content()).isEmpty();
        assertThat(page.last()).isTrue();
        server.verify();
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
