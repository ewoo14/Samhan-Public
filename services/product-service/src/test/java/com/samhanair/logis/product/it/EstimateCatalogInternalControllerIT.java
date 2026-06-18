package com.samhanair.logis.product.it;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.product.ProductServiceApplication;
import com.samhanair.logis.product.domain.BundleComponent;
import com.samhanair.logis.product.domain.BundleMode;
import com.samhanair.logis.product.domain.Category;
import com.samhanair.logis.product.domain.EstimateCategory;
import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductCategory;
import com.samhanair.logis.product.domain.ProductEstimateExposure;
import com.samhanair.logis.product.domain.ProductSpec;
import com.samhanair.logis.product.domain.ProductType;
import com.samhanair.logis.product.domain.UsageScope;
import com.samhanair.logis.product.repository.BundleComponentRepository;
import com.samhanair.logis.product.repository.CategoryRepository;
import com.samhanair.logis.product.repository.ProductEstimateExposureRepository;
import com.samhanair.logis.product.repository.ProductRepository;
import com.samhanair.logis.product.repository.ProductSpecRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** estimate-app 내부 카탈로그 endpoint 통합 테스트. */
@SpringBootTest(classes = ProductServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class EstimateCatalogInternalControllerIT extends AbstractPostgresIT {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpecRepository productSpecRepository;

    @Autowired
    private ProductEstimateExposureRepository exposureRepository;

    @Autowired
    private BundleComponentRepository bundleComponentRepository;

    /** products endpoint 는 default ESTIMATE, 주문서 호출은 PARTNER_ORDER + BOTH scope 로 노출 필터를 바꾼다. */
    @Test
    void products_scopeParam_filtersEstimateVsPartnerOrderCatalog() throws Exception {
        Product estimateOnly = seedCatalogProduct("IT_SCOPE_EST", UsageScope.ESTIMATE);
        Product partnerOrderOnly = seedCatalogProduct("IT_SCOPE_PO", UsageScope.PARTNER_ORDER);
        Product both = seedCatalogProduct("IT_SCOPE_BOTH", UsageScope.BOTH);
        exposureRepository.save(ProductEstimateExposure.create(
                estimateOnly.getId(), EstimateCategory.HOME_MULTI, 1));
        exposureRepository.save(ProductEstimateExposure.create(
                partnerOrderOnly.getId(), EstimateCategory.HOME_MULTI, 2));
        exposureRepository.save(ProductEstimateExposure.create(
                both.getId(), EstimateCategory.HOME_MULTI, 3));
        productRepository.flush();
        exposureRepository.flush();

        mockMvc.perform(get("/products/internal/estimate-catalog/products?category=HOME_MULTI")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].modelCode", hasItem("IT_SCOPE_EST")))
                .andExpect(jsonPath("$.data[*].modelCode", hasItem("IT_SCOPE_BOTH")))
                .andExpect(jsonPath("$.data[?(@.modelCode == 'IT_SCOPE_PO')]").doesNotExist());

        mockMvc.perform(get("/products/internal/estimate-catalog/products"
                        + "?category=HOME_MULTI&scope=PARTNER_ORDER")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].modelCode", hasItem("IT_SCOPE_PO")))
                .andExpect(jsonPath("$.data[*].modelCode", hasItem("IT_SCOPE_BOTH")))
                .andExpect(jsonPath("$.data[?(@.modelCode == 'IT_SCOPE_EST')]").doesNotExist());
    }

    /** 상업멀티 구성품 조회 시 구성품 ProductSpec 목록을 additive specs 필드로 반환한다. */
    @Test
    void components_commercialMulti_returns_componentSpecs() throws Exception {
        Product parent = seedBundleParent("IT_COMM_SET_01",
                ProductCategory.COMMERCIAL_MULTI, EstimateCategory.COMMERCIAL_MULTI);
        Product component = seedComponentProduct("IT_COMM_IDU_01", "상업 실내기 IT",
                ProductCategory.COMMERCIAL_MULTI, EstimateCategory.COMMERCIAL_MULTI);
        productSpecRepository.save(ProductSpec.create(component.getId(), "냉방능력", "5.6", "kW", 1));
        bundleComponentRepository.save(BundleComponent.seed(parent.getId(), "IT_COMM_IDU_01",
                BigDecimal.ONE, BundleComponent.QtyMode.FOLLOW_SET,
                BundleComponent.ComponentKind.INDOOR, null, true, null));
        productRepository.flush();
        productSpecRepository.flush();
        bundleComponentRepository.flush();

        mockMvc.perform(get("/products/internal/estimate-catalog/components?category=COMMERCIAL_MULTI")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.componentModelCode == 'IT_COMM_IDU_01')]"
                        + ".specs[*].specKey", hasItem("냉방능력")));
    }

    /** 싱글 실내기 구성품에 ProductSpec 이 없으면 specs 필드는 존재하되 빈 배열로 반환한다. */
    @Test
    void components_singleSet_withoutComponentSpec_returns_emptySpecs() throws Exception {
        Product parent = seedBundleParent("IT_SINGLE_SET_01",
                ProductCategory.SINGLE_SET, EstimateCategory.SINGLE_SET);
        seedComponentProduct("IT_SINGLE_IDU_01", "싱글 실내기 IT",
                ProductCategory.SINGLE_SET, EstimateCategory.SINGLE_SET);
        bundleComponentRepository.save(BundleComponent.seed(parent.getId(), "IT_SINGLE_IDU_01",
                BigDecimal.ONE, BundleComponent.QtyMode.FOLLOW_SET,
                BundleComponent.ComponentKind.INDOOR, null, true, null));
        productRepository.flush();
        bundleComponentRepository.flush();

        mockMvc.perform(get("/products/internal/estimate-catalog/components?category=SINGLE_SET")
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.componentModelCode == 'IT_SINGLE_IDU_01')].specs",
                        hasItem(hasSize(0))));
    }

    /** 부모 BUNDLE(EXPAND) 품목 1건 저장. */
    private Product seedBundleParent(String modelCode, ProductCategory productCategory,
                                     EstimateCategory estimateCategory) {
        Category cat = categoryRepository.save(Category.create("CAT-" + modelCode, "estimate bundle", null, 40));
        Product parent = Product.seedFromSheet("세트 " + modelCode, modelCode, cat,
                BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(800_000), ProductType.BUNDLE,
                productCategory, UsageScope.BOTH, estimateCategory);
        parent.changeBundle(ProductType.BUNDLE, BundleMode.EXPAND);
        return productRepository.save(parent);
    }

    /** products endpoint scope 필터 검증용 카탈로그 품목 1건 저장. */
    private Product seedCatalogProduct(String modelCode, UsageScope usageScope) {
        Category cat = categoryRepository.save(Category.create("CAT-" + modelCode, "estimate catalog", null, 39));
        return productRepository.save(Product.seedFromSheet("품목 " + modelCode, modelCode, cat,
                BigDecimal.valueOf(500_000), BigDecimal.valueOf(400_000), ProductType.SINGLE,
                ProductCategory.HOME_MULTI, usageScope, EstimateCategory.HOME_MULTI));
    }

    /** 구성 후보 품목(SINGLE) 1건 저장 — modelCode 로 BundleComponent 와 join 된다. */
    private Product seedComponentProduct(String modelCode, String name, ProductCategory productCategory,
                                         EstimateCategory estimateCategory) {
        Category cat = categoryRepository.save(Category.create("CAT-" + modelCode, "estimate component", null, 41));
        Product component = Product.seedFromSheet(name, modelCode, cat,
                BigDecimal.valueOf(300_000), BigDecimal.valueOf(250_000), ProductType.SINGLE,
                productCategory, UsageScope.BOTH, estimateCategory);
        return productRepository.save(component);
    }
}
