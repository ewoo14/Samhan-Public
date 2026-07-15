package com.samhanair.logis.slip.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.permission.DynamicPermissionClient;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.PermissionGuardMetrics;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryBulkItemResponse;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryResponse;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryService;
import com.samhanair.logis.slip.service.NextDaySlipImageService;
import com.samhanair.logis.slip.service.SlipCleanupService;
import com.samhanair.logis.slip.service.SlipExcelExportService;
import com.samhanair.logis.slip.service.SlipService;
import com.samhanair.logis.slip.web.dto.PartnerProductPriceMemoryBulkRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    @Mock private PermissionGuardMetrics permissionGuardMetrics;

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
                controller.getPriceMemory(partnerId, productId, accountId.toString(), "SALES");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().unitPrice()).isEqualByComparingTo("123456.00");
        verify(dynamicPermissionClient, never()).check(
                accountId, "purchases.slip.edit", PermissionAction.UPDATE);
        verify(dynamicPermissionClient, never()).check(
                accountId, "estimates.list", PermissionAction.CREATE);
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
                controller.getPriceMemory(partnerId, productId, accountId.toString(), "SALES");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void getPriceMemories_partialHit_returnsHitOnlyAndAuthorizesOncePerRequest() {
        UUID accountId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID hitProductId = UUID.randomUUID();
        UUID missProductId = UUID.randomUUID();
        LocalDateTime rememberedAt = LocalDateTime.of(2026, 7, 15, 10, 0);
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(true);
        when(priceMemoryService.findAll(partnerId, List.of(hitProductId, missProductId))).thenReturn(List.of(
                new PartnerProductPriceMemoryBulkItemResponse(
                        hitProductId, new BigDecimal("123456.00"), "LINE_SAVE", rememberedAt)));

        ApiResponse<List<PartnerProductPriceMemoryBulkItemResponse>> response = controller.getPriceMemories(
                new PartnerProductPriceMemoryBulkRequest(partnerId, List.of(hitProductId, missProductId)),
                accountId.toString(), "SALES");

        assertThat(response.getData()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(hitProductId);
            assertThat(item.unitPrice()).isEqualByComparingTo("123456.00");
            assertThat(item.updatedAt()).isEqualTo(rememberedAt);
        });
        verify(dynamicPermissionClient).check(accountId, "sales.slip.create", PermissionAction.CREATE);
        verify(dynamicPermissionClient, never()).check(accountId, "purchases.slip.edit", PermissionAction.UPDATE);
        verify(dynamicPermissionClient, never()).check(accountId, "estimates.list", PermissionAction.CREATE);
    }

    @Test
    void singleAndBulk_samePairReturnSameWireValue() {
        UUID accountId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocalDateTime rememberedAt = LocalDateTime.of(2026, 7, 15, 11, 30);
        PartnerProductPriceMemoryResponse singleValue = new PartnerProductPriceMemoryResponse(
                new BigDecimal("550000.00"), "BUNDLE_SET", rememberedAt);
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(true);
        when(priceMemoryService.find(partnerId, productId)).thenReturn(Optional.of(singleValue));
        when(priceMemoryService.findAll(partnerId, List.of(productId))).thenReturn(List.of(
                new PartnerProductPriceMemoryBulkItemResponse(
                        productId, singleValue.unitPrice(), singleValue.source(), singleValue.updatedAt())));

        PartnerProductPriceMemoryResponse single = controller.getPriceMemory(
                partnerId, productId, accountId.toString(), "SALES").getBody().getData();
        PartnerProductPriceMemoryBulkItemResponse bulk = controller.getPriceMemories(
                new PartnerProductPriceMemoryBulkRequest(partnerId, List.of(productId)),
                accountId.toString(), "SALES").getData().get(0);

        assertThat(bulk.unitPrice()).isEqualByComparingTo(single.unitPrice());
        assertThat(bulk.source()).isEqualTo(single.source());
        assertThat(bulk.updatedAt()).isEqualTo(single.updatedAt());
    }

    @Test
    void getPriceMemory_withoutCreatePermission_returnsForbidden() {
        UUID accountId = UUID.randomUUID();
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("purchases.slip.edit"), eq(PermissionAction.UPDATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("estimates.list"), eq(PermissionAction.CREATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("estimates.list"), eq(PermissionAction.UPDATE)))
                .thenReturn(false);

        assertThatThrownBy(() -> controller.getPriceMemory(
                UUID.randomUUID(), UUID.randomUUID(), accountId.toString(), "SALES"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void getPriceMemory_withEstimateCreatePermission_returnsNoContent() {
        UUID accountId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("purchases.slip.edit"), eq(PermissionAction.UPDATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("estimates.list"), eq(PermissionAction.CREATE)))
                .thenReturn(true);
        when(priceMemoryService.find(partnerId, productId)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<PartnerProductPriceMemoryResponse>> response =
                controller.getPriceMemory(partnerId, productId, accountId.toString(), "SALES");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(dynamicPermissionClient, never()).check(
                accountId, "estimates.list", PermissionAction.UPDATE);
    }

    @Test
    void getPriceMemory_withPurchaseUpdatePermission_shortCircuitsEstimateChecks() {
        UUID accountId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("purchases.slip.edit"), eq(PermissionAction.UPDATE)))
                .thenReturn(true);
        when(priceMemoryService.find(partnerId, productId)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<PartnerProductPriceMemoryResponse>> response =
                controller.getPriceMemory(partnerId, productId, accountId.toString(), "PURCHASES");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(dynamicPermissionClient, never()).check(
                accountId, "estimates.list", PermissionAction.CREATE);
        verify(dynamicPermissionClient, never()).check(
                accountId, "estimates.list", PermissionAction.UPDATE);
    }

    @Test
    void getPriceMemory_withEstimateUpdatePermission_preservesFourthOrBranch() {
        UUID accountId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(dynamicPermissionClient.check(eq(accountId), eq("sales.slip.create"), eq(PermissionAction.CREATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("purchases.slip.edit"), eq(PermissionAction.UPDATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("estimates.list"), eq(PermissionAction.CREATE)))
                .thenReturn(false);
        when(dynamicPermissionClient.check(eq(accountId), eq("estimates.list"), eq(PermissionAction.UPDATE)))
                .thenReturn(true);
        when(priceMemoryService.find(partnerId, productId)).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse<PartnerProductPriceMemoryResponse>> response =
                controller.getPriceMemory(partnerId, productId, accountId.toString(), "SALES");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
