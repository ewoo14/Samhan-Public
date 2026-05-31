## Codex QA 사이클 2 리뷰 (head `67791758`)

### 판정
- **Claude QA 사이클 2 주요 판정은 대체로 valid**입니다. IT 6건은 성공/409/404/403/422/MASTER 주문번호 path를 커버하고, `@BeforeEach` outbox 선삭제 + 외부 client 7개 `@MockBean` 격리도 확인했습니다.
- **사이클 1 Gap 해소 확인**: dev-report §6의 Playwright 상태는 `BLOCKED`가 아니라 `PASS — 4 passed, 0 skipped, 0 failed (3.3s)`로 수정되어 있습니다.
- **PNG 4장 확인**: `01-edit-form`, `02-reload`, `03-audit-timeline`, `04-role-guard-partner` 모두 non-zero이며 화면상 UUID 노출은 보이지 않습니다.

### Coverage Gaps
- **Minor**: Playwright 4건은 브라우저 상호작용이 아니라 `fs.readFileSync` 기반 정적 계약 테스트입니다. T1~T4 계약 회귀 방어에는 유효하지만, 실제 modal open/fill/save, 409 banner click, audit timeline 렌더링까지 E2E로 검증하지는 않습니다. 현재 Codex sandbox/browser 실행 제약을 고려하면 blocker는 아니고 residual risk로 보는 것이 맞습니다.
- **Minor**: IT 성공 케이스는 audit field `납기/요청사항/주문 라인`의 old/new를 단언하지만, 같은 요청에서 변경되는 `거래처 코드/사업자번호`의 old/new까지 모두 per-field 단언하지는 않습니다. 서비스 diff에는 포함되어 있으므로 테스트 강화 여지는 있습니다.
- **Non-blocking**: 409 발생 후 reload를 누르고 최신 `updatedAt`으로 재저장 성공하는 흐름은 없습니다. 낙관적 잠금 UX의 완전한 회귀 방어로는 후속 Playwright/browser 케이스가 더 적합합니다.
- **Invalid/over-engineering**: 수정 후 list 화면 refresh 시나리오는 이번 direct PUT이 `orderNumber`를 바꾸지 않고 상세 화면 중심이므로 필수 회귀로 보기는 어렵습니다.

### 결론
QA 관점에서 merge blocker는 없습니다. 단, 실제 브라우저 기반 Playwright 1건과 audit old/new 전체 필드 단언은 후속 hardening 후보로 남깁니다.

**Codex QA-agent — 2026-05-17**
