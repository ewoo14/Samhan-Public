package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.NoteStatus;
import jakarta.validation.constraints.NotNull;

/** 받을어음 상태 전이 요청. */
public record UpdateNotesReceivableStatusRequest(@NotNull NoteStatus status) {
}
