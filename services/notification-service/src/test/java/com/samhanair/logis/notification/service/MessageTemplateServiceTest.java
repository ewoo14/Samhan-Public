package com.samhanair.logis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.notification.client.OutboundSlipDto;
import com.samhanair.logis.notification.client.OutboundSlipDto.OutboundSlipLineDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MessageTemplateService} 단위 테스트 — PR-E1 BE-4 (3 case).
 *
 * <ol>
 *   <li>renderDispatchMessage — 시간 / 다중 품목 정상 포맷</li>
 *   <li>renderDispatchMessage — 주소 80자 초과 truncate (...)</li>
 *   <li>renderDispatchMessage — 시간 null + 품목 null/empty 처리</li>
 * </ol>
 */
class MessageTemplateServiceTest {

    private final MessageTemplateService service = new MessageTemplateService();

    @Test
    @DisplayName("정상 — 시간 HH:mm + 다중 품목 + 거래처/주소 표시")
    void renderDispatchMessage_normal() {
        OutboundSlipDto slip = new OutboundSlipDto(
                "OUT-2026-05-10-001",
                "P-2026-0001",
                "에어디자이너 주식회사",
                LocalDate.of(2026, 5, 10),
                LocalDateTime.of(2026, 5, 10, 14, 30),
                "서울시 강남구 테헤란로 123",
                List.of(
                        new OutboundSlipLineDto("산소호흡기 마스크", 5),
                        new OutboundSlipLineDto("필터 카트리지", 12)));

        String message = service.renderDispatchMessage(slip);

        assertThat(message).contains("[배차안내]");
        assertThat(message).contains("거래처: 에어디자이너 주식회사");
        assertThat(message).contains("시간: 14:30");
        assertThat(message).contains("주소: 서울시 강남구 테헤란로 123");
        assertThat(message).contains("산소호흡기 마스크 5개");
        assertThat(message).contains("필터 카트리지 12개");
    }

    @Test
    @DisplayName("주소 — 80자 초과 시 truncate + '...' 부착")
    void renderDispatchMessage_addressTruncate() {
        String longAddress = "서울특별시 강남구 테헤란로 길고긴주소".repeat(10); // > 80 char
        OutboundSlipDto slip = new OutboundSlipDto(
                "OUT-2026-05-10-002",
                "P-2026-0002",
                "거래처B",
                LocalDate.of(2026, 5, 10),
                LocalDateTime.of(2026, 5, 10, 9, 0),
                longAddress,
                List.of(new OutboundSlipLineDto("품목X", 1)));

        String message = service.renderDispatchMessage(slip);

        assertThat(message).contains("...");
        // 주소 라인이 80자 + "..." 길이 안에 들어감
        String addressLine = message.lines()
                .filter(l -> l.startsWith("주소: "))
                .findFirst()
                .orElseThrow();
        // "주소: " (4) + 80 + "..." (3) = 87
        assertThat(addressLine.length()).isLessThanOrEqualTo(4 + MessageTemplateService.ADDRESS_MAX_LENGTH + 3);
    }

    @Test
    @DisplayName("경계 — 시간 null = '시간 미정', 품목 empty = '품목 없음', 주소 blank = '주소 미입력'")
    void renderDispatchMessage_nullTimeAndEmptyLines() {
        OutboundSlipDto slip = new OutboundSlipDto(
                "OUT-2026-05-10-003",
                "P-2026-0003",
                "거래처C",
                LocalDate.of(2026, 5, 10),
                null,
                "  ",
                List.of());

        String message = service.renderDispatchMessage(slip);

        assertThat(message).contains("시간: 시간 미정");
        assertThat(message).contains("주소: 주소 미입력");
        assertThat(message).contains("품목 없음");

        // null slip → IllegalArgumentException
        assertThatThrownBy(() -> service.renderDispatchMessage(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
