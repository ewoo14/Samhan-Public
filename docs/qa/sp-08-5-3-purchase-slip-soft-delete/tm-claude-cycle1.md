## Claude 5-agent 사이클 1 통합 리뷰 (head `7cbbd13b`)

> tech-manager 통합 — BE/FE/Designer/QA/DevOps. PR #222 SP-08-5-3 매입 soft delete. 사용자 6/7회차 정책.

### CI 상태 (리뷰 시점)

GitGuardian + Frontend DS/Mobile-Staff/Desktop arologis/Detox = PASS (5건). 백엔드/Frontend Desktop/Playwright IN_PROGRESS.

### 결함 종합 표

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | Designer | **MAJOR (D-1)** | `SlipDetailPage.tsx` L444-446 | 422 응답 `alert()` 처리 — Modal 내 `.danger-banner` 배너로 변경 (PNG 02 mock 정합) |
| 2 | FE / Designer | **Major (F-01 / D-2)** | `SlipDetailPage.tsx` L2024 | 409 배너 `className="error-banner"` ↔ `.danger-banner` 신규 정의 이중화 — `.danger-banner` 통일 |
| 3 | BE | MEDIUM (BE-1) | `SlipDeleteIT.java:177` D3 | `markDeleted` 직후 findById 1차 캐시 — `flush() + entityManager.clear()` 추가 |
| 4 | BE | MEDIUM (BE-2) | `SlipDeleteIT.java:226-248` D8 | INSPECTING 단일 — DisplayName "이후 단계" 정정 + COMPLETED case 추가 또는 명세 정정 |
| 5 | DevOps | MEDIUM (D1) | `shared/common/ErrorCode.java` | ErrorCode 2건 shared 추가 — 14 service 재컴파일. CI 결과 확인 후 판단 (현재 컨벤션 SP-08-5-2 동일 — 잔존 유지 가능) |
| 6 | Designer | MINOR (D-3) | `SlipDetailPage.tsx` L2019 | inline `color: var(--color-danger-700)` `.danger-banner` className 으로 통합 + fallback `#B91C1C` 제거 |
| 7 | Designer | INFO (D-4) | `global.css` L3019 | `.danger-banner` color 700 vs `.warning-banner` 800 — 단계 일관 또는 의도 주석 |
| 8 | FE | Minor (F-02) | `SlipDetailPage.tsx:592-595` | `canDirectDeletePurchase` SAVED/DRAFT BE 계약 정합 (SENT 이후 허용 시 가드 과도) |
| 9 | FE | Minor (F-03) | `SlipDetailPage.tsx:1997` | 확인 onClick `isPending` early-return 가드 권장 |
| 10 | BE | LOW (BE-3) | `SlipDeleteService.java:69` | `actorId == null` dead branch — 도메인/service 한 곳 정리 |
| 11 | BE | LOW (BE-4) | `SlipDeleteController.java:71-79` | parseActorId/resolveName SlipUpdateController 중복 — BaseSlipController 또는 SlipHeaderUtils 추출 (후속 슬라이스 적절) |
| 12 | BE | LOW (BE-5) | `SlipDeleteIT.java:54` 주석 | "8 케이스" → 9 정정 |
| 13 | QA | INFO (F1) | `SlipDeleteIT.java:269` | `containsOnly(1)` revisionNo 단언 취약 — 현행 허용 |
| 14 | QA | MINOR (F2) | dev-report §6 | "BE IT 9 case — 미수신" → "PASS: 9 / 0 failed" 갱신 |
| 15 | DevOps | LOW (D2) | `.gitattributes` 부재 | SP-08-5-2 회고 follow-up 유지 |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | **사이클 2 필요** (MEDIUM 2 + LOW 3) |
| FE | **사이클 2 필요** (Major 1 + Minor 2) |
| Designer | **CHANGES REQUESTED** (MAJOR 1 + MINOR 2 + INFO 1) |
| QA | **APPROVE** (INFO 1 + MINOR 1) |
| DevOps | **사이클 2 조건부** (MEDIUM 1 CI 대기 + LOW 1) |

### TM 결정 (사용자 6/7회차 정책 — 사이클 1c 일괄 fix)

- **1c Claude fix 후보** (PR 내 모든 결함 해결):
  1. **#1 Designer MAJOR D-1**: 422 응답 처리를 `alert()` → Modal 내 `.danger-banner` div 렌더 (`purchaseDeleteInspectionAlert` state 신설 + 배너 + dismiss)
  2. **#2 FE F-01 / Designer D-2**: 409 banner `className="error-banner"` → `className="danger-banner"` 변경
  3. **#3 BE-1**: `SlipDeleteIT D3` `slipRepository.flush()` 후 `entityManager.clear()` 추가
  4. **#4 BE-2**: D8 testDeleteCompletedReturns422 신규 추가 (COMPLETED 상태) + 기존 INSPECTING case DisplayName 정확화
  5. **#6 Designer D-3**: inline color → `.danger-banner` className 통합 + fallback `#B91C1C` 제거
  6. **#7 Designer D-4**: `.danger-banner` color `--color-danger-800` 으로 통일 (`.warning-banner` 패턴 일관)
  7. **#8 FE F-02**: BE 컨벤션 확인 — `canDirectDeletePurchase` 는 BE `EDITABLE_STATUSES` (`DRAFT/SAVED`) 와 정합 (BE 결정). 정정 불필요.
  8. **#9 FE F-03**: 확인 onClick `if (deletePurchaseSlipMutation.isPending) return` 가드 추가
  9. **#10 BE-3**: `SlipDeleteService.delete` actorId null 분기 제거 (parseActorId 가 zero UUID 보장)
  10. **#12 BE-5**: IT 주석 9 정정
  11. **#14 QA F2**: dev-report §6 "PASS: 9 / 0 failed" 갱신
- **사이클 1c 후속 (후속 슬라이스 후보)**:
  - #5 DevOps D1 ErrorCode shared 모듈 — slip-service 패키지 이동은 광범위 변경, SP-08-5-2 와 동일 패턴 (slip-service 가 shared 사용하는 것이 컨벤션). 현행 유지 + DevOps follow-up 메모리 기록
  - #11 BE-4 controller utility 추출 — 후속 슬라이스 적절
  - #13 QA F1 containsOnly — 현행 허용
  - #15 DevOps D2 `.gitattributes` — 후속 슬라이스
- **CI green 도달 시 PM 자동 머지** (사용자 7회차)
- **Codex 2a review 대기** — 1c fix push 후

**tech-manager — 2026-05-18**
