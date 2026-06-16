package com.samhanair.logis.product.web;

import com.samhanair.logis.product.domain.BranchPipeLookup;
import com.samhanair.logis.product.domain.OduRecommendationLookup;
import com.samhanair.logis.product.domain.OduRecommendationLookup.RecommendationType;
import com.samhanair.logis.product.domain.ProductCategory;
import com.samhanair.logis.product.repository.BranchPipeLookupRepository;
import com.samhanair.logis.product.repository.OduRecommendationLookupRepository;
import com.samhanair.logis.product.repository.ProductRepository;
import com.samhanair.logis.product.web.dto.BranchPipeResponse;
import com.samhanair.logis.product.web.dto.MaterialPriceResponse;
import com.samhanair.logis.product.web.dto.OduRecommendationResponse;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 견적/주문 라인 입력 lookup endpoint.
 *
 * <p>응답은 legacy shim 계약에 맞춰 {@code ApiResponse} envelope 없이 배열을 직접 반환한다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductLookupController {

    private static final Comparator<MaterialPriceResponse> MATERIAL_PRODUCT_CODE_ORDER =
            Comparator.comparing(MaterialPriceResponse::materialKey,
                            Comparator.nullsLast(String::compareTo))
                    .thenComparing(MaterialPriceResponse::name, Comparator.nullsLast(String::compareTo));

    private final ProductRepository productRepository;
    private final OduRecommendationLookupRepository oduRecommendationLookupRepository;
    private final BranchPipeLookupRepository branchPipeLookupRepository;

    /** GET /api/v1/material-prices — 전체 자재 단가 lookup. */
    @GetMapping("/material-prices")
    @RequirePermission(page = "products.list", action = PermissionAction.VIEW)
    public List<MaterialPriceResponse> listMaterialPrices() {
        // 싱글 자재는 Product(MATERIAL)가 원천이다. 기존 배열/필드명은 데스크톱 lookup 호환용으로 유지한다.
        return productRepository.findByProductCategoryAndIsDeletedFalse(ProductCategory.MATERIAL).stream()
                // Product(MATERIAL) 전환 후 materialKey 는 MAT-* 품목코드다. 코드/이름 기준으로 결정 정렬한다.
                .map(MaterialPriceResponse::from)
                .sorted(MATERIAL_PRODUCT_CODE_ORDER)
                .toList();
    }

    /** GET /api/v1/odu-recommendations?type=HOME_MULTI — 추천 실외기 lookup. */
    @GetMapping("/odu-recommendations")
    @RequirePermission(page = "products.list", action = PermissionAction.VIEW)
    public List<OduRecommendationResponse> listOduRecommendations(
            @RequestParam(required = false, name = "type") RecommendationType type) {
        List<OduRecommendationLookup> rows = type == null
                ? oduRecommendationLookupRepository.findAllByOrderByRecommendationTypeAscIndoorCapacityAsc()
                : oduRecommendationLookupRepository.findByRecommendationTypeOrderByIndoorCapacityAsc(type);
        return rows.stream()
                .map(OduRecommendationResponse::from)
                .toList();
    }

    /** GET /api/v1/branch-pipes?branchCode=1509 — 분기관 lookup. */
    @GetMapping("/branch-pipes")
    @RequirePermission(page = "products.list", action = PermissionAction.VIEW)
    public List<BranchPipeResponse> listBranchPipes(
            @RequestParam(required = false) String branchCode) {
        List<BranchPipeLookup> rows = branchCode == null
                ? branchPipeLookupRepository.findAllByOrderByBranchCodeAsc()
                : branchPipeLookupRepository.findAllByBranchCodeOrderByBranchCodeAsc(branchCode);
        return rows.stream()
                .map(BranchPipeResponse::from)
                .toList();
    }

}
