# QA Cycle 1 리뷰 — audit Slice A PR #259 (followup-cleanup)

> 작성자: QA agent (Claude) | 일자: 2026-05-19
> 검토 대상: clients/desktop/playwright/admin-hr/admin-hr-guard.spec.ts
> 참조 파일: clients/desktop/playwright/audit/full-screen-audit.spec.ts, sp-d4-remaining-pages-permission-migration.spec.ts

---

## 판정: CONDITIONAL APPROVE (조건부 승인 — P2 권고 1건)

2건 검증 항목 모두 충족 확인. 회귀 위험 0건. 단, isServerAvailable 패턴이 SP-D4 계보와 불일치하여 향후 통일 권고(P2).

---

## 검증 1. 10 spec isServerAvailable() 가드 패턴 일관성

### 확인 범위

`clients/desktop/playwright/` 하위 전체 54개 spec 파일 중 `isServerAvailable` 함수를 보유한 파일은 32개다. 이 중 두 가지 패턴이 병존한다.

**패턴 A (Skip 패턴)**: `test.skip(!ok, "...")` — admin-hr-guard.spec.ts, full-screen-audit.spec.ts, manual-capture.spec.ts, slip-rename.spec.ts 등 다수.

**패턴 B (Fail 패턴)**: `expect(ok, "...").toBe(true)` — sp-d1-dynamic-rbac.spec.ts, sp-d2-accounting-permission-migration.spec.ts, sp-d3-slip-dispatch-permission-migration.spec.ts, sp-d4-remaining-pages-permission-migration.spec.ts. 이 4개 파일은 헤더 주석에 명시적으로 "false green (|| true / test.skip(!ok) / page.setContent() fallback) 0건" 을 가드 요구사항으로 기재하고, `test.skip(!ok)` 패턴을 금지 패턴으로 자기 검증 TC까지 보유한다.

### admin-hr-guard.spec.ts 검증 결과

`isServerAvailable()` 구현은 `http.default.get` + `hostname/port/path/'/'` + `timeout: 2_000` + `req.on('error')` + `req.on('timeout')` 4-line 구조로 패턴 A 계보(admin-hr, full-screen-audit, manual 3종)의 표준 구현과 정확히 동일하다. `test.beforeEach` 내 `test.skip(!ok, ...)` 호출도 동일 계보 내에서 일관된다.

`http.default.get` (dynamic import `await import('http')` 후 `.default.get`) 방식은 full-screen-audit.spec.ts(L46-L53), manual-capture.spec.ts, slip-rename.spec.ts 와 동일하다. sp-d4는 정적 import `import * as http from 'http'`를 사용하는데 이는 패턴 B 계보 차이이며, admin-hr-guard는 패턴 A 계보 내에서 일관하다.

5 정적 명시 주석(TC-HR1~TC-HR5 각 섹션 구분 주석 5건)은 129~337행에 걸쳐 모두 존재함을 확인했다.

**결론: 패턴 A 계보 내 일관성 충족. P2 권고 — 향후 신규 spec 작성 시 SP-D4 패턴 B(expect.toBe(true))로 통일 검토 필요. 이번 PR 범위 내 결함 아님.**

---

## 검증 2. TC-HR3 (L243-254) 조기 반환 강화

### 변경 전후 분석

TC-HR3(L222-288)는 SALES 역할에서 인사 카테고리 `nav-category-hr` 또는 `nav-admin-users` 요소의 disabled 상태와 클릭 후 URL 무변화를 검증한다.

L241-254의 조기 반환 분기는 `isVisible = false`일 때 실행된다. 변경 전 패턴(단순 `return`)은 서버는 가동됐으나 요소가 숨겨진 경우 TC가 검증 없이 통과되는 false-green 위험을 가졌다.

변경 후(현재 코드 L243-255):

```
expect(
  isVisible,
  'TC-HR3: SALES 에게 인사 카테고리 nav 요소가 숨겨져야 함 (완전 숨김 = 허용된 가드 동작)',
).toBe(false)
return
```

`expect(isVisible).toBe(false)` explicit assertion이 조기 반환 직전에 삽입되어 있다. 이 assertion은 `isVisible = false`가 보장된 후에 실행되므로 항상 pass하지만, 의도가 명시적으로 기록되고, 만약 향후 리팩터링으로 `isVisible`이 조기 반환 전에 재할당되는 경우를 방어한다. `console.info` 로그도 L247에서 안내 메시지를 출력한다.

스크린샷 캡처(`capture(page, 'TC-HR3-sales-hr-hidden')`)가 L249에 배치되어 있어 숨김 상태의 시각 증거가 docs/qa/admin-hr-category-and-disabled-ux 에 저장된다. 이는 PR 본문 스크린샷 의무(feedback_pr_qa_screenshots)를 충족한다.

**결론: `expect(isVisible).toBe(false)` explicit assertion 추가 확인. false-green 방지 강화 달성. 이상 없음.**

---

## 검증 3. 27 backlog spec 별도 issue 명시 여부

`clients/desktop/playwright/` 하위에서 `isServerAvailable`을 보유하지 않은 spec 22개(= 54 - 32)가 존재한다. 이 파일들은 `accounting-close-menu-gap`, `full-menu-contract`, `partner-ui-menu-gap`, `photo-audit`, `purchase-inspection-cta`, `sp-05`, `sp-06`, `sp-07`, `sp-08-3-2`, `sp-08-3-dispatch-parity`, `sp-08-4-1`, `sp-08-4-3`, `sp-08-4-4`, `sp-08-5-2`, `sp-08-5-3`, `sp-08-5-4`, `sp-08-5-5`, `sp-08-6-2`, `sp-08-6-3`, `sp-08-6-4`, `sp-08-7`, `sp-08-8` 계열이다. 이 22개에 `qa/playwright/tests/` 하위 40개 spec을 포함하면 isServerAvailable 미적용 spec이 다수 잔존한다.

현재 PR 범위에서 이 backlog spec들에 대한 별도 Issue 참조가 spec 파일 내 주석이나 PR 본문에 명시되어 있지 않다. 범위 외 항목이므로 이번 cycle 결함으로 판정하지 않으나, 기술부채 issue 생성을 권고한다.

**권고: backlog 22+ spec의 isServerAvailable 가드 미적용 건을 GitHub Issue로 생성하여 추적 관리 권장. 현재 PR 승인 블로커 아님.**

---

## 검증 4. 다른 spec 회귀 위험

`admin-hr-guard.spec.ts` 변경이 타 spec에 미치는 영향을 분석했다.

- `isServerAvailable` 함수는 파일 내 로컬 정의이며 export 없음. 타 spec 참조 불가.
- `HR_MENU_TEST_IDS` 상수 역시 파일 내 로컬 정의. 외부 의존 없음.
- `attachPageErrorHook`, `waitForSettle`, `capture`, `buildUrl` 헬퍼 모두 파일 내 로컬. 타 spec 영향 없음.
- `SCREENSHOT_DIR`은 `docs/qa/admin-hr-category-and-disabled-ux` 경로로 고정. 기존 spec의 스크린샷 경로와 충돌 없음.
- Playwright `test.describe` 구조가 단일 describe 블록 내에서 완결되어 전역 상태 오염 없음.

**결론: 다른 spec 회귀 위험 0건 확인.**

---

## 종합 판정표

| 검증 항목 | 결과 | 비고 |
|---|---|---|
| isServerAvailable http.get HEAD 2s timeout 구조 | 통과 | 패턴 A 계보 일관 |
| test.skip(!ok) + 안내 메시지 | 통과 | beforeEach 내 L130-131 확인 |
| 5 정적 명시 주석 (TC-HR1~TC-HR5) | 통과 | L135/172/219/291/337 확인 |
| TC-HR3 조기 반환 강화 — expect(isVisible).toBe(false) | 통과 | L250-254 명시적 assert 확인 |
| TC-HR3 스크린샷 캡처 (숨김 상태 증거) | 통과 | L249 capture 호출 확인 |
| 27 backlog spec 별도 issue 권고 명시 | P2 권고 | PR 범위 외, 블로커 아님 |
| 타 spec 회귀 위험 | 없음 | 로컬 함수 격리 확인 |
| isServerAvailable 패턴 A/B 불일치 | P2 권고 | SP-D4 계보와 향후 통일 권고 |

P1 결함: 없음. P2 권고: 2건 (backlog issue 생성, 패턴 B 통일 검토). 코드 수정 불필요.
