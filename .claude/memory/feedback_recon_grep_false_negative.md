---
name: recon-grep-false-negative
description: 정찰에서 "문자열 grep 0매치 = 기능 미배선/미존재"는 오판 가능 — 실 파일/라우트 존부로 검증
metadata:
  type: feedback
---

정찰(특히 핸드오프에 박제하는 정찰)에서 **특정 문자열(page-code·식별자) grep 0매치를 "기능 미배선/미구현"으로 결론짓지 말 것.** grep 부재 ≠ 기능 부재.

**Why:** 2026-06-23 후속3. 이전 세션 핸드오프가 "arologis-desktop 6 page-code 중 5개 미배선(0파일)"로 박제했으나 **오판**이었다. 실제로는 5개 백오피스 페이지(Employees/Departments/Cashbook/Accounts/Permissions)가 전부 존재·라우팅·머지(#426~#433)된 상태였고, page-code 문자열이 FE 소스에 없던 이유는 단지 **FE가 page-code 아닌 롤(canManageHr 등)로 게이팅**했기 때문. 잘못된 정찰 premise가 다음 세션을 "5개 기능 신설" 오해로 출발시킬 뻔했다(실제 작업=롤→canAccess 정렬).

**How to apply:**
- "X가 없다/미배선이다" 결론 전에 **실 파일 트리(find)·라우트 정의·메뉴 배선을 직접 확인**. 기능은 다른 식별자·다른 게이팅 방식으로 존재할 수 있다.
- 핸드오프에 정찰 결과를 박제할 때 "정찰 확정"이라 단언하기 전, 핵심 주장 1~2개는 파일 존부로 교차검증.
- 메모리가 "완결"이라 하는데 정찰이 "미존재"라 하면(또는 반대) **둘 다 의심하고 파일시스템으로 규명** — 한쪽을 무비판 채택 금지.
- 라이브 결과 해석도 코드로 검증([[per-round-live-qa]]) + 스펙 false-RED 주의([[realqa-run-and-false-red]])의 정찰 버전.

**참조:** [[per-round-live-qa]] / [[realqa-run-and-false-red]] / [[local-stack-qa-gotchas]]
