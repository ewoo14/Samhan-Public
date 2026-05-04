package com.samhanair.logis.product.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.product.config.HeaderAuthenticationFilter;
import com.samhanair.logis.product.config.InternalAuthProperties;
import com.samhanair.logis.product.config.InternalTokenFilter;
import com.samhanair.logis.product.domain.ProductStatus;
import com.samhanair.logis.product.service.ProductService;
import com.samhanair.logis.product.web.dto.LookupRequest;
import com.samhanair.logis.product.web.dto.ProductSummaryResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Verifies internal-token enforcement on /products/internal/lookup. inventory-service 가
 * X-Internal-Token 으로만 호출 가능해야 함을 보장한다.
 */
class ProductInternalControllerTest {

    private static final String VALID_TOKEN = "test-internal-token";

    private ProductService productService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        productService = Mockito.mock(ProductService.class);
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken(VALID_TOKEN);

        mockMvc = MockMvcBuilders.standaloneSetup(new ProductInternalController(productService))
                .addFilters(new InternalTokenFilter(props), new HeaderAuthenticationFilter())
                .build();
    }

    @Test
    void lookup_withMissingToken_returns401AndDoesNotCallService() throws Exception {
        UUID id = UUID.randomUUID();
        var body = new LookupRequest(List.of(id));

        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders
                        .post("/products/internal/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        verify(productService, never()).lookup(any());
    }

    @Test
    void lookup_withWrongToken_returns401() throws Exception {
        UUID id = UUID.randomUUID();
        var body = new LookupRequest(List.of(id));

        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders
                        .post("/products/internal/lookup")
                        .header("X-Internal-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        verify(productService, never()).lookup(any());
    }

    @Test
    void lookup_withValidToken_returns200AndDelegatesToService() throws Exception {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        var body = new LookupRequest(List.of(id));

        when(productService.lookup(List.of(id))).thenReturn(List.of(
                new ProductSummaryResponse(id, "스마트 벽걸이", "SHA-W15K", categoryId,
                        new BigDecimal("1500000.00"), ProductStatus.ACTIVE)));

        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders
                        .post("/products/internal/lookup")
                        .header("X-Internal-Token", VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("SHA-W15K");
        verify(productService).lookup(List.of(id));
    }
}
