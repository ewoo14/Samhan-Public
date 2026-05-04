package com.samhanair.logis.slip.delivery.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 배치에 슬립 1건 수동 추가 요청 — Plan §4.1. */
public record AddSlipToBatchRequest(@NotNull UUID slipId) {
}
