package com.samhanair.logis.user.domain;

/**
 * EmployeeSignatureAudit 의 action 분류 - C1a (slip slip_signature_audit 미러).
 *
 * <ul>
 *   <li>{@link #RECORD} - 서명 신규 등록(업로드/모바일). 재등록도 새 RECORD 1건.</li>
 *   <li>{@link #INVALIDATE} - 관리자 무효화(MASTER).</li>
 * </ul>
 *
 * <p>VARCHAR(20) + DB CHECK(action IN ('RECORD','INVALIDATE')) 와 정확히 일치.
 */
public enum SignatureAuditAction {
    RECORD,
    INVALIDATE
}
