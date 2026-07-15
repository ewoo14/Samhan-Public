package com.samhanair.logis.slip.estimate.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.shared.realtime.collection.CollectionRealtimePublisher;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.ProductSummary;
import com.samhanair.logis.slip.estimate.domain.Estimate;
import com.samhanair.logis.slip.estimate.repository.EstimateRepository;
import com.samhanair.logis.slip.estimate.revision.service.EstimateRevisionService;
import com.samhanair.logis.slip.estimate.web.dto.CreateEstimateRequest;
import com.samhanair.logis.slip.estimate.web.dto.UpdateEstimateRequest;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** EstimateService — #809 거래처+품목 최근 단가 기억 훅 테스트. */
@ExtendWith(MockitoExtension.class)
class EstimateServicePriceMemoryTest {

    @Mock private EstimateRepository estimateRepository;
    @Mock private EstimateNumberService estimateNumberService;
    @Mock private ProductClient productClient;
    @Mock private EstimateToSlipConverter slipConverter;
    @Mock private EstimateRevisionService estimateRevisionService;
    @Mock private CollectionRealtimePublisher collectionRealtimePublisher;
    @Mock private PartnerProductPriceMemoryService priceMemoryService;

    @InjectMocks private EstimateService service;

    private UUID partnerId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        partnerId = UUID.randomUUID();
        productId = UUID.randomUUID();
        when(productClient.lookup(any())).thenReturn(List.of(
                new ProductSummary(productId, "에어컨", "M-1", "AC-001",
                        UUID.randomUUID(), new BigDecimal("1000.00"), "ACTIVE")));
    }

    @Test
    void create_remembersVatInclusiveInputPriceExactly() {
        when(estimateNumberService.next(any())).thenReturn("2026/05/16-1");
        when(estimateNumberService.extractSeqNo("2026/05/16-1")).thenReturn(1);
        when(estimateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateEstimateRequest(
                LocalDate.of(2026, 5, 16), partnerId, "삼한", null, null, null, null,
                List.of(new CreateEstimateRequest.EstimateLineRequest(
                        productId, "에어컨", "M-1", null, 1,
                        new BigDecimal("88000.00"), null, null, true))),
                "user-1", "홍길동");

        verify(priceMemoryService).remember(
                eq(partnerId), eq(productId), eq(new BigDecimal("88000.00")), eq("user-1"));
    }

    @Test
    void update_remembersOverridePrice() {
        UUID estimateId = UUID.randomUUID();
        Estimate estimate = Estimate.create("2026/05/16-1", LocalDate.of(2026, 5, 16), 1,
                partnerId, "삼한", null, null, null, null, "user-1");
        ReflectionTestUtils.setField(estimate, "id", estimateId);
        when(estimateRepository.findById(estimateId)).thenReturn(Optional.of(estimate));

        service.update(estimateId, new UpdateEstimateRequest(
                partnerId, "삼한", null, null, null, null,
                List.of(new UpdateEstimateRequest.EstimateLineUpdate(
                        productId, "에어컨", "M-1", null, 1,
                        new BigDecimal("99000.00"), null, null, true))),
                "user-2", "김매니저");

        verify(priceMemoryService).remember(
                eq(partnerId), eq(productId), eq(new BigDecimal("99000.00")), eq("user-2"));
    }
}
