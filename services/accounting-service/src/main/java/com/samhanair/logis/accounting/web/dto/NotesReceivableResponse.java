package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.NoteStatus;
import com.samhanair.logis.accounting.domain.NoteType;
import com.samhanair.logis.accounting.domain.NotesReceivable;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 받을어음 응답. UUID 는 노출하지 않고 noteNo 와 거래처 식별자만 반환한다. */
public record NotesReceivableResponse(
        String noteNo,
        String partnerCode,
        String bizNo,
        String partnerName,
        LocalDate issueDate,
        LocalDate maturityDate,
        BigDecimal amount,
        NoteType noteType,
        NoteStatus status,
        String memo
) {
    public static NotesReceivableResponse of(NotesReceivable note, PartnerDisplay partner) {
        return new NotesReceivableResponse(
                note.getNoteNo(),
                partner.partnerCode(),
                partner.bizNo(),
                partner.partnerName(),
                note.getIssueDate(),
                note.getMaturityDate(),
                note.getAmount(),
                note.getNoteType(),
                note.getStatus(),
                note.getMemo()
        );
    }

    /** API 표시용 거래처 정보. */
    public record PartnerDisplay(String partnerCode, String bizNo, String partnerName) {
    }
}
