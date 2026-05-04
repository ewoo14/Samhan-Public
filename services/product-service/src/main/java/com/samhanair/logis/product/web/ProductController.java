package com.samhanair.logis.product.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.product.domain.ProductStatus;
import com.samhanair.logis.product.service.ProductService;
import com.samhanair.logis.product.web.dto.CreateProductRequest;
import com.samhanair.logis.product.web.dto.LookupRequest;
import com.samhanair.logis.product.web.dto.ProductResponse;
import com.samhanair.logis.product.web.dto.ProductSummaryResponse;
import com.samhanair.logis.product.web.dto.UpdatePriceRequest;
import com.samhanair.logis.product.web.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제품 master CRUD + 가격/태그/단종 부분 수정. 권한 매트릭스:
 * <ul>
 *   <li>MASTER / MANAGER / DEVELOPER — 전체 mutation</li>
 *   <li>ACCOUNTANT — 가격 patch 한정 추가 권한</li>
 *   <li>그 외 (SALES / WAREHOUSE / INVENTORY) — 읽기 전용</li>
 * </ul>
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String CALLER_HEADER = "X-User-Id";

    private final ProductService productService;

    @GetMapping
    public ApiResponse<Page<ProductSummaryResponse>> search(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String tagKey,
            @RequestParam(required = false) String tagValue,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(productService.search(categoryId, status, tagKey, tagValue, q, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getOne(@PathVariable UUID id) {
        return ApiResponse.ok(productService.getOne(id));
    }

    @PostMapping("/lookup")
    public ApiResponse<List<ProductSummaryResponse>> lookup(@Valid @RequestBody LookupRequest request) {
        return ApiResponse.ok(productService.lookup(request.ids()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER')")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER')")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @PatchMapping("/{id}/price")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','ACCOUNTANT')")
    public ApiResponse<ProductResponse> updatePrice(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdatePriceRequest request) {
        return ApiResponse.ok(productService.updatePrice(id, request));
    }

    @PutMapping("/{id}/tags")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER')")
    public ApiResponse<ProductResponse> replaceTags(@PathVariable UUID id,
                                                    @RequestBody Map<String, String> tags) {
        return ApiResponse.ok(productService.replaceTags(id, tags));
    }

    @PostMapping("/{id}/discontinue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER')")
    public void discontinue(@PathVariable UUID id) {
        productService.discontinue(id);
    }

    @PostMapping("/{id}/reactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER')")
    public void reactivate(@PathVariable UUID id) {
        productService.reactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER')")
    public void delete(@PathVariable UUID id,
                       @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        productService.delete(id, callerHeader);
    }
}
