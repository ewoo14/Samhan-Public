package com.samhanair.logis.inventory.web.dto;

import jakarta.validation.constraints.Size;

/** 이동전표 입고 확정 요청 — 메모만. */
public record ReceiveRequest(@Size(max = 500) String note) {
}
