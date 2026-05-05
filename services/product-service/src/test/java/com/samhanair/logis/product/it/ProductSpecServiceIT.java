package com.samhanair.logis.product.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.product.domain.Category;
import com.samhanair.logis.product.domain.EstimateCategory;
import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductCategory;
import com.samhanair.logis.product.domain.ProductSpec;
import com.samhanair.logis.product.domain.ProductType;
import com.samhanair.logis.product.domain.SpecKeyTemplate;
import com.samhanair.logis.product.domain.UsageScope;
import com.samhanair.logis.product.repository.CategoryRepository;
import com.samhanair.logis.product.repository.ProductRepository;
import com.samhanair.logis.product.repository.ProductSpecRepository;
import com.samhanair.logis.product.repository.SpecKeyTemplateRepository;
import com.samhanair.logis.product.service.ProductSpecService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * ProductSpec CRUD + reorder + apply-to-existing dry-run IT.
 *
 * <p>출처: G18 (409 strict on dup) / G19 (admin trigger only + dry-run).
 */
@SpringBootTest
@DirtiesContext
@WithMockUser(username = "test-user")
@Transactional
class ProductSpecServiceIT extends AbstractPostgresIT {

    @Autowired
    private ProductSpecService specService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSpecRepository specRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SpecKeyTemplateRepository templateRepository;

    private Product fixture;

    @BeforeEach
    void setupFixture() {
        Category cat = categoryRepository.save(Category.create("SPEC-TEST", "spec test", null, 1));
        fixture = productRepository.save(Product.seedFromSheet("Spec Test", "SPEC001", cat,
                BigDecimal.ZERO, BigDecimal.ZERO, ProductType.SINGLE,
                ProductCategory.HOME_MULTI, UsageScope.BOTH, EstimateCategory.HOME_MULTI));
        productRepository.flush();
    }

    @Test
    void CRUD_정상() {
        ProductSpec added = specService.addSpec("SPEC001", "냉방성능(kW)", "5.6", "kW", null);
        assertThat(added.getDisplayOrder()).isEqualTo(1);

        ProductSpec edited = specService.editSpec("SPEC001", added.getId(), "6.0", "kW");
        assertThat(edited.getSpecValue()).isEqualTo("6.0");

        var listAfterAdd = specService.listByModelCode("SPEC001");
        assertThat(listAfterAdd).hasSize(1).extracting(ProductSpec::getSpecKey).containsExactly("냉방성능(kW)");

        specService.deleteSpec("SPEC001", added.getId(), "test-user");
        var listAfterDel = specService.listByModelCode("SPEC001");
        assertThat(listAfterDel).isEmpty();
    }

    @Test
    void specKey_중복_409_throws() {
        specService.addSpec("SPEC001", "전원선", "220V", null, 1);
        assertThatThrownBy(() -> specService.addSpec("SPEC001", "전원선", "다시", null, 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reorder_drag_drop_정상() {
        ProductSpec a = specService.addSpec("SPEC001", "스펙A", "1", null, 1);
        ProductSpec b = specService.addSpec("SPEC001", "스펙B", "2", null, 2);
        ProductSpec c = specService.addSpec("SPEC001", "스펙C", "3", null, 3);

        Map<UUID, Integer> orderMap = new HashMap<>();
        orderMap.put(a.getId(), 30);
        orderMap.put(b.getId(), 10);
        orderMap.put(c.getId(), 20);
        specService.reorder("SPEC001", orderMap);

        var list = specService.listByModelCode("SPEC001");
        assertThat(list).extracting(ProductSpec::getSpecKey).containsExactly("스펙B", "스펙C", "스펙A");
    }

    @Test
    void applyToExisting_dryRun_INSERT_안함() {
        SpecKeyTemplate tmpl = templateRepository.save(SpecKeyTemplate.create(
                EstimateCategory.HOME_MULTI, "냉매가스", null, 99, true));
        templateRepository.flush();

        var result = specService.applyTemplateToExisting(tmpl.getId(), true);
        assertThat(result.dryRun()).isTrue();
        assertThat(result.actuallyAdded()).isZero();
        // SPEC001 (HOME_MULTI 카테고리) 가 후보로 잡혔어야 함
        assertThat(result.previewModelCodes()).contains("SPEC001");
        // 실제 INSERT 안 됨 검증
        assertThat(specRepository.existsByProductIdAndSpecKey(fixture.getId(), "냉매가스")).isFalse();
    }

    @Test
    void applyToExisting_실행_시_INSERT됨() {
        SpecKeyTemplate tmpl = templateRepository.save(SpecKeyTemplate.create(
                EstimateCategory.HOME_MULTI, "차단기", "A", 99, true));
        templateRepository.flush();

        var result = specService.applyTemplateToExisting(tmpl.getId(), false);
        assertThat(result.dryRun()).isFalse();
        assertThat(result.actuallyAdded()).isGreaterThanOrEqualTo(1);
        assertThat(specRepository.existsByProductIdAndSpecKey(fixture.getId(), "차단기")).isTrue();
    }
}
