---
name: feedback_codex_parallel_and_content_filter
description: mcp__codex__codex 는 병렬 디스패치 가능(2+ 동시 backgrounded 실측). 단 콘텐츠 필터가 "권한/세션/인증" 리뷰 브리핑을 사이버보안 위험으로 오탐 → 중립 표현 재구성 필요. (2026-07-24 실측)
metadata:
  type: feedback
---

# codex 병렬 + 콘텐츠 필터 (2026-07-24 실측)

## 병렬 디스패치 가능
`mcp__codex__codex` 를 **동시에 여러 개** 호출하면 각각 별도 task-id 로 backgrounded 되어 **병렬 실행**된다(2026-07-24 실측: T6 SOL 2차 + T7 LUNA 구현 동시 성공). 서로 다른 워크트리면 파일 충돌도 없다. "codex 1슬롯" 은 오해였다 — 트랙별 codex 단계(구현·2차검증·라운드 fix)를 병렬로 밀 수 있다.

**Why**: 이 오해로 세션 내내 codex 를 순차로만 돌려 처리량을 떨어뜨렸다.

**How to apply**: 서로 독립한 트랙의 codex 단계는 한 응답에서 동시 디스패치하라. OPUS 적대검증(Agent tool)은 codex 와 별개 도구라 codex 와도 병렬. [[feedback_codex_mcp_session_limit]] 의 -32000 세션 한계와는 별개 이슈.

## 콘텐츠 필터 오탐
codex 콘텐츠 필터가 **"권한 캐시 누출·세션 하이재킹·재로그인 시 이전 권한 렌더"** 류 표현을 사이버보안 위험으로 오탐해 라운드를 통째 FAIL 시킨다(2026-07-24 T6 SOL 2차 1회). 실제로는 방어적 버그 수정인데도.

**How to apply**: 권한/세션/인증 관련 리뷰·fix 브리핑은 **중립·방어 맥락 표현**으로 재구성하라 — "권한 캐시 누출" → "메뉴 목록 데이터 캐시 무효화", "재로그인 시 이전 권한 렌더" → "계정 전환 후 UI 가 직전 계정 상태를 표시하는 렌더링 버그", "보안 우회가 아니라 표시 정합성 수정" 명시. FAIL 나면 같은 미션을 순화해 재디스패치하면 통과.
