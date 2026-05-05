package com.samhanair.logis.product.service;

import com.samhanair.logis.product.domain.BundleComponent;
import com.samhanair.logis.product.domain.BundleMode;
import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductType;
import com.samhanair.logis.product.repository.BundleComponentRepository;
import com.samhanair.logis.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BUNDLE EXPAND/KEEP 분기 처리기.
 *
 * <p><b>출처</b>:
 * <ul>
 *     <li>DOMAIN-EXTENSIONS §2 — BUNDLE 옵션 A + bundleMode</li>
 *     <li>partner-order Code.js SEND_AS_SET_IDS — 4 SKU (발통원형/발통평형/유선보드/천장펌프) = KEEP</li>
 *     <li>Migration Plan §2.1 — 견적/주문 라인 처리 분기</li>
 * </ul>
 *
 * <p>Layer 4 의미 정렬:
 * <ul>
 *     <li>{@link #expand(String, BigDecimal)} = "BUNDLE 부모 modelCode + 수량 → 견적/주문 라인 list 산출
 *         (EXPAND = component 펼침, KEEP = 부모 1 라인 유지)"</li>
 * </ul>
 */
@Service
public class BundleExpander {

    /**
     * SEND_AS_SET_IDS — partner-order Code.js 의 4 SKU 화이트리스트.
     * 본 SKU 들은 시드 시 bundleMode=KEEP 으로 자동 설정.
     * 시드 시점에 정확한 modelCode 매핑 (마이그 실행 시점에 추가).
     */
    public static final Set<String> SEND_AS_SET_IDS = Set.of(
            "FOOT_ROUND",     // 발통원형
            "FOOT_FLAT",      // 발통평형
            "WIRED_BOARD",    // 유선보드
            "CEILING_PUMP"    // 천장펌프
    );

    private final ProductRepository productRepository;
    private final BundleComponentRepository componentRepository;

    public BundleExpander(ProductRepository productRepository,
                          BundleComponentRepository componentRepository) {
        this.productRepository = productRepository;
        this.componentRepository = componentRepository;
    }

    /**
     * BUNDLE 부모 modelCode + setQty → 라인 list.
     * EXPAND: component qty * setQty (FOLLOW_SET 또는 FIXED).
     * KEEP: 부모 1 라인.
     *
     * @param parentModelCode 부모 BUNDLE modelCode
     * @param setQty 세트 수량
     * @return 펼친 라인 (modelCode + qty 페어). KEEP 인 경우 단일 element.
     */
    @Transactional(readOnly = true)
    public List<ExpandedLine> expand(String parentModelCode, BigDecimal setQty) {
        Product parent = productRepository.findByModelCodeAndIsDeletedFalse(parentModelCode)
                .orElseThrow(() -> new EntityNotFoundException("Product 없음: " + parentModelCode));
        if (parent.getProductType() != ProductType.BUNDLE) {
            return Collections.singletonList(new ExpandedLine(parent.getModelCode(), setQty));
        }
        BundleMode mode = parent.getBundleMode() == null ? BundleMode.EXPAND : parent.getBundleMode();
        if (mode == BundleMode.KEEP) {
            return Collections.singletonList(new ExpandedLine(parent.getModelCode(), setQty));
        }
        // EXPAND
        List<BundleComponent> components = componentRepository.findByBundleProductId(parent.getId());
        List<ExpandedLine> result = new ArrayList<>(components.size());
        for (BundleComponent c : components) {
            BigDecimal qty = (c.getQtyMode() == BundleComponent.QtyMode.FOLLOW_SET)
                    ? setQty.multiply(c.getDefaultQty())
                    : c.getDefaultQty();
            result.add(new ExpandedLine(c.getComponentProductCode(), qty));
        }
        return result;
    }

    /** 펼친 라인. */
    public record ExpandedLine(String modelCode, BigDecimal quantity) {}
}
