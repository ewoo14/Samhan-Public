package com.samhanair.logis.dcconfig.lock;

import com.samhanair.logis.dcconfig.domain.DcConfig;
import com.samhanair.logis.dcconfig.domain.DcConfigSource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * dc-config-service 잠금 정책용 가상 status — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link DcConfig} 자체는 status 컬럼이 없고 {@link DcConfigSource} 가 시드 출처를 표시한다.
 * 본 enum 은 EditLockGuard 정책 분기를 위한 가상 status — 호출자가 {@code DcConfig.getSource()}
 * 검사 후 본 enum 으로 매핑.
 *
 * <p><b>정책</b>:
 * <ul>
 *   <li>{@link #DRAFT} — ADMIN_EDIT (관리자 수동 편집) — 자유 mutation</li>
 *   <li>{@link #IN_USE} — LEGACY_CSV / NOTION_DB / INTERNAL_RPC (외부 시드/RPC 적용) → APPROVED 후
 *       mutation 가능 (정책 적용 후 변경 신중)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum DcConfigStatus {

    DRAFT("초안"),
    IN_USE("적용 중");

    private final String displayName;

    /** {@link DcConfig} → {@link DcConfigStatus} 매핑 helper. */
    public static DcConfigStatus from(DcConfig dcConfig) {
        DcConfigSource source = dcConfig.getSource();
        if (source == null || source == DcConfigSource.ADMIN_EDIT) {
            return DRAFT;
        }
        return IN_USE;
    }
}
