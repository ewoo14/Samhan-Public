---
name: feedback_reconvergence_before_merge
description: 검증 라운드 fix(특히 상태머신·타이밍·불변식 변경, CI/늦은 포착 fix 포함) 후 머지 전 재수렴 적대라운드 의무. CI/vitest/타깃QA green ≠ 수렴. 적대 [NEW]/심각도 라벨은 baseline git diff로 pre-existing 여부 확증 후 disposition. 2026-07-17 #825 슬1.
metadata:
  type: feedback
---

**사건(2026-07-17 #825 슬1)**: CODEX SOL R2 fix + CI "mock 회귀 hard gate" 회귀 fix(handleChange debounce 상태머신 변경) 후, **vitest 61/810 · ac-2/ac-3 Playwright 14 · 타깃 라이브QA 좁은 재검증만 하고 수렴 선언·머지**. 개발책임자 "재수렴 리뷰 한거 맞아?" 지적. 소급 재수렴(OPUS handleChange · OPUS 종합잔여 · CODEX SOL 3렌즈) 돌리니 **결과는 깨끗(슬1 순변경 기준 0 신규 HIGH/MED)** 이었으나, **머지 전 재수렴을 안 돌린 규율 공백은 실재**. 결과가 다행히 양호했을 뿐.

**Why (두 실책)**:
1. **CI 게이트 통과 = 수렴 착각**. fix 가 상태머신(candidates/status/debounce)을 바꿨는데 좁은 테스트로만 재검증하고, 그 fix 자체를 적대적으로 재검증(0 신규 HIGH/MED 확인)하지 않고 머지. 캐논 "0수렴까지 반복" 의 재수렴 라운드를 생략.
2. **적대 [NEW] 라벨 검증 전 수용(과잉 경보)**. 소급 재수렴에서 CODEX·OPUS종합이 pre-existing 결함을 `[NEW] MED` 로 오판했고, PM(나)이 baseline 확인 전 그 라벨을 받아들여 "머지 성급했다" 경보. `git show <merge-sha> -- <file>`(부모 대비 순변경) 로 직접 대조하니 **둘 다 pre-existing**(항목 A 는 오히려 슬1 이 stale 창을 단축 = 개선). 렌즈는 중간 이터레이션(R1 임시상태)·현재 코드 형상만 보고 pre-existing 을 NEW 로 오판할 수 있음.

**How to apply**:
1. **검증 라운드 fix(상태머신·타이밍·불변식·debounce·선택로직 변경) 후 = 좁은 재검증으로 끝내지 말 것.** 머지 전 **재수렴 적대라운드**(그 fix + 파생을 새 눈으로 적대검증, 신규 HIGH/MED 0 수렴 확인)를 반드시 1회. **CI·늦은 포착으로 인한 fix 도 동일** — 그 fix 자체가 재수렴 대상이다.
2. **CI green · vitest green · 타깃QA green ≠ 수렴.** 수렴의 정의 = 적대라운드가 신규 HIGH/MED 0. 게이트 통과는 필요조건이지 충분조건 아님.
3. **적대검증 [NEW]/심각도 라벨은 그대로 수용/무마 금지 → baseline git diff 로 확증 후 disposition.** `git show <sha> -- <file>` 또는 부모 대비로 pre-existing vs 슬라이스도입을 실측. PM 은 검증 전 경보(과잉)와 무마(은폐) 둘 다 금지 — 근거는 diff 다.
4. **pre-existing 확증 LOW** = 개발책임자 disposition(이슈 등록/후속 슬롯 흡수). **슬라이스 도입 HIGH/MED** = 현 PR 내 fix + 재수렴.
5. **🚨 2-model 재수렴 = 한 모델 '수렴' 선언을 단독 신뢰 금지**(2026-07-18 #825 슬2 실증): OPUS 재수렴이 "0 confirmed·수렴 완료"로 판정한 코드를 **CODEX SOL이 매 라운드 실엣지를 반복 포착**(partnerCode 길이 계약·CM-b 빈draft 우회·동명 거래처 가드 우회). 반대로 CODEX가 `[NEW] MED` 오판한 것을 baseline diff로 반증하기도 함. → **양 모델 모두 돌리고**(OPUS+CODEX), 어느 하나의 "수렴/미수렴" 단독 판정 금지. 6라운드까지 갈 수 있으니 narrow 엣지는 [[feedback_pm_regulate_slice_effort]]로 바운드하며 수렴(내 fix가 낳은 신규결함도 재수렴이 포착 — AA·autoFocus·mock갭).

→ [[feedback_design_system_playwright_mock_suite]](이 사건의 CI 포착 계기)·[[feedback_pm_regulate_slice_effort]](BATCH disposition·재수렴 1회)·[[feedback_canonical_workflow]](0수렴까지 반복)·[[feedback_recon_grep_false_negative]](검증 없는 단언 금지)·[[feedback_no_fake_data_ever]](정직 보고).
