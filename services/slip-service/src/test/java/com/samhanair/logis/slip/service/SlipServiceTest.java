package com.samhanair.logis.slip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.client.InventoryClient;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.ProductSummary;
import com.samhanair.logis.slip.domain.DeliveryTag;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import com.samhanair.logis.slip.repository.SlipRepository;
import com.samhanair.logis.slip.web.dto.CreateSlipRequest;
import com.samhanair.logis.slip.web.dto.EditHeaderRequest;
import com.samhanair.logis.slip.web.dto.SlipDetailResponse;
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

/** SlipService — lifecycle + Inventory mock 호출 검증. */
@ExtendWith(MockitoExtension.class)
class SlipServiceTest {

    @Mock private SlipRepository slipRepository;
    @Mock private SlipNumberService slipNumberService;
    @Mock private ProductClient productClient;
    @Mock private InventoryClient inventoryClient;

    @InjectMocks private SlipService service;

    private UUID productId;
    private UUID sourceWh;
    private UUID destWh;
    private UUID partnerId;
    private UUID slipId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        sourceWh = UUID.randomUUID();
        destWh = UUID.randomUUID();
        partnerId = UUID.randomUUID();
        slipId = UUID.randomUUID();

        lenient().when(productClient.lookup(any())).thenReturn(List.of(
                new ProductSummary(productId, "에어컨", "M-1", UUID.randomUUID(),
                        new BigDecimal("1000.00"), "ACTIVE")));
        lenient().when(productClient.requireExists(productId)).thenReturn(
                new ProductSummary(productId, "에어컨", "M-1", UUID.randomUUID(),
                        new BigDecimal("1000.00"), "ACTIVE"));
    }

    // ---------- create ----------

    @Test
    void create_outbound_returnsDraft_andCallsProductLookup() {
        when(slipNumberService.next(any(LocalDate.class))).thenReturn("2026/05/04-001");
        when(slipNumberService.extractSeqNo("2026/05/04-001")).thenReturn(1);
        when(slipRepository.save(any(Slip.class))).thenAnswer(inv -> {
            Slip s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", slipId);
            return s;
        });

        CreateSlipRequest req = new CreateSlipRequest(
                SlipType.OUTBOUND, LocalDate.of(2026, 5, 4),
                sourceWh, destWh, partnerId, "삼한공조", DeliveryTag.DAY, "메모",
                List.of(new CreateSlipRequest.SlipLineRequest(productId, "에어컨", "M-1",
                        2, new BigDecimal("100.00"), null)));

        SlipDetailResponse res = service.create(req, "user-1");

        assertThat(res.status()).isEqualTo(SlipStatus.DRAFT);
        assertThat(res.slipNo()).isEqualTo("2026/05/04-001");
        assertThat(res.lines()).hasSize(1);
        assertThat(res.lines().get(0).lineTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        verify(productClient).lookup(any());
    }

    @Test
    void create_inbound_setsSourceNull() {
        when(slipNumberService.next(any(LocalDate.class))).thenReturn("2026/05/04-002");
        when(slipNumberService.extractSeqNo("2026/05/04-002")).thenReturn(2);
        when(slipRepository.save(any(Slip.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSlipRequest req = new CreateSlipRequest(
                SlipType.INBOUND, LocalDate.of(2026, 5, 4),
                null, destWh, partnerId, "삼한", DeliveryTag.RETURN, null,
                List.of(new CreateSlipRequest.SlipLineRequest(productId, "p", null,
                        1, new BigDecimal("10.00"), null)));

        SlipDetailResponse res = service.create(req, "user-1");

        assertThat(res.slipType()).isEqualTo(SlipType.INBOUND);
        assertThat(res.sourceWarehouseId()).isNull();
        assertThat(res.destinationWarehouseId()).isEqualTo(destWh);
    }

    // ---------- accept (OUTBOUND inventory reserve) ----------

    @Test
    void accept_outbound_callsInventoryReserve_perLine() {
        Slip slip = preparedOutbound(SlipStatus.SENT, 2, new BigDecimal("100.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.accept(slipId, "warehouse-1");

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.ACCEPTED);
        verify(inventoryClient, times(1))
                .reserve(eq(productId), eq(sourceWh), eq(2), anyString(), eq(slipId));
    }

    @Test
    void accept_inbound_doesNotCallInventoryReserve() {
        Slip slip = preparedInbound(SlipStatus.SENT);
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.accept(slipId, "warehouse-1");

        verify(inventoryClient, never())
                .reserve(any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void accept_fromDraft_throwsConflict_andDoesNotCallInventory() {
        Slip slip = preparedOutbound(SlipStatus.DRAFT, 1, new BigDecimal("10.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        assertThatThrownBy(() -> service.accept(slipId, "u"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        verify(inventoryClient, never()).reserve(any(), any(), anyInt(), anyString(), any());
    }

    // ---------- complete ----------

    @Test
    void complete_outbound_callsInventoryDeduct_fromReservationTrue() {
        Slip slip = preparedOutbound(SlipStatus.PROCESSING, 3, new BigDecimal("50.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.complete(slipId);

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.COMPLETED);
        verify(inventoryClient, times(1))
                .deduct(eq(productId), eq(sourceWh), eq(3), eq(true), anyString(), eq(slipId));
    }

    @Test
    void complete_inbound_callsInventoryInbound() {
        Slip slip = preparedInbound(SlipStatus.PROCESSING);
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.complete(slipId);

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.COMPLETED);
        verify(inventoryClient, times(1))
                .inbound(eq(productId), eq(destWh), anyInt(), anyString(), any(BigDecimal.class));
    }

    // ---------- reject ----------

    @Test
    void reject_fromAccepted_outbound_callsRelease() {
        Slip slip = preparedOutbound(SlipStatus.ACCEPTED, 4, new BigDecimal("20.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.reject(slipId, "manager-1", "재고 없음");

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.REJECTED);
        verify(inventoryClient, times(1))
                .release(eq(productId), eq(sourceWh), eq(4), anyString(), eq(slipId));
    }

    @Test
    void reject_fromSent_doesNotCallRelease() {
        Slip slip = preparedOutbound(SlipStatus.SENT, 1, new BigDecimal("10.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.reject(slipId, "manager-1", "잘못된 신청");

        verify(inventoryClient, never())
                .release(any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void reject_fromDraft_throwsConflict() {
        Slip slip = preparedOutbound(SlipStatus.DRAFT, 1, new BigDecimal("10.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        assertThatThrownBy(() -> service.reject(slipId, "m", "x"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    // ---------- cancel ----------

    @Test
    void cancel_fromSaved_succeeds_noInventoryCall() {
        Slip slip = preparedOutbound(SlipStatus.SAVED, 1, new BigDecimal("10.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.cancel(slipId, "u");

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.CANCELED);
        verify(inventoryClient, never()).release(any(), any(), anyInt(), anyString(), any());
    }

    @Test
    void cancel_fromAccepted_throwsConflict_perDomainGuard() {
        Slip slip = preparedOutbound(SlipStatus.ACCEPTED, 1, new BigDecimal("10.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        assertThatThrownBy(() -> service.cancel(slipId, "u"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    // ---------- editHeader ----------

    @Test
    void editHeader_inDraft_appliesPartial() {
        Slip slip = preparedOutbound(SlipStatus.DRAFT, 1, new BigDecimal("10.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        service.editHeader(slipId,
                new EditHeaderRequest(null, "새거래처", null, "새메모"), "u");

        assertThat(slip.getPartnerName()).isEqualTo("새거래처");
        assertThat(slip.getMemo()).isEqualTo("새메모");
    }

    @Test
    void editHeader_inSent_throwsConflict() {
        Slip slip = preparedOutbound(SlipStatus.SENT, 1, new BigDecimal("10.00"));
        when(slipRepository.findById(slipId)).thenReturn(Optional.of(slip));

        assertThatThrownBy(() -> service.editHeader(slipId,
                new EditHeaderRequest(null, "x", null, null), "u"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    // ---------- read ----------

    @Test
    void getOne_notFound_throwsNotFound() {
        when(slipRepository.findById(slipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOne(slipId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ---------- helpers ----------

    private Slip preparedOutbound(SlipStatus status, int qty, BigDecimal unitPrice) {
        Slip slip = Slip.createOutbound("2026/05/04-001", LocalDate.of(2026, 5, 4), 1,
                sourceWh, destWh, partnerId, "삼한공조", DeliveryTag.DAY, null, "u");
        ReflectionTestUtils.setField(slip, "id", slipId);
        slip.addLine(SlipLine.create(slip, productId, "에어컨", "M-1", qty, unitPrice, null));
        forceStatus(slip, status);
        return slip;
    }

    private Slip preparedInbound(SlipStatus status) {
        Slip slip = Slip.createInbound("2026/05/04-002", LocalDate.of(2026, 5, 4), 2,
                destWh, partnerId, "삼한", DeliveryTag.RETURN, null, "u");
        ReflectionTestUtils.setField(slip, "id", slipId);
        slip.addLine(SlipLine.create(slip, productId, "p", null, 1, new BigDecimal("10.00"), null));
        forceStatus(slip, status);
        return slip;
    }

    private void forceStatus(Slip slip, SlipStatus status) {
        ReflectionTestUtils.setField(slip, "status", status);
    }
}
