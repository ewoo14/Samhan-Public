package com.samhanair.logis.slip.domain;

/**
 * SlipSignatureAudit 의 action 분류 — Slice C (signature-slice-C Plan §3.1).
 *
 * <ul>
 *   <li>{@link #RECORD} — 서명 신규 등록 (공개 mobile endpoint POST 시)</li>
 *   <li>{@link #INVALIDATE} — 관리자 무효화 (admin DELETE, MASTER only)</li>
 * </ul>
 *
 * <p>VARCHAR(20) 컬럼 매핑.
 */
public enum SignatureAuditAction {
    RECORD,
    INVALIDATE,
    /** Slice C2 — 배송기사 서명 등록 (공개 mobile endpoint POST 시). */
    RECORD_DRIVER
}
