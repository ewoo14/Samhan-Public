# SP-D4 잔여 7 도메인 PermissionGuard 이중 가드 마이그레이션

> 연관 Plan: [`docs/planning/2026-05-18_sp-d4-remaining-pages-permission-migration.md`](../../planning/2026-05-18_sp-d4-remaining-pages-permission-migration.md)
> 연관 이전 슬라이스: SP-D3 #243 (`2c182af0`)
> 베이스: `main`

## 슬라이스 목표

SP-D1~D3 (회계/슬립/배차/SMS) 완료 후, **잔여 핵심 사용자 노출 도메인 7개** (견적/거래처주문/재고/직원/거래처/상품/아로지스) controller 에 **PermissionGuard 이중 가드** 추가. RoleGuard `@PreAuthorize` 보존(회귀 차단). PageCode enum 19 → 41(+22), Flyway V10 으로 22 PageCode × 7 ROLE = 154 row seed 추가.

## 변경 요약

### BE — 8 서비스 BUILD SUCCESSFUL
- `PageCode.java` +22 enum 상수
- `V10__sp_d4_remaining_domains_page_permissions.sql` — 154 seed row (ON CONFLICT 멱등성)
- 도메인 PermissionGuard 7 shared component 신규 + DynamicPermissionClient 5 서비스 이식
- 13 controller 메서드 수정 — `@RequestHeader X-User-Role` + guard 호출, `@PreAuthorize` 보존
- IT 7 신규 × 4 case = 28 case

### FE — typecheck/lint/build PASS
- `permissionsApi.ts` PageCode 19 → 41
- `mock.ts` 22 신규 PageCode 매트릭스 (154 cell)
- `AppLayout.tsx` 사이드바 22 코드 dynamicCanAccess + hidden 정합
- `routes/index.tsx` 14 라우트 PermissionGuard 추가

### Designer — typecheck PASS
- `PermissionMatrixPage.tsx` 13 카테고리 그룹 thead 3행 구조
- design-system token `--color-brand-50/200/700` 사용 / 신규 컴포넌트 0건

### QA — Playwright 20/20 PASS
- `sp-d4-remaining-pages-permission-migration.spec.ts` 14 case + 6 회귀 가드
- 시나리오 / IT cross-check / domain integrity / 사이드바 7 역할 스크린샷

### DevOps
- V10 dry-run / 롤백 SQL / 롤링 배포 가이드 / Grafana 알람 완화
- CI 8 서비스 기존 matrix 포함 확인 / docker-compose 영향 없음 (classpath Flyway)

## TM cross-check: APPROVE

| Check | 결과 |
|---|---|
| UUID 정합성 | ✅ role_page_permissions 단일 테이블 |
| API contract | ✅ Gateway JWT→X-User-Role 자동 mutate |
| 디자인 일관성 | ✅ tokens.css brand 토큰 실존 |
| 도메인 정합 | ✅ 7 guard SP-D3 패턴 일관 |
| Flyway 의존성 | ✅ V7~V9 와 0 중복 |
| SP-D2/D3 회귀 | ✅ V10 INSERT only |
| 컴파일 검증 | ✅ BE 8 + FE typecheck |

### cycle 2 fix 1건 PM 통합 commit 에 포함

- `routes/index.tsx` `/admin/blocked-partners` → `PermissionGuard partners.block` view 추가 (TM 권장 backlog 사이클 절약)

## SP-D5 이연

- RoleGuard `@PreAuthorize` 제거 (단일 가드화)
- 권한 캐시 invalidation event-driven
- AOP/Aspect 통합

## QA 스크린샷

### Playwright 14 case
- T01~T14 스크린샷: `clients/desktop/playwright/sp-d4-remaining-pages-permission-migration/screenshots/`

### 사이드바 7 역할 비교
- `docs/qa/sp-d4-remaining-pages-permission-migration/screenshots/sidebar-{master,manager,accountant,sales,warehouse,dispatch,inventory}.png`

## 메모리 가드 일관성

- ✅ `feedback_multi_agent_team_pattern.md` — 5-team 디스패치
- ✅ `feedback_integrated_pr_pattern.md` — 통합 PR (단편 PR 금지)
- ✅ `feedback_it_mockbean_external_clients.md` — 28 IT 모두 `@MockBean` + lenient stub + `X-User-Role` 헤더
- ✅ `feedback_korean_commits.md` — 한국어 본문
- ✅ `feedback_pm_integration_build_check.md` — BE+FE 사전 컴파일 검증
- ✅ `feedback_dual_5agent_review.md` — Claude + Codex 5-agent 양쪽 리뷰 진행 예정

🤖 Generated with [Claude Code](https://claude.com/claude-code)
