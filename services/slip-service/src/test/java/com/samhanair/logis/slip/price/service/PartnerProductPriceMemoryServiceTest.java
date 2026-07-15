package com.samhanair.logis.slip.price.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.slip.price.domain.PartnerProductPriceMemory;
import com.samhanair.logis.slip.price.repository.PartnerProductPriceMemoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** PartnerProductPriceMemoryService — 최근 단가 조회/저장 계약 테스트. */
@ExtendWith(MockitoExtension.class)
class PartnerProductPriceMemoryServiceTest {

    @Mock private PartnerProductPriceMemoryRepository repository;

    @InjectMocks private PartnerProductPriceMemoryService service;

    @Test
    void find_returnsVatInclusiveInputPriceWithoutTransforming() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PartnerProductPriceMemory memory = PartnerProductPriceMemory.create(
                partnerId, productId, new BigDecimal("110000.00"), "LINE_SAVE");

        when(repository.findByPartnerIdAndProductId(partnerId, productId)).thenReturn(Optional.of(memory));

        Optional<PartnerProductPriceMemoryResponse> response = service.find(partnerId, productId);

        assertThat(response).isPresent();
        assertThat(response.get().unitPrice()).isEqualByComparingTo("110000.00");
    }

    @Test
    void remember_skipsNullPartnerId() {
        service.remember(null, UUID.randomUUID(), new BigDecimal("100.00"), "actor");

        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void remember_propagatesRepositoryFailureForCallerFailSoftBoundary() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        doThrow(new RuntimeException("db down")).when(repository)
                .upsert(org.mockito.Mockito.any(), org.mockito.Mockito.eq(partnerId), org.mockito.Mockito.eq(productId),
                        org.mockito.Mockito.any(), org.mockito.Mockito.eq("LINE_SAVE"),
                        org.mockito.Mockito.eq("actor"), org.mockito.Mockito.any(LocalDateTime.class));

        assertThatThrownBy(() -> service.remember(partnerId, productId, new BigDecimal("100.00"), "actor"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");

        verify(repository).upsert(org.mockito.Mockito.any(), org.mockito.Mockito.eq(partnerId), org.mockito.Mockito.eq(productId),
                org.mockito.Mockito.any(), org.mockito.Mockito.eq("LINE_SAVE"),
                org.mockito.Mockito.eq("actor"), org.mockito.Mockito.any(LocalDateTime.class));
    }
}
