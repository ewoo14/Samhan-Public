package com.samhanair.logis.product.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.product.service.ProductService;
import com.samhanair.logis.product.web.dto.LookupRequest;
import com.samhanair.logis.product.web.dto.ProductSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 internal endpoint. {@link com.samhanair.logis.product.config.InternalTokenFilter}
 * 가 X-Internal-Token 으로 인증하므로 별도 @PreAuthorize 불필요.
 */
@RestController
@RequestMapping("/products/internal")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductService productService;

    /**
     * 제품 ID 일괄 조회 — inventory-service 등 internal 호출자가 productId 존재 여부 검증에 사용.
     * X-Internal-Token 헤더 인증 통과 후 진입.
     *
     * @param request LookupRequest (ids: 제품 UUID 리스트)
     * @return 응답 envelope 안 List&lt;ProductSummaryResponse&gt; (200) — 입력 순서와 무관
     */
    @Operation(summary = "제품 ID 일괄 조회 (internal)",
            description = "X-Internal-Token 인증 후 호출. inventory-service 등 service-to-service 용")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "X-Internal-Token 누락 또는 불일치")
    })
    @PostMapping("/lookup")
    public ApiResponse<List<ProductSummaryResponse>> lookup(@Valid @RequestBody LookupRequest request) {
        return ApiResponse.ok(productService.lookup(request.ids()));
    }
}
