package com.samhanair.logis.dashboard.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.dashboard.client.InventoryClient;
import com.samhanair.logis.dashboard.domain.RealTimeStock;
import com.samhanair.logis.dashboard.repository.RealTimeStockRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실시간 재고 service — Phase 9 W4.
 *
 * <p>inventory-service 연동 + 로컬 cache row upsert. 사용자 화면에는 productCode / warehouseCode 만
 * 노출 (UUID 비공개 가드).
 */
@Service
@RequiredArgsConstructor
public class RealTimeStockService {

    private final RealTimeStockRepository repository;
    private final InventoryClient inventoryClient;

    /**
     * 창고 코드 + (선택) productId 필터 조회. 둘 다 null/blank 시 전체 list (admin 한정 사용).
     *
     * @param warehouseCode 창고 코드 (nullable)
     * @param productId 제품 UUID (nullable)
     * @return 매칭 row (활성 행 한정)
     */
    @Transactional(readOnly = true)
    public List<RealTimeStock> findStocks(String warehouseCode, UUID productId) {
        if (warehouseCode == null || warehouseCode.isBlank()) {
            return repository.findAll();
        }
        if (productId == null) {
            return repository.findAllByWarehouseCode(warehouseCode);
        }
        return repository.findFirstByProductIdAndWarehouseCode(productId, warehouseCode)
                .map(List::of)
                .orElseGet(List::of);
    }

    /**
     * 단건 lookup. inventory-service 호출 후 fail-soft 인 경우 로컬 cache 반환.
     */
    @Transactional
    public RealTimeStock refreshOne(UUID productId, String warehouseCode) {
        if (productId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "productId 필수");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "warehouseCode 필수");
        }
        Optional<BigDecimal> latestQty = inventoryClient.findStock(productId, warehouseCode);
        return repository.findFirstByProductIdAndWarehouseCode(productId, warehouseCode)
                .map(existing -> {
                    latestQty.ifPresent(existing::refreshQuantity);
                    return existing;
                })
                .orElseGet(() -> {
                    BigDecimal qty = latestQty.orElse(BigDecimal.ZERO);
                    return repository.save(RealTimeStock.of(productId, warehouseCode, qty, null));
                });
    }
}
