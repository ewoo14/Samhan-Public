package com.samhanair.logis.slip.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryResponse;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryService;
import com.samhanair.logis.slip.service.NextDaySlipImageService;
import com.samhanair.logis.slip.service.SlipCleanupService;
import com.samhanair.logis.slip.service.SlipExcelExportService;
import com.samhanair.logis.slip.service.SlipService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/** SlipController — #809 가격기억 조회 endpoint 계약 테스트. */
@ExtendWith(MockitoExtension.class)
class SlipControllerPriceMemoryTest {

    @Mock private SlipService slipService;
    @Mock private NextDaySlipImageService nextDaySlipImageService;
    @Mock private SlipCleanupService slipCleanupService;
    @Mock private SlipExcelExportService slipExcelExportService;
    @Mock private PartnerProductPriceMemoryService priceMemoryService;
    @Mock private DynamicPermissionClient dynamicPermissionClient;

    @InjectMocks private SlipController controller;

    @Test
    void getPriceMemory_hit_returnsOkEnvelope() {
        UUID accountId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(true);
        when(priceMemoryService.find(partnerId, productId)).thenReturn(Optional.of(
                new PartnerProductPriceMemoryResponse(
                        new BigDecimal("123456.00"), "LINE_SAVE", LocalDateTime.of(2026, 7, 15, 10, 0))));

        ResponseEntity<ApiResponse<PartnerProductPriceMemoryResponse>> response =
                controller.getPriceMemory(partnerId, productId, accountId.toString());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().unitPrice()).isEqualByComparingTo("123456.00");
    }

    @Test
    void getPriceMemory_miss_returnsNoContent() {
        UUID accountId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(true);
        when(priceMemoryService.find(partnerId, productId)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<PartnerProductPriceMemoryResponse>> response =
                controller.getPriceMemory(partnerId, productId, accountId.toString());

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void getPriceMemory_withoutCreatePermission_returnsForbidden() {
        UUID accountId = UUID.randomUUID();
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("purchases.slip.edit"), eq(PermissionAction.UPDATE)))
                .thenReturn(false);

        assertThatThrownBy(() -> controller.getPriceMemory(UUID.randomUUID(), UUID.randomUUID(), accountId.toString()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
