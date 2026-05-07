package com.samhanair.logis.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.dashboard.client.InventoryClient;
import com.samhanair.logis.dashboard.domain.RealTimeStock;
import com.samhanair.logis.dashboard.repository.RealTimeStockRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * RealTimeStockService 단위 테스트 — Phase 9 W4 (4 case).
 *
 * <ol>
 *   <li>warehouseCode null + productId null → 전체 list</li>
 *   <li>warehouseCode 만 있음 → warehouseCode 필터</li>
 *   <li>refreshOne — 신규 row insert (inventoryClient mock 빈 결과 → ZERO 적재)</li>
 *   <li>refreshOne — productId null → 400</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealTimeStockServiceTest {

    @Mock
    private RealTimeStockRepository repository;
    @Mock
    private InventoryClient inventoryClient;

    @InjectMocks
    private RealTimeStockService service;

    @BeforeEach
    void setup() {
        lenient().when(repository.save(any(RealTimeStock.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(inventoryClient.findStock(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void findStocks_with_no_filter_returns_all() {
        when(repository.findAll()).thenReturn(List.of());
        List<RealTimeStock> result = service.findStocks(null, null);
        assertThat(result).isEmpty();
        verify(repository).findAll();
    }

    @Test
    void findStocks_with_warehouse_only_filters_by_warehouse() {
        when(repository.findAllByWarehouseCode("W01")).thenReturn(List.of());
        List<RealTimeStock> result = service.findStocks("W01", null);
        assertThat(result).isEmpty();
        verify(repository).findAllByWarehouseCode("W01");
    }

    @Test
    void refreshOne_inserts_new_row_when_missing() {
        UUID productId = UUID.randomUUID();
        when(repository.findFirstByProductIdAndWarehouseCode(productId, "W01"))
                .thenReturn(Optional.empty());

        RealTimeStock result = service.refreshOne(productId, "W01");

        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(repository).save(any(RealTimeStock.class));
    }

    @Test
    void refreshOne_with_null_productId_throws_400() {
        assertThatThrownBy(() -> service.refreshOne(null, "W01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("productId 필수");
    }
}
