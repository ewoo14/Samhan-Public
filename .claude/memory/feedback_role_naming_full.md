---
name: 권한 표기 — 풀네임 의무 (약어 금지)
description: PR/Issue/문서/주석에서 Role enum 표기 시 풀네임 사용. M/M/D 같은 1글자 약어 금지
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: 모든 PR 본문, Issue 본문, README, QA 리포트, 코멘트 등 사람이 읽는 문서에서 권한 표기는 **반드시 풀네임**(`MASTER`, `MANAGER`, `DEVELOPER`, `SALES`, `ACCOUNTANT`, `WAREHOUSE`, `INVENTORY`) 사용. **`M/M/D`, `M/M/D/A` 같은 1글자 약어 금지**.

**Why**: 2026-05-04 PR #7 (Product Service BE) 의 권한 컬럼에 `M/M/D` 사용 → 개발책임자가 "권한을 너무 축약해서 썼음" 지적. 이유:
- 7개 Role 중 첫글자가 겹치는 경우 있음 (MASTER/MANAGER 둘 다 M)
- 약어는 컨텍스트 없이 읽으면 의미 추론 불가
- Code 가 아닌 문서 가독성에서 풀네임이 압도적으로 유리

**적용 형식**:
- Markdown 표: `MASTER, MANAGER, DEVELOPER` 콤마 구분 (스페이스 1)
- 모든 권한 허용: "인증된 모든 사용자" 또는 "전 권한"
- 권한 없음: "공개" 또는 "비인증 가능"

**예시**:
```
| Method | Path | 권한 |
|---|---|---|
| GET    | `/products` | 인증된 모든 사용자 |
| POST   | `/products` | MASTER, MANAGER, DEVELOPER |
| PATCH  | `/products/{id}/price` | MASTER, MANAGER, DEVELOPER, ACCOUNTANT |
```

**예외 (코드는 OK)**: Java `@PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER')")` 같은 코드는 어차피 enum 명을 써야 하므로 그대로. 본 규칙은 **사람이 읽는 산문/문서** 에만 적용.

**과거 위반 사례**: PR #7 본문 권한 표에서 M/M/D, M/M/D/A 사용 → 즉시 풀네임으로 정정. 본 메모리는 향후 모든 팀별 PR 에서 적용.
