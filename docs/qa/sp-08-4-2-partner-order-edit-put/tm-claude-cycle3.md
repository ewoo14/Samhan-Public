## Claude 5-agent 사이클 3 통합 리뷰 (head `232c5637`)

> tech-manager agent 가 BE / FE / Designer / QA / DevOps 5 agent 결과 종합.

### 결함 종합 표

| 출처 | 우선순위 | 위치 | 내용 | 처리 권고 |
|---|---|---|---|---|
| BE | P1 | `PartnerOrder.java:102, replaceLines:208-210` | `@OneToMany(orphanRemoval=true)` + `@SQLRestriction` + markDeleted 조합 의미 충돌 — markDeleted 라인은 컬렉션에서 제거되지 않으므로 orphanRemoval 이 실질 무동작. soft-delete 전략이면 `orphanRemoval=false` 로 변경하고 Javadoc 명시 필요 | 사이클 4 fix |
| BE | P2 | `PartnerOrderUpdateService.java:53, verifyVersion:80-84` | `modifiedAt == null` 시 무조건 409 → 신규 주문 첫 수정 거부 가능. `createdAt` fallback 또는 `requestUpdatedAt==null` 통과 정책 명시 필요 | 사이클 4 fix |
| BE | Nit | `PartnerOrderIdResolver.java:58` | `catch (RuntimeException ignored)` 범위 과다, Repository 내부 DB 예외도 묻힘. `IllegalArgumentException` 으로 좁히기 권고 | 사이클 4 fix |
| BE | Nit | `PartnerOrderUpdateService.java:63-66` | `saveAndFlush` 직후 `partnerOrderRepository.flush()` 재호출 중복. 두 번째 flush 제거 가능 | 사이클 4 fix |
| QA | Nit | `docs/dev-reports/sp-08-4-2-partner-order-edit-put.md §6` | §6 Verification 표가 "IT 6 / Playwright 4" 원문 유지. §9.1 본문은 8/5 로 갱신되었으나 §6 표 수치 누락 | 사이클 4 fix |
| FE | 경고 | `SalesPartnerOrderDetailPage.tsx:121` | `handleConflictReload` useCallback deps 에 `query` 전체 객체 참조 → 매 렌더 재생성. `[query.refetch, syncFormFromData]` 로 좁히기 | 후속 슬라이스 백로그 |
| FE | 정보 | `clients/web/design-system/src/components/Input/Input.module.css` | Designer 사이클 2 P1 `:read-only` 시각 cue 미반영, 이 PR 범위 외 | 후속 슬라이스 백로그 |
| Designer | Nit | `SalesPartnerOrderDetailPage.tsx` line table | `key={`${line.modelCode}-${index}`}` 혼합 패턴, 서버 `lineId` 도입 또는 `key={index}` 단일화 검토 | 후속 슬라이스 백로그 |
| Designer | Nit | `tokens.css` | `--color-success-50/200/700` 토큰 미정의 — fallback hex 가 실제 색상 결정. DS 레벨 토큰 승격 별도 이슈 | 후속 슬라이스 백로그 |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 4 필요 |
| FE | APPROVE |
| Designer | APPROVE |
| QA | APPROVE (Nit non-blocker) |
| DevOps | PASS |

### TM 결정

- **종합: 사이클 4 필요** — BE P1 (orphanRemoval=true vs soft-delete 의미 충돌) + P2 (verifyVersion modifiedAt null 거짓 양성 409) blocker 2건. FE/Designer/QA/DevOps 4 agent 는 APPROVE/PASS.
- **사이클 4 fix 후보** (우선순위 순):
  1. (P1) `PartnerOrder.orphanRemoval=false` + Javadoc 에 soft-delete 전략 명시
  2. (P2) `verifyVersion` 에서 `modifiedAt==null` 시 `createdAt` fallback 또는 `requestUpdatedAt==null` 통과 정책 추가 + IT case 보강
  3. (BE Nit) `PartnerOrderIdResolver` catch 범위 `IllegalArgumentException` 으로 좁히기
  4. (BE Nit) `PartnerOrderUpdateService` 중복 `flush()` 제거
  5. (QA Nit) `docs/dev-reports/sp-08-4-2-partner-order-edit-put.md §6` Verification 표 IT 8 / Playwright 5 수치 갱신
- **후속 슬라이스 백로그**: FE-C1 (`handleConflictReload` deps 좁히기), FE-C2 / Designer Nit (`Input.module.css` `:read-only` 시각 cue — DS 레벨 작업), Designer Nit D-C2-2 (line key 안정성 — 서버 `lineId` 도입 검토), `--color-success-*` design-token 승격.

**tech-manager — 2026-05-17**
