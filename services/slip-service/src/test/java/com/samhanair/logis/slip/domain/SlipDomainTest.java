package com.samhanair.logis.slip.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Slip 도메인 — 상태 전이 가드 + applyDeliveryTagAutoMemo + 헤더 수정 가드 검증. */
class SlipDomainTest {

    private static final UUID SOURCE_WH = UUID.randomUUID();
    private static final UUID DEST_WH = UUID.randomUUID();
    private static final UUID PARTNER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    @Test
    void createOutbound_setsDraft_andRequiresSourceWarehouse() {
        Slip slip = Slip.createOutbound("2026/05/04-001", LocalDate.of(2026, 5, 4), 1,
                SOURCE_WH, DEST_WH, PARTNER, "삼한공조",
                DeliveryTag.DAY, "메모", "user-1");

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.DRAFT);
        assertThat(slip.getSlipType()).isEqualTo(SlipType.OUTBOUND);
        assertThat(slip.getSourceWarehouseId()).isEqualTo(SOURCE_WH);
        assertThat(slip.getDestinationWarehouseId()).isEqualTo(DEST_WH);
        assertThat(slip.isEditable()).isTrue();
    }

    @Test
    void createOutbound_nullSourceWarehouse_throws() {
        assertThatThrownBy(() -> Slip.createOutbound("X-001", LocalDate.now(), 1,
                null, DEST_WH, PARTNER, "p", DeliveryTag.DAY, null, "u"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createInbound_setsSourceNull_andRequiresDestWarehouse() {
        Slip slip = Slip.createInbound("2026/05/04-002", LocalDate.of(2026, 5, 4), 2,
                DEST_WH, PARTNER, "삼한공조",
                DeliveryTag.RETURN, null, "user-1");

        assertThat(slip.getSourceWarehouseId()).isNull();
        assertThat(slip.getDestinationWarehouseId()).isEqualTo(DEST_WH);
        assertThat(slip.getSlipType()).isEqualTo(SlipType.INBOUND);
    }

    @Test
    void createInbound_nullDestWarehouse_throws() {
        assertThatThrownBy(() -> Slip.createInbound("X", LocalDate.now(), 1,
                null, PARTNER, "p", DeliveryTag.RETURN, null, "u"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createOutbound_inboundOnlyTag_throws() {
        assertThatThrownBy(() -> Slip.createOutbound("X", LocalDate.now(), 1,
                SOURCE_WH, DEST_WH, PARTNER, "p", DeliveryTag.RETURN, null, "u"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullOutboundLifecycle_endsAtConfirmed() {
        Slip slip = newOutbound();
        slip.addLine(SlipLine.create(slip, PRODUCT, "에어컨", "M-1", 5, new BigDecimal("100.00"), null));
        slip.save();
        slip.send();
        slip.accept("acc");
        slip.process();
        slip.complete();
        slip.ship();
        slip.deliver();
        slip.confirm();

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.CONFIRMED);
        assertThat(slip.getConfirmedAt()).isNotNull();
        assertThat(slip.getCompletedAt()).isNotNull();
        assertThat(slip.getAcceptedAt()).isNotNull();
        assertThat(slip.getAcceptedBy()).isEqualTo("acc");
    }

    @Test
    void inboundLifecycle_skipsShipDeliver() {
        Slip slip = newInbound();
        slip.addLine(SlipLine.create(slip, PRODUCT, "p", null, 1, new BigDecimal("10.00"), null));
        slip.save();
        slip.send();
        slip.accept("acc");
        slip.process();
        slip.complete();
        slip.confirm();

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.CONFIRMED);
    }

    @Test
    void inboundShip_throwsConflict() {
        Slip slip = newInbound();
        slip.save();
        slip.send();
        slip.accept("a");
        slip.process();
        slip.complete();

        assertThatThrownBy(slip::ship)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void send_fromDraft_throwsConflict() {
        Slip slip = newOutbound();
        assertThatThrownBy(slip::send)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void editHeader_blockedAfterSent() {
        Slip slip = newOutbound();
        slip.save();
        slip.send();

        assertThatThrownBy(() -> slip.editHeader(null, "변경", null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void editHeader_inDraft_appliesPartial() {
        Slip slip = newOutbound();
        slip.editHeader(null, "새거래처", DeliveryTag.STACK, "새메모");

        assertThat(slip.getPartnerName()).isEqualTo("새거래처");
        assertThat(slip.getDeliveryTag()).isEqualTo(DeliveryTag.STACK);
        assertThat(slip.getMemo()).isEqualTo("새메모");
    }

    @Test
    void cancel_fromAccepted_throwsConflict() {
        Slip slip = newOutbound();
        slip.save();
        slip.send();
        slip.accept("acc");

        assertThatThrownBy(slip::cancel)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void cancel_fromSent_succeeds() {
        Slip slip = newOutbound();
        slip.save();
        slip.send();
        slip.cancel();

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.CANCELED);
    }

    @Test
    void reject_fromSent_movesToRejected_andPrependsReason() {
        Slip slip = newOutbound();
        slip.editHeader(null, null, null, "원본메모");
        slip.save();
        slip.send();
        slip.reject("재고 부족");

        assertThat(slip.getStatus()).isEqualTo(SlipStatus.REJECTED);
        assertThat(slip.getMemo()).startsWith("[반려: 재고 부족]");
    }

    @Test
    void reject_fromDraft_throwsConflict() {
        Slip slip = newOutbound();
        assertThatThrownBy(() -> slip.reject("x"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyDeliveryTagAutoMemo_stack_prependsAutoLine() {
        Slip slip = Slip.createOutbound("X", LocalDate.of(2026, 5, 4), 1,
                SOURCE_WH, DEST_WH, PARTNER, "삼한",
                DeliveryTag.STACK, "원본", "u");
        slip.applyDeliveryTagAutoMemo();

        assertThat(slip.getMemo()).contains("야적").contains("상차").contains("하차").contains("원본");
    }

    @Test
    void applyDeliveryTagAutoMemo_dayTag_noOp() {
        Slip slip = Slip.createOutbound("X", LocalDate.of(2026, 5, 4), 1,
                SOURCE_WH, DEST_WH, PARTNER, "삼한",
                DeliveryTag.DAY, "원본", "u");
        slip.applyDeliveryTagAutoMemo();

        assertThat(slip.getMemo()).isEqualTo("원본");
    }

    @Test
    void slipLine_create_calculatesLineTotal() {
        Slip slip = newOutbound();
        SlipLine line = SlipLine.create(slip, PRODUCT, "에어컨", "M-1",
                3, new BigDecimal("1500.00"), null);

        assertThat(line.getLineTotal()).isEqualByComparingTo(new BigDecimal("4500.00"));
    }

    @Test
    void slipLine_changeQuantity_recalculatesLineTotal() {
        Slip slip = newOutbound();
        SlipLine line = SlipLine.create(slip, PRODUCT, "p", null,
                2, new BigDecimal("100.00"), null);
        line.changeQuantity(5);

        assertThat(line.getLineTotal()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void slipLine_negativeUnitPrice_throws() {
        Slip slip = newOutbound();
        assertThatThrownBy(() -> SlipLine.create(slip, PRODUCT, "p", null,
                1, new BigDecimal("-1.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slipLine_zeroQuantity_throws() {
        Slip slip = newOutbound();
        assertThatThrownBy(() -> SlipLine.create(slip, PRODUCT, "p", null,
                0, new BigDecimal("100.00"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void slipNumberSequence_next_increments() {
        SlipNumberSequence seq = SlipNumberSequence.create(LocalDate.of(2026, 5, 4));
        assertThat(seq.next()).isEqualTo(1);
        assertThat(seq.next()).isEqualTo(2);
        assertThat(seq.next()).isEqualTo(3);
        assertThat(seq.getLastSeq()).isEqualTo(3);
    }

    private Slip newOutbound() {
        return Slip.createOutbound("2026/05/04-001", LocalDate.of(2026, 5, 4), 1,
                SOURCE_WH, DEST_WH, PARTNER, "삼한공조",
                DeliveryTag.DAY, null, "user-1");
    }

    private Slip newInbound() {
        return Slip.createInbound("2026/05/04-002", LocalDate.of(2026, 5, 4), 2,
                DEST_WH, PARTNER, "삼한공조",
                DeliveryTag.RETURN, null, "user-1");
    }
}
