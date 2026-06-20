package com.samhanair.logis.partnerauth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.samhanair.logis.partnerauth.config.DcConfigClientProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** DcConfigClient — dc-config-service by-bizno internal RestClient 계약 테스트. */
class DcConfigClientTest {

    private static final String TOKEN = "test-internal-token";
    private static final String BASE_URL = "http://dc-config-service:8089";
    private static final String ENDPOINT = BASE_URL + "/internal/partners/by-bizno/1234567890";

    private MockRestServiceServer server;
    private DcConfigClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        DcConfigClientProperties properties = new DcConfigClientProperties();
        properties.setUrl(BASE_URL);
        properties.setInternalToken(TOKEN);
        client = new DcConfigClient(properties);
        ReflectionTestUtils.setField(client, "restClient", builder.baseUrl(BASE_URL).build());
    }

    @Test
    void by_bizno는_경로_토큰을_전달하고_nested_DC를_파싱한다() {
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"code":"OK","message":"성공","data":{
                          "partnerId":"00000000-0000-0000-0000-000000000301",
                          "partnerCode":"P-DC-001",
                          "bizNo":"1234567890",
                          "name":"삼한거래처",
                          "address":"서울",
                          "phone":"010-1111-2222",
                          "manager":"홍길동",
                          "partnerGroup":"A",
                          "creditLimit":1000000,
                          "remark":"extra fields ignored",
                          "dcConfig":{
                            "partnerCode":"P-DC-001",
                            "homeDiscountRate":0.1234,
                            "commercialDiscountRate":"0.2345",
                            "showIHose":true,
                            "discount360Amount":1000,
                            "discount4WayAmount":2000,
                            "discount1WayAmount":3000,
                            "discountStandAmount":4000,
                            "discountDeluxeAmount":5000,
                            "discountFirstGradeAmount":6000,
                            "unitRoundTo":100,
                            "unitRoundMode":"HALF_UP",
                            "source":"SEED",
                            "note":"ignored"
                          }
                        }}""", MediaType.APPLICATION_JSON));

        Optional<PartnerConfigDto> result = client.findByBizNo("1234567890");

        assertThat(result).isPresent();
        PartnerConfigDto config = result.orElseThrow();
        assertThat(config.partnerCode()).isEqualTo("P-DC-001");
        assertThat(config.partnerName()).isEqualTo("삼한거래처");
        assertThat(config.managerName()).isEqualTo("홍길동");
        assertThat(config.mobileNo()).isEqualTo("010-1111-2222");
        assertThat(config.dc()).isNotNull();
        assertThat(config.dc().homeDiscountRate()).isEqualByComparingTo("0.1234");
        assertThat(config.dc().commercialDiscountRate()).isEqualByComparingTo("0.2345");
        assertThat(config.dc().showIHose()).isTrue();
        assertThat(config.dc().discount360Amount()).isEqualByComparingTo("1000");
        assertThat(config.dc().discount4WayAmount()).isEqualByComparingTo("2000");
        assertThat(config.dc().discount1WayAmount()).isEqualByComparingTo("3000");
        assertThat(config.dc().discountStandAmount()).isEqualByComparingTo("4000");
        assertThat(config.dc().discountDeluxeAmount()).isEqualByComparingTo("5000");
        assertThat(config.dc().discountFirstGradeAmount()).isEqualByComparingTo("6000");
        assertThat(config.dc().unitRoundTo()).isEqualTo(100);
        assertThat(config.dc().unitRoundMode()).isEqualTo("HALF_UP");
        server.verify();
    }

    @Test
    void by_bizno_404는_Optional_empty로_dampen한다() {
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findByBizNo("1234567890")).isEmpty();
        server.verify();
    }

    @Test
    void by_bizno_401은_Optional_empty로_dampen한다() {
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(client.findByBizNo("1234567890")).isEmpty();
        server.verify();
    }

    @Test
    void by_bizno_5xx도_소스대로_Optional_empty로_dampen한다() {
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withServerError());

        assertThat(client.findByBizNo("1234567890")).isEmpty();
        server.verify();
    }
}
