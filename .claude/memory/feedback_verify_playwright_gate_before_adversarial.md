---
name: feedback_verify_playwright_gate_before_adversarial
description: 구현이 Playwright/E2E 게이트 스펙을 생성하면 PM은 그 게이트가 CI에서 green인지(또는 로컬 실행) 적대검증·다음 단계 전에 확인. "playwright 실행 금지" 지시로 미검증 시 push 후 CI 하드게이트 RED 서프라이즈. mock 모드는 page.route no-op 트랩. 2026-07-18 #845 DS-1.
metadata:
  type: feedback
---

**사건(2026-07-18 #845 DS-1)**: CODEX LUNA 구현 디스패치 시 PM이 "**playwright는 생성만·실행 금지**(PM 라이브 검증)"이라 지시. 신규 `ac-845-ds1-form-renderer.spec.ts`가 생성됐고 vitest/typecheck만 검증 후 커밋·adversarial 착수. 그런데 push 후 CI **`Desktop Playwright (mock 회귀 hard gate)` = RED**(머지 불가). PM은 이를 놓쳤고, **DevOps 적대검증 에이전트가 `gh pr checks`로 포착**. 근본원인: 스펙이 **지어낸 결재 id(`playwright-ds1-fixture`)**를 사용 → mock 스토어에 없음 → mock 404 → 에러배너 → 대상 DOM 미마운트 → 15s 타임아웃. 게다가 mock 모드는 axios 인터셉터가 클라이언트 레벨서 short-circuit해 실 HTTP 미발생 → 스펙의 `page.route(...)` **절대 발동 안 함**([[feedback_inprocess_mock_principles]] page.route no-op 트랩).

**Why**:
1. **"playwright 실행 금지" 지시가 게이트 검증 공백을 만듦** — vitest green·typecheck green으로 "구현 OK" 판단하고 넘어갔으나, 새로 생성된 **Playwright 스펙 자체가 CI 하드게이트를 RED로 만드는지**는 미검증. CI 권위(exact SHA)를 push 후에야 알게 됨.
2. **mock 하네스 함정 미인지** — mock 모드(VITE_MOCK_MODE)는 클라이언트 인터셉터 short-circuit이라 `page.route` 오버라이드가 무효(no-op)이고, 시드에 없는 id는 404. 스펙이 **mock 시드 데이터 기준**으로 작성돼야 함.

**How to apply**:
1. **구현이 Playwright/E2E(또는 CI 하드게이트에 자동 수집되는) 스펙을 생성·수정하면 = PM이 그 게이트를 검증 후 다음 단계.** ① 로컬에서 그 스펙을 메인 config로 실행해 green 확인하거나, ② 커밋·push 후 **CI 해당 게이트(예: Desktop Playwright mock hard gate) green을 적대검증/재수렴/머지 전에 `gh pr checks`로 확인.** "PM 라이브 검증은 나중" 으로 미루면 CI RED가 늦게 터져 라운드 낭비. **"vitest+typecheck green = 구현 OK"는 Playwright 게이트를 포함하지 않는다.**
2. **mock 모드 Playwright 스펙 = mock 시드 데이터 기준 작성** — 지어낸 id 금지. mock 스토어 시드 id(예 `77777777-aaaa-...`) 사용. **mock-handled 엔드포인트에 `page.route` 오버라이드 의존 금지**(short-circuit로 no-op). 필요 데이터는 mock 스토어에 시드하거나 시드 콘텐츠 기준 단언([[feedback_inprocess_mock_principles]]).
3. **구현 dispatch 시 "playwright 실행 금지"를 걸었으면, PM이 그 몫을 반드시 대신 수행** — 지시로 위임한 검증은 PM 책임으로 회수. 미수행 채로 넘어가면 CI가 대신 잡되(RED) 늦다.

**추가 사건(2026-07-20 #832)**: mock parity 슬라이스가 **mock.ts 공유 픽스처 데이터**를 변형(시드 통장거래 matchedPartnerName 삼한상사→삼한공조 A·CODEF '운임 정산' counterparty를 긴 거래처코드 `1234567890`으로)했는데 PM이 **vitest·typecheck·BE만 로컬 검증하고 전체 Playwright mock 게이트를 미실행** → push 후 CI `Desktop Playwright (mock 회귀 hard gate)` **2건 RED**(bank-bulk-receipt=벌크바 매칭명 기대 stale·codef-fe-bc3=긴 counterparty가 모바일 거래처 autocomplete 드롭다운을 160px 셀에 클리핑). **교훈 확장**: 게이트 RED는 **컴포넌트/스펙 변경뿐 아니라 mock.ts 데이터·공유 픽스처 변경**으로도 터진다(다른 Playwright 스펙이 그 픽스처값에 의존). ⇒ **mock.ts(데이터 포함) 변경 = 전체 Playwright mock 게이트 로컬 실행 필수**(`node_modules/.bin/playwright test`·vitest만으론 공유 픽스처 회귀 미포착). 공유 픽스처는 되도록 불변 유지하고 신규 테스트는 자기 fixture 격리([[feedback_parallel_agent_gradle_shared_tree_contention]] 공유자원 경합 계열). **회사PC 함정**: `@axe-core/playwright`(devDep) 미설치 시 게이트가 collection 단계서 `Cannot find package` 로 **전체 중단**(false "green") → `npm install @axe-core/playwright` 후 실행(package.json 버전 변경분은 원복).

→ [[feedback_design_system_playwright_mock_suite]](mock 게이트가 권위)·[[feedback_inprocess_mock_principles]](page.route no-op)·[[feedback_parallel_agent_gradle_shared_tree_contention]](CI 권위=exact SHA)·[[feedback_changed_module_full_test_before_push]].
