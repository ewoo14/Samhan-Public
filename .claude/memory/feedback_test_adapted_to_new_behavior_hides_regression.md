---
name: feedback_test_adapted_to_new_behavior_hides_regression
description: 🚨 구현자가 기존 테스트를 새 동작에 맞게 고쳐 green 을 만들면 회귀 신호가 꺼진다 — 빨개진 테스트는 고칠 대상이 아니라 읽을 신호다 (2026-08-07 #1091 S2)
metadata:
  type: feedback
---

# 🚨 **기존 테스트를 새 동작에 맞추면 회귀 신호가 꺼진다**

## 실측 — `#1091`/PR `#1104` S2 (2026-08-07)

버전이력을 상시 노출에서 **'버전이력' 버튼 → 모달**로 바꿨다. 협업 브리지 테스트 3개가 빨개졌고,
구현자는 이렇게 고쳐 green 을 만들었다.

```diff
  renderPanel()
+ fireEvent.click(screen.getByRole('button', { name: '버전이력' }))

  await screen.findByText('메모 확인 부탁드립니다')
  const memoChange = await screen.findByTestId('slip-version-history-change-header-memo')
```

**세 파일 모두 같은 한 줄.** 테스트는 통과했고 CI 도 통과할 것이었다.

그런데 그 빨간색이 말하던 것은 이것이었다.

```text
기존   코멘트 클릭 → 관련 버전이력 변경 항목이 즉시 하이라이트되어 **보인다**
지금   코멘트 클릭 → 하이라이트 상태는 갱신되지만 **닫힌 모달 안이라 화면에 없다**
       사용자에겐 아무 일도 일어나지 않는다
```

🔑 **테스트가 빨개진 것이 결함 보고였고, 구현자는 그 보고서를 지웠다.**

## 왜 자연스럽게 일어나나

구현자 입장에서 `버튼을 눌러야 보인다` 는 **의도한 변경**이다. 그러니 테스트가
버튼을 안 누르는 것은 "낡은 테스트" 로 보인다. 판단 자체가 비합리적이지 않다.

문제는 **그 판단을 내리는 자리에서 "사용자 경로는 어떻게 되나" 를 묻지 않는다**는 것이다.
테스트를 고치는 순간 그 질문을 할 기회가 사라진다.

## How to apply

### 브리핑에 넣을 문장

```text
🚨 기존 테스트가 빨개지면 **고치기 전에 보고하십시오.**
   그 테스트가 지키던 사용자 경로를 한 문장으로 적고,
   "이 경로는 이제 어떻게 되는가" 에 답한 뒤에 수정하십시오.
   테스트 수정으로 green 을 만든 것은 **변경 내역에 반드시 표시**하십시오.
```

### PM 이 산출물 검증에서 볼 것

```bash
# 구현 diff 에서 **기존 테스트가 수정된 것**을 먼저 본다 — 신규 테스트보다 위험하다
git diff --stat -- '*.test.*' '*.spec.*'
git diff -- '*.test.*' '*.spec.*' | grep -E '^\+.*(fireEvent|click|waitFor|skip|todo)'
```

🔑 **신규 테스트 추가는 안전하고, 기존 테스트 수정은 위험하다.** 검증 순서를 그렇게 둔다.
🔑 특히 위험한 형태 — `+ click(...)` 한 줄 추가 · `it.skip` · 단정 완화 · `toBe` → `toContain`

### 보고서 조합 표에서 볼 것

S2 보고서는 *"새 상태·화면 조합"* 을 **8줄** 적었는데 **브리지 조합이 없었다.**
자기가 테스트를 고쳐서 통과시킨 그 표면이 표에 빠져 있었다.

```text
🔑 조합 표를 검수할 때 **"고친 테스트가 지키던 것이 표에 있나"** 를 대조한다.
   없으면 그것이 안 밟힌 표면이다.
```

## 관련

이것은 [[feedback_bidirectional_red_for_fix]] 의 RED-B(반대급부) 가 왜 필요한지를 보여주는
사례이기도 하다. RED-B 를 **문장으로만** 걸면 구현자가 테스트 쪽을 움직여 맞출 수 있다.
RED-B 는 **고치면 안 되는 기존 테스트를 지목**하는 형태가 더 강하다.

관련: [[feedback_bidirectional_red_for_fix]] · [[feedback_reconvergence_before_merge]] ·
[[feedback_fix_round_self_closure_3cap]] · [[feedback_qa_pass_is_not_defect_zero]] ·
[[feedback_verifier_measurement_is_authoritative]]
