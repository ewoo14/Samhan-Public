---
name: feedback_sol_review_includes_live_qa
description: 🚨 SOL 적대검증은 코드만 읽지 말고 **직접 라이브QA 를 해야 한다** — 단위 테스트 근거로 낸 "결함 0" 이 라이브에서 뒤집힌 실측 (2026-08-07 개발책임자 지시)
metadata:
  type: feedback
---

# 🚨 SOL 적대검증도 **직접 라이브QA** 를 한다

> 개발책임자 (2026-08-07)
> *"SOL 검증 시에는 SOL도 라이브QA를 직접 해야함."*

## 규칙

적대검증 라운드의 판정문은 **실서버에서 직접 밟은 근거**를 포함해야 한다.

```text
🚫 안 됨   코드 읽기 + 단위 테스트 결과만으로 "도달 결함 0" 판정
✅ 해야 함  실서버 실행 · 실 GUI · 스크린샷 · 응답 원문
           그 근거로 "실 사용자 경로로 재현 가능한 결함" 을 판정
```

브리핑에 **"코드만 읽고 판정하지 말고 직접 밟아라"** 를 명시한다.
못 밟으면 *"관측 불가"* 로 적게 하고, **그것을 결함 0 으로 세지 않는다.**

## Why — 단위 테스트 근거의 "결함 0" 이 라이브에서 뒤집혔다

`#1102`/PR `#1103` 실측 (2026-08-07):

```text
S2 SOL 적대검증   결함 1 — 조회 중 미확정 단가가 확정값처럼 노출
S3 LUNA fix       네 곳을 props.line.lookupLoading 에 걸어 가림
S4 SOL 재검증     **결함 0** ← 단위 테스트 97/97 · 20회 반복 근거
R1 라이브QA       **결함 1** ← 실 화면은 여전히 `0` 과 `판매가` note 노출
```

왜 못 잡았나:

```java
// 가드를 건 조건
SlipFormPage.tsx:1331
  lookupLoading: Boolean(partnerId && productId && shouldAutoFill && !dcResult)   // 네 조건 AND
```
**테스트가 fixture 로 `lookupLoading: true` 를 직접 주입**했다. 그 플래그가 실제로
켜지는지는 아무도 검증하지 않았고, SOL 도 그 테스트를 근거로 0 을 냈다.

🔑 **SOL 이 라이브를 직접 밟았으면 S4 에서 잡혔다.** 그러면 R1 라운드와 되돌림이 없었다.

## 함께 지킬 것

- 라이브QA 는 **실서버 실제 실행**이다. `--list`·typecheck 류 정적 게이트로 대체 금지
- 스크린샷 다수 · `chromium.launch({ headless: true })` (사용자 PC 를 뺏지 않는다)
- 브리핑 맨 앞에 **환경 확인 절** (배포본이 이 PR 코드인가 · mock 이 아닌가)
- 발화 조건이 0 이면 **"판정 불가"** 이지 "결함 0" 이 아니다

## 이 규칙이 바꾸는 것

종전엔 **적대검증(코드) → 라이브QA(별도 라운드)** 였다. 이제 적대검증 자체가 라이브를 포함하므로
라운드가 하나 줄고, *"단위는 green 인데 화면은 아니다"* 를 **같은 라운드에서** 잡는다.

관련: [[feedback_canonical_workflow]] · [[feedback_live_qa_first_not_last]] ·
[[feedback_qa_pass_is_not_defect_zero]] · [[feedback_revert_when_defect_count_does_not_drop]] ·
[[feedback_live_qa_every_round_screenshots]]
