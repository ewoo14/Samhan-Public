package com.samhanair.logis.user.lock;

import com.samhanair.logis.user.domain.Employee;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * user-service 잠금 정책용 가상 status — PR-H4b (Phase 12 Step 4b).
 *
 * <p>{@link Employee} 자체는 status 컬럼이 없고 {@code terminationDate} 필드의 null 여부로
 * 활성/비활성을 구분한다. 본 enum 은 EditLockGuard 정책 분기를 위한 가상 status — 호출자가
 * {@code Employee.getTerminationDate()} 검사 후 본 enum 으로 매핑.
 *
 * <p><b>정책</b>:
 * <ul>
 *   <li>{@link #ACTIVE} — 자유 mutation (terminationDate == null)</li>
 *   <li>{@link #DEACTIVATED} — APPROVED 1건 소진 후 mutation 가능 (terminationDate != null)</li>
 * </ul>
 *
 * <p>Department 는 status 컬럼 없음 — 모두 ACTIVE 로 매핑 (편의상). 향후 Department 자체에
 * 활성/비활성 컬럼 도입 시 본 enum 확장.
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {

    ACTIVE("활성"),
    DEACTIVATED("비활성");

    private final String displayName;

    /** {@link Employee} → {@link UserStatus} 매핑 helper. */
    public static UserStatus from(Employee employee) {
        return employee.getTerminationDate() == null ? ACTIVE : DEACTIVATED;
    }
}
