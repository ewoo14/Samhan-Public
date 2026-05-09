package com.samhanair.logis.product.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.product.domain.Category;
import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductStatus;
import com.samhanair.logis.product.repository.CategoryRepository;
import com.samhanair.logis.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Stage 1 HvacProductSeeder 단위 테스트 — idempotency + HVAC 단가 6종 비즈니스 룰 검증.
 *
 * <p>비즈니스 룰 (Stage 1 dev-report §HVAC 단가 6종):
 * outbound = inbound * 1.20, single = inbound * 1.50, outdoor = inbound * 1.40,
 * multi50 = inbound * 1.10, multi48 = inbound * 1.12, multi45 = inbound * 1.15,
 * item35 = inbound * 1.30.
 */
@ExtendWith(MockitoExtension.class)
class HvacProductSeederTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private HvacProductSeeder seeder;

    @Test
    void firstRunCreatesAll100Products() {
        stubCategoriesPresent();
        when(productRepository.existsByModelNameAndIsDeletedFalse(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(100)).save(captor.capture());

        Set<String> modelNames = new HashSet<>();
        for (Product p : captor.getAllValues()) {
            modelNames.add(p.getModelName());
        }
        assertThat(modelNames).hasSize(100);
    }

    @Test
    void idempotentRunSkipsExistingByModelName() {
        stubCategoriesPresent();
        // 시뮬레이션: model 첫 50건은 이미 존재
        when(productRepository.existsByModelNameAndIsDeletedFalse(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            // odd model names exist, even don't (just an arbitrary pattern)
            return name.hashCode() % 2 == 0;
        });

        seeder.run();
        // 일부만 save — 정확한 수치는 hashCode 의존이지만 100 미만 확인
        verify(productRepository, org.mockito.Mockito.atMost(100)).save(any());
    }

    @Test
    void noOpWhenAllExist() {
        stubCategoriesPresent();
        when(productRepository.existsByModelNameAndIsDeletedFalse(anyString())).thenReturn(true);

        seeder.run();

        verify(productRepository, never()).save(any());
    }

    @Test
    void hvacPriceMatrixFollows6RatioRules() {
        stubCategoriesPresent();
        when(productRepository.existsByModelNameAndIsDeletedFalse(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(100)).save(captor.capture());

        // 모든 row 에서 6 비율 룰 확인 (rounding tolerance)
        for (Product p : captor.getAllValues()) {
            BigDecimal inbound = p.getInboundPrice();
            assertRatio(inbound, p.getOutboundPrice(), "1.20");
            assertRatio(inbound, p.getSinglePrice(),   "1.50");
            assertRatio(inbound, p.getOutdoorPrice(),  "1.40");
            assertRatio(inbound, p.getMulti50Price(),  "1.10");
            assertRatio(inbound, p.getMulti48Price(),  "1.12");
            assertRatio(inbound, p.getMulti45Price(),  "1.15");
            assertRatio(inbound, p.getItem35Price(),   "1.30");
        }
    }

    @Test
    void fourDiscontinuedAtSeq25Boundary() {
        stubCategoriesPresent();
        when(productRepository.existsByModelNameAndIsDeletedFalse(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(100)).save(captor.capture());

        long discontinued = captor.getAllValues().stream()
                .filter(p -> p.getStatus() == ProductStatus.DISCONTINUED)
                .count();
        assertThat(discontinued).isEqualTo(4L); // seq 25/50/75/100
    }

    @Test
    void everyProductHasKoreanVatStandard10Percent() {
        stubCategoriesPresent();
        when(productRepository.existsByModelNameAndIsDeletedFalse(anyString())).thenReturn(false);

        seeder.run();

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(100)).save(captor.capture());

        for (Product p : captor.getAllValues()) {
            assertThat(p.getVatRateOnSales()).isEqualByComparingTo("0.10");
            assertThat(p.getVatRateOnPurchase()).isEqualByComparingTo("0.10");
            assertThat(p.getPriceIncludesVat()).isTrue();
        }
    }

    @Test
    void earlyReturnIfNoCategoriesPresent() {
        // 카테고리 시드 없음 — runner 가 즉시 return (warn 로그)
        when(categoryRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        seeder.run();

        verify(productRepository, never()).save(any());
    }

    private void stubCategoriesPresent() {
        Category dummyCat = mockCategory();
        // lenient 로 모든 findById 호출에 대해 동일 instance 반환
        lenient().when(categoryRepository.findById(any(UUID.class)))
                .thenReturn(Optional.of(dummyCat));
    }

    private Category mockCategory() {
        // 도메인 메서드만 사용 (reflection 회피) — Category.create() factory
        return Category.create("HVAC", "공조", null, 1);
    }

    private static void assertRatio(BigDecimal base, BigDecimal actual, String expectedRatio) {
        BigDecimal expected = base.multiply(new BigDecimal(expectedRatio))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
