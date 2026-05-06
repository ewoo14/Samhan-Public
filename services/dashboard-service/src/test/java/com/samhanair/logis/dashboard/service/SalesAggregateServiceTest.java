package com.samhanair.logis.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.dashboard.client.AccountingClient;
import com.samhanair.logis.dashboard.client.PartnerOrderClient;
import com.samhanair.logis.dashboard.domain.AggregateInterval;
import com.samhanair.logis.dashboard.domain.SalesAggregate;
import com.samhanair.logis.dashboard.repository.SalesAggregateRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * SalesAggregateService 단위 테스트 — Phase 9 W4 (5 case).
 *
 * <ol>
 *   <li>findAggregates — from > to → 400</li>
 *   <li>findAggregates — partnerId null + 정상 범위 → repository delegate (전체)</li>
 *   <li>findAggregates — partnerId 있음 → partner 필터 lookup</li>
 *   <li>aggregateOne — partnerId null → 400</li>
 *   <li>aggregateOne — 신규 row insert + 4 client fail-soft (ZERO/0) 정합</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesAggregateServiceTest {

    @Mock
    private SalesAggregateRepository repository;
    @Mock
    private AccountingClient accountingClient;
    @Mock
    private PartnerOrderClient partnerOrderClient;

    @InjectMocks
    private SalesAggregateService service;

    @BeforeEach
    void setup() {
        lenient().when(repository.save(any(SalesAggregate.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(accountingClient.sumSalesByPartner(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(partnerOrderClient.countOrdersByPartner(any(), any(), any())).thenReturn(0);
    }

    @Test
    void findAggregates_with_from_after_to_throws_400() {
        assertThatThrownBy(() -> service.findAggregates(
                LocalDate.now(), LocalDate.now().minusDays(7), AggregateInterval.DAILY, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("from 이 to");
    }

    @Test
    void findAggregates_without_partner_returns_all() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        when(repository.findAllByAggregateDateBetweenOrderByAggregateDateAsc(from, to))
                .thenReturn(List.of());

        List<SalesAggregate> result = service.findAggregates(from, to, AggregateInterval.DAILY, null);

        assertThat(result).isEmpty();
        verify(repository).findAllByAggregateDateBetweenOrderByAggregateDateAsc(from, to);
    }

    @Test
    void findAggregates_with_partner_filters() {
        UUID partnerId = UUID.randomUUID();
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        when(repository.findAllByPartnerIdAndAggregateDateBetweenOrderByAggregateDateAsc(partnerId, from, to))
                .thenReturn(List.of());

        List<SalesAggregate> result = service.findAggregates(from, to, AggregateInterval.WEEKLY, partnerId);

        assertThat(result).isEmpty();
        verify(repository).findAllByPartnerIdAndAggregateDateBetweenOrderByAggregateDateAsc(partnerId, from, to);
    }

    @Test
    void aggregateOne_with_null_partner_throws_400() {
        assertThatThrownBy(() -> service.aggregateOne(LocalDate.now(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("partnerId 필수");
    }

    @Test
    void aggregateOne_inserts_new_row_with_fail_soft_zeros() {
        LocalDate today = LocalDate.now();
        UUID partnerId = UUID.randomUUID();
        when(repository.findFirstByAggregateDateAndPartnerId(today, partnerId))
                .thenReturn(Optional.empty());

        SalesAggregate result = service.aggregateOne(today, partnerId);

        assertThat(result).isNotNull();
        assertThat(result.getPartnerId()).isEqualTo(partnerId);
        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getItemCount()).isZero();
        verify(repository).save(any(SalesAggregate.class));
    }
}
