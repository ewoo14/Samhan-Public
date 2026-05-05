package com.samhanair.logis.product.service;

import com.samhanair.logis.product.domain.MaterialKey;
import com.samhanair.logis.product.domain.MaterialPrice;
import com.samhanair.logis.product.repository.MaterialPriceRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 자재 단가 계산기 — D4 default + D7/D8 분기.
 *
 * <p><b>출처</b>:
 * <ul>
 *     <li>Discussion §11 D7 — 자재 단가 합산 룰</li>
 *     <li>DOMAIN-EXTENSIONS §1 G8 — D4 가 default master (245 hits)</li>
 *     <li>싱글 자재가격 시트 row 2~29 = MaterialPrice 28 row 시드</li>
 * </ul>
 *
 * <p>Layer 4 의미 정렬:
 * <ul>
 *     <li>{@link #resolveMaterialPrice(MaterialKey)} = "MaterialKey enum → 시트 D 열 master cell 가격 lookup"</li>
 *     <li>{@link #defaultMaterialKey()} = "자재 합계 default master = D4 반환"</li>
 * </ul>
 */
@Service
public class MaterialPriceCalculator {

    private final MaterialPriceRepository repository;

    public MaterialPriceCalculator(MaterialPriceRepository repository) {
        this.repository = repository;
    }

    /**
     * MaterialKey enum → 시트 D 열 master cell 가격 lookup.
     * D4 (자재 합계 master) / D7 (미포함) / D8 (포함).
     *
     * @param key D4/D7/D8
     * @return 자재 가격, 미시드 시 0
     */
    public BigDecimal resolveMaterialPrice(MaterialKey key) {
        if (key == null) {
            return BigDecimal.ZERO;
        }
        Optional<MaterialPrice> mp = repository.findByMaterialKey(key.name());
        return mp.map(MaterialPrice::getPrice).orElse(BigDecimal.ZERO);
    }

    /** 자재 합계 default master = D4 (DOMAIN-EXTENSIONS §1 G8). */
    public MaterialKey defaultMaterialKey() {
        return MaterialKey.D4;
    }

    /**
     * Detector 가 산출한 setMaterialKey 가 NULL 일 경우 default 채택.
     */
    public MaterialKey resolveOrDefault(MaterialKey detected) {
        return detected == null ? defaultMaterialKey() : detected;
    }
}
