package com.samhanair.logis.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 배차안내 SMS 실 발송 요청 (PR-E1 BE-4).
 *
 * <p>POST /admin/notifications/dispatch-batch/send body. preview 단계에서 FE 가 메시지를 수정할 수
 * 있으므로 (partnerCode, message) 쌍을 명시 전달 받는다 — service 가 SMS provider 에 전달할 본문은
 * 본 요청의 message 필드 (preview 시점 자동 조립 본문 + FE 수정).
 *
 * <p>blocked 가드는 send 시점에도 재확인 (preview 와 send 사이에 BLOCK 등록될 가능성).
 *
 * @param date 배차일 (preview 와 동일 — 감사용 / 검증용)
 * @param entries 발송 대상 거래처별 (partnerCode + 수정된 message + 수신 전화번호) 목록
 */
public record DispatchBatchSendRequest(
        @NotNull LocalDate date,
        @NotNull @Size(min = 1, max = 1000) List<@Valid SendEntry> entries) {

    /**
     * 발송 대상 1건.
     *
     * @param partnerCode 거래처코드 (blocked 가드 키, 매핑 키)
     * @param recipientPhone 수신 전화번호 (단톡방 운영자 / 거래처 담당자) — EXTERNAL_PHONE 채널
     * @param message 발송할 본문 (preview 시점 조립 + FE 수정)
     * @param chatRoomName 단톡방 이름 (감사용 / 결과 그룹핑 용)
     */
    public record SendEntry(
            @NotBlank String partnerCode,
            @NotBlank @Size(max = 20) String recipientPhone,
            @NotBlank @Size(max = 2000) String message,
            @Size(max = 200) String chatRoomName) {
    }
}
