package com.samhanair.logis.product.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.product.domain.BundleComponent;
import com.samhanair.logis.product.domain.BundleMode;
import com.samhanair.logis.product.domain.Category;
import com.samhanair.logis.product.domain.EstimateCategory;
import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductCategory;
import com.samhanair.logis.product.domain.ProductType;
import com.samhanair.logis.product.domain.UsageScope;
import com.samhanair.logis.product.repository.BundleComponentRepository;
import com.samhanair.logis.product.repository.CategoryRepository;
import com.samhanair.logis.product.repository.ProductRepository;
import com.samhanair.logis.product.service.BundleExpander;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * BundleExpander EXPAND/KEEP 분기 IT (sample 5 BUNDLE).
 *
 * <p>출처: DOMAIN-EXTENSIONS §2 + partner-order Code.js SEND_AS_SET_IDS.
 */
@SpringBootTest
@DirtiesContext
@WithMockUser(username = "test-user")
@Transactional
class BundleExpanderIT extends AbstractPostgresIT {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BundleComponentRepository componentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BundleExpander expander;

    @Test
    void EXPAND_모드_component_펼침_FOLLOW_SET_qty_곱() {
        Category cat = categoryRepository.save(Category.create("BUNDLE-TEST-EXP", "test", null, 1));
        Product parent = Product.seedFromSheet("BUNDLE 부모", "BUND001", cat,
                BigDecimal.ZERO, BigDecimal.ZERO,
                ProductType.BUNDLE, ProductCategory.SINGLE_SET,
                UsageScope.BOTH, EstimateCategory.SINGLE_SET);
        parent.changeBundle(ProductType.BUNDLE, BundleMode.EXPAND);
        parent = productRepository.save(parent);

        componentRepository.save(BundleComponent.seed(parent.getId(), "C001",
                new BigDecimal("1"), BundleComponent.QtyMode.FOLLOW_SET,
                BundleComponent.ComponentKind.INDOOR, "기본", true, null));
        componentRepository.save(BundleComponent.seed(parent.getId(), "C002",
                new BigDecimal("2"), BundleComponent.QtyMode.FIXED,
                BundleComponent.ComponentKind.PANEL, null, false, null));
        productRepository.flush();
        componentRepository.flush();

        var lines = expander.expand("BUND001", new BigDecimal("3"));
        assertThat(lines).hasSize(2);
        // C001: FOLLOW_SET → setQty(3) * defaultQty(1) = 3
        assertThat(lines.get(0).modelCode()).isEqualTo("C001");
        assertThat(lines.get(0).quantity()).isEqualByComparingTo("3");
        // C002: FIXED → defaultQty(2) 그대로 (setQty 무관)
        assertThat(lines.get(1).modelCode()).isEqualTo("C002");
        assertThat(lines.get(1).quantity()).isEqualByComparingTo("2");
    }

    @Test
    void KEEP_모드_부모_단일_라인_유지() {
        Category cat = categoryRepository.save(Category.create("BUNDLE-TEST-KEEP", "test", null, 2));
        Product parent = Product.seedFromSheet("KEEP 부모", "BUND_KEEP", cat,
                BigDecimal.ZERO, BigDecimal.ZERO,
                ProductType.BUNDLE, ProductCategory.SINGLE_SET,
                UsageScope.BOTH, EstimateCategory.SINGLE_SET);
        parent.changeBundle(ProductType.BUNDLE, BundleMode.KEEP);
        parent = productRepository.save(parent);
        componentRepository.save(BundleComponent.seed(parent.getId(), "X001",
                new BigDecimal("1"), BundleComponent.QtyMode.FIXED,
                BundleComponent.ComponentKind.ACCESSORY, null, false, null));
        productRepository.flush();
        componentRepository.flush();

        var lines = expander.expand("BUND_KEEP", new BigDecimal("5"));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).modelCode()).isEqualTo("BUND_KEEP");
        assertThat(lines.get(0).quantity()).isEqualByComparingTo("5");
    }

    @Test
    void SINGLE_제품_그대로_단일_라인() {
        Category cat = categoryRepository.save(Category.create("SINGLE-TEST", "test", null, 3));
        Product single = Product.seedFromSheet("단일", "SNG001", cat,
                BigDecimal.ZERO, BigDecimal.ZERO,
                ProductType.SINGLE, ProductCategory.HOME_MULTI,
                UsageScope.BOTH, EstimateCategory.HOME_MULTI);
        productRepository.save(single);
        productRepository.flush();

        var lines = expander.expand("SNG001", new BigDecimal("4"));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).modelCode()).isEqualTo("SNG001");
        assertThat(lines.get(0).quantity()).isEqualByComparingTo("4");
    }
}
