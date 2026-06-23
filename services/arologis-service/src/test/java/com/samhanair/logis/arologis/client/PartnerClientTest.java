package com.samhanair.logis.arologis.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** PartnerClient — partner-service bulk partner summary lookup fail-soft 계약 회귀 가드. */
class PartnerClientTest {

    private static final String TOKEN = "test-token";
    private static final String ENDPOINT = "http://partner-service/internal/partners/find-by-codes";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private PartnerClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PartnerClient(builder, new ObjectMapper(),
                "http://partner-service", TOKEN, false);
    }

    @Test
    void findByCodes_200은_헤더_바디를_검증하고_partnerCode_name만_파싱한다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().json("[\"P1\",\"P2\"]"))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": [
                            {
                              "partnerCode": "P1",
                              "name": "가나다상사",
                              "partnerId": "00000000-0000-0000-0000-000000000701"
                            },
                            {
                              "partnerCode": "P2",
                              "name": "라마물류",
                              "partnerId": "00000000-0000-0000-0000-000000000702"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PartnerClient.PartnerSummary> result = client.findByCodes(List.of("P1", "P2"));

        assertThat(result)
                .extracting(PartnerClient.PartnerSummary::partnerCode)
                .containsExactly("P1", "P2");
        assertThat(result)
                .extracting(PartnerClient.PartnerSummary::name)
                .containsExactly("가나다상사", "라마물류");
        assertThat(PartnerClient.PartnerSummary.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("partnerCode", "name");
        server.verify();
    }

    @Test
    void findByCodes_4xx는_fail_soft로_빈리스트를_반환한다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().json("[\"P1\"]"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.findByCodes(List.of("P1"))).isEmpty();
        server.verify();
    }

    @Test
    void findByCodes_5xx는_fail_soft로_빈리스트를_반환한다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().json("[\"P1\"]"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.findByCodes(List.of("P1"))).isEmpty();
        server.verify();
    }

    @Test
    void findByCodes_null과_빈입력은_빈리스트이고_HTTP를_호출하지_않는다() {
        assertThat(client.findByCodes(null)).isEmpty();
        assertThat(client.findByCodes(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void findByCodes_skeleton_mode_true는_빈리스트이고_HTTP를_호출하지_않는다() {
        PartnerClient skeletonClient = new PartnerClient(builder, new ObjectMapper(),
                "http://partner-service", TOKEN, true);

        assertThat(skeletonClient.findByCodes(List.of("P1"))).isEmpty();
        server.verify();
    }

    @Test
    void findByCode_200_1건은_Optional_present를_반환한다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().json("[\"P1\"]"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[{"partnerCode":"P1","name":"가나다상사"}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<PartnerClient.PartnerSummary> result = client.findByCode("P1");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().partnerCode()).isEqualTo("P1");
        assertThat(result.orElseThrow().name()).isEqualTo("가나다상사");
        server.verify();
    }

    @Test
    void findByCode_빈응답은_Optional_empty를_반환한다() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().json("[\"UNKNOWN\"]"))
                .andRespond(withSuccess("""
                        {"success":true,"data":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findByCode("UNKNOWN")).isEmpty();
        server.verify();
    }
}
