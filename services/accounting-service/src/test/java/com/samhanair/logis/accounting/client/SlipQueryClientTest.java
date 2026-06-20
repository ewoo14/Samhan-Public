package com.samhanair.logis.accounting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.InternalAuthProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** SlipQueryClient — slip-service 판매조회 internal RestClient 계약 테스트. */
class SlipQueryClientTest {

    private static final String TOKEN = "test-internal-token";
    private static final String ENDPOINT = "http://slip-service/internal/slips/sales-query";

    private MockRestServiceServer server;
    private SlipQueryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(TOKEN);
        client = new SlipQueryClient(builder, props);
    }

    @Test
    void sales_query는_경로_쿼리_토큰을_전달하고_content를_파싱한다() {
        server.expect(once(), requestTo(ENDPOINT
                        + "?from=2026-06-01&to=2026-06-30&page=0&size=200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"data":{
                          "content":[
                            {"slipNo":"2026/06/01-1","partnerCode":"P-001","partnerName":"삼한테스트","supplyAmount":100000}
                          ],
                          "last":true,"totalElements":1,"totalPages":1,"number":0,"size":200
                        }}""", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> rows = client.fetchAllSalesRows(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("slipNo", "2026/06/01-1")
                .containsEntry("partnerCode", "P-001")
                .containsEntry("partnerName", "삼한테스트")
                .containsEntry("supplyAmount", 100000);
        server.verify();
    }

    @Test
    void sales_query는_last_false면_다음_page를_조회하고_last_true에서_종료한다() {
        server.expect(once(), requestTo(ENDPOINT
                        + "?from=2026-06-01&to=2026-06-30&page=0&size=200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"data":{
                          "content":[{"slipNo":"2026/06/01-1","partnerCode":"P-001"}],
                          "last":false,"totalElements":2,"totalPages":2,"number":0,"size":200
                        }}""", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(ENDPOINT
                        + "?from=2026-06-01&to=2026-06-30&page=1&size=200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess("""
                        {"success":true,"data":{
                          "content":[{"slipNo":"2026/06/02-1","partnerCode":"P-002"}],
                          "last":true,"totalElements":2,"totalPages":2,"number":1,"size":200
                        }}""", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> rows = client.fetchAllSalesRows(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(rows).extracting(row -> row.get("slipNo"))
                .containsExactly("2026/06/01-1", "2026/06/02-1");
        server.verify();
    }

    @Test
    void sales_query_4xx는_소스대로_빈_결과로_dampen한다() {
        server.expect(once(), requestTo(ENDPOINT
                        + "?from=2026-06-01&to=2026-06-30&page=0&size=200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetchAllSalesRows(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))).isEmpty();
        server.verify();
    }

    @Test
    void sales_query_5xx는_INTERNAL_ERROR로_매핑한다() {
        server.expect(once(), requestTo(ENDPOINT
                        + "?from=2026-06-01&to=2026-06-30&page=0&size=200"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchAllSalesRows(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR));
        server.verify();
    }
}
