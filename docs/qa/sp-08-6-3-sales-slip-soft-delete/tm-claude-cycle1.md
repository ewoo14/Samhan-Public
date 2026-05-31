## Claude 5-agent 사이클 1 통합 리뷰 (head `8f92213e`)

> tech-manager 통합 — BE/FE/Designer/QA/DevOps. SP-08-6-3 매출 soft delete.

### CI 상태

22 SUCCESS + 1 IN_PROGRESS + 0 FAILURE. GitGuardian SUCCESS.

### 결함 종합 표

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | Designer / FE / QA | **MAJOR** | `SlipDetailPage.tsx` L555, `SalesQueryPage.tsx` L277 | 403/fallback `alert()` 잔존 — SP-08-5-3 D-1 재발. `.danger-banner` Modal 내 렌더 + state 분리 (`salesDeleteForbiddenAlert` 또는 동등) + Playwright T2 보강 |
| 2 | FE | **MAJOR (F-02)** | `SalesQueryPage.tsx` L961-971 | 409 충돌 배너에 "최신 내용 불러오기" reload 버튼 누락 — SP-08-6-2 modal 패턴 위반. reload 버튼 + refetchDetail 또는 invalidateQueries 추가 |
| 3 | Designer | **MAJOR (D-1)** | `SlipDetailPage.tsx` L2596 | 삭제 confirm modal 거래처 표시 누락 — `slipNo` + `partnerName` 양쪽 표시 필요 |
| 4 | Designer | **MAJOR (D-2)** | `SlipDetailPage.tsx` L551 | 422 SHIPPED 배너 텍스트 "출고 완료된" → "출고 진행 중이거나 완료된" + `<strong>삭제 불가</strong>` + `<p>` 2단 구조 |
| 5 | BE | **MEDIUM (B-1)** | `Slip.deleteForSales()` | `requireNotLocked()` 호출 누락 — 회계 마감 lock 우회. `cancel()` 패턴 일관 (상태 가드 후 lock 체크) |
| 6 | Designer / FE | MINOR (D-5/F-03) | 삭제 confirm 버튼 disabled | `salesDeleteShippedAlert !== null` 시 disabled 추가 (무한 422 루프 방지) |
| 7 | Designer | MINOR (D-4) | success toast slipNo | "전표가 삭제되었습니다" → "전표가 삭제되었습니다. ({slipNo})" |
| 8 | QA | MINOR (F-01/F-02) | 403/fallback alert() | Designer/FE 동일 — 통합 fix |
| 9 | FE | LOW (F-04) | SalesQueryPage L885 주석 | "최신 내용 불러오기" 주석-구현 불일치 — F-02 fix 시 동반 |
| 10 | BE | LOW (B-2) | `SlipSalesDeleteIT D9` | `containsOnly(1)` revisionNo 가정 명시 또는 `greaterThanOrEqualTo(1)` |
| 11 | BE | INFO (B-3) | `SalesSlipDeleteService` Javadoc | `@link SalesSlipUpdateService#verifyVersion` private 참조 → 자기 참조 |
| 12 | QA | INFO (F-03) | `SlipSalesDeleteIT D3` | `markDeleted()` 직접 호출 → `deleteForSales()` 우회. cascade soft-delete 검증 보강 권고 |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (MEDIUM B-1 lockFlag + LOW/INFO) |
| FE | 사이클 2 필요 (MAJOR F-01/F-02 + MINOR F-03 + LOW F-04) |
| Designer | **CHANGES REQUESTED** (MAJOR D-1/D-2/D-3 + MINOR D-4/D-5) |
| QA | 사이클 2 필요 (MINOR alert + INFO) |
| DevOps | **APPROVE** (결함 0, CI 22/23 SUCCESS) |

### TM 결정 (사용자 6/7회차 정책 — 1c 일괄 fix)

**1c Claude fix 후보**:
1. **MAJOR Designer D-3/FE F-01/QA F-01-F-02**: 403/fallback alert() → `salesDeleteForbiddenAlert` + `salesDeleteErrorAlert` state + `.danger-banner` 인라인 렌더 + SalesQueryPage 동일 적용 + Playwright T2 보강 (not.toContain alert 추가 단언)
2. **MAJOR FE F-02**: SalesQueryPage 409 배너에 reload 버튼 + handleSalesDeleteConflictReload (또는 invalidateQueries + refetch)
3. **MAJOR Designer D-1**: 삭제 confirm modal `거래처: {partnerName}` 추가
4. **MAJOR Designer D-2**: 422 SHIPPED 배너 텍스트 "출고 진행 중이거나 완료된" + `<strong>삭제 불가</strong>` + `<p>` 구조
5. **MEDIUM BE B-1**: `Slip.deleteForSales()` 에 `requireNotLocked()` 추가 (상태 가드 후 lock 체크)
6. **MINOR Designer D-5/FE F-03**: confirm 버튼 disabled 조건 `salesDeleteShippedAlert !== null` 추가
7. **MINOR Designer D-4**: success toast slipNo 포함
8. **LOW BE B-2**: D9 IT `greaterThanOrEqualTo(1)` 또는 주석 명시
9. **INFO BE B-3**: Javadoc @link 자기 참조
10. **INFO QA F-03**: SlipSalesDeleteIT D3 `markDeleted` → `deleteForSales` 호출 변경

**CI green 유지 확인** + Codex 2a review 진행.

**tech-manager — 2026-05-18**
