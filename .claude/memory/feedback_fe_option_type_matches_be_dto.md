---
name: feedback_fe_option_type_matches_be_dto
description: FE 옵션 필드 타입은 BE DTO 타입과 정확 일치해야 — boolean vs String variant 불일치는 silent no-op
metadata:
  type: feedback
---

FE 가 BE 로 보내는 옵션/필드의 **타입**은 BE record/DTO 의 타입과 정확히 일치해야 한다. 이름만 맞고 타입이 다르면 Jackson 이 강제변환하여 **조용히 무효(silent no-op)** 가 된다.

**사례 (PR #439, 세트 전개 PR-3b)**: FE 가 `panelShape360` 을 `boolean`(체크박스, `true`/`false`)으로 모델링했으나 BE `BundleSetOptions.panelShape360` 은 `String`(`'원형'`/`'사각'`, `BundleExpander` 가 `shapeVal.equals(p.variant)` 로 패널 variant 정확매칭). Jackson 이 boolean→`"true"`/`"false"` 로 강제변환 → variant 와 절대 불일치 → 360 형상 선택이 무효. FE 를 `string|null` + 형상 셀렉트(미지정/원형/사각)로 교정.

**Why**: 이름 기반 계약 검토(필드명만 대조)는 타입 불일치를 못 잡는다. BE-contract 리뷰어도 필드명만 보고 APPROVE 했고, FE 리뷰어가 `BundleExpander` 실 사용처를 읽어 적발. [[feedback_enforcement_real_http_test]] 와 동류 — 정적 통과가 런타임 무효를 가린다.

**How to apply**:
- FE↔BE 계약 검토 시 필드명뿐 아니라 **타입**(특히 boolean vs String variant, number vs string)을 BE record 정의 + 실 소비처(matching 로직)까지 대조.
- 옵션이 "선택지 문자열 매칭"이면 FE 도 셀렉트/문자열로, boolean 토글로 착각 금지.
- 실 Docker QA 로 옵션 변화→출력 변화를 실증(예: `panelShape360="사각"`→패널 modelCode 실제 교체). Playwright 제출-본문 단언(FE 전송) + 실서버 honor(§3) = end-to-end 폐곡선.
