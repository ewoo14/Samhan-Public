## Codex 5-agent 사이클 2 통합 리뷰 (head `d6364d4b`)

> tech-manager agent 가 Codex BE / FE / Designer / QA (후공정) / DevOps 5 agent 결과 종합.

### Claude 발견 평가 종합

| Claude 발견 | 우선순위 | Codex 평가 | 사유 |
|---|---|---|---|
| BE P2-1 `createFromEstimate` status/confirmedAt 의미 흐름 | Medium | VALID | `new PartnerOrder(...)` 재사용으로 `CONFIRMING` + `confirmedAt=now` 유입, 주석/도메인 의미 충돌 (history/API 확정 진행 중 처럼 표시). outbox 부작용은 `NOT_REQUIRED` 로 막혔으나 의미적 정합 깨짐 |
| BE P2-2 `X-User-Role` 상수 누락 | Low | VALID | `HttpHeaderConstants` `X-User-Id`/`X-User-Name` 만, role 은 `HeaderAuthenticationFilter` 하드코딩 |
| BE P2-3 `@SQLRestriction` + V6 partial unique 정책 점검 | Low | INVALID / non-blocking | active row 기준 중복 차단 + 서비스 주석 "동일 estimateId active 1건" 명시 → 정책 일치 |
| FE-C2-01 거래처 코드 편집 허용 | — | invalid as FE / keep as BE-policy note | BE `PartnerOrderUpdateRequest.partnerCode @NotBlank` + `updateHeader(partnerCode, ...)` 정합 |
| FE-C2-02 route id slash/hyphen path resolve 실패 | — | INVALID | `PartnerOrderIdResolver.findByIdentifier()` `toSlashOrderNo()` hyphen→slash 보정, list hyphen resolve 됨 |
| FE-C2-03 detail queryKey canonical orderNumber 미사용 | — | VALID info | slash/hyphen 표현 cache key 잔존, 직접 slash URL 시 중복 cache 가능 |
| D1 `--state-success #10B981` on `#D1FAE5` 대비비 | P1 | VALID | 약 2.2:1 — 4.5:1 미달. `.statusSent` `#065f46` fallback 권장 |
| D2 PNG 04 `PARTNER_ORDER_FROM_ESTIMATE_ALREADY_CONVERTED` 노출 | P1 | VALID | TSX 한국어 고정 문구 렌더, QA 스크립트가 코드 문자열 그림. 사용자-facing 증거 정책 위반 |
| D3 `historyRow --line-default` 토큰 출처 | — | INVALID | `tokens.css:141` 정의됨, 출처 명확 |
| QA2-P2-01 첫 `201 Created` body 미단언 | Medium | VALID | 첫 요청 status code 만, body/orderNumber/lines/status 미고정 |
| QA2-P2-02 dev-report §6 IT/PNG 수치 stale | Medium | VALID | `IT 9건`, `4 PNG` 잔존 — 실제 IT 11건, PNG 5장 |
| QA-Nit-01 `02-delete-success.png` soft delete 표기 | nit | INVALID | `삭제된 2026/05/17-1 미노출` 문구로 정책 정합 |
| QA-Nit-02 `resolveActorName` 공백 fallback Javadoc | nit | VALID nit | 동작상 문제 없으나 정책 명시 권장 |
| DevOps D-1 `FixtureEstimateClient` 운영 배포 위험 | P1 | VALID | `@Component` + `findById()` `Optional.empty()` 운영 NOT_FOUND. `@Profile`/property guard 필요 |
| DevOps D-2 `nextOrderNo` soft-deleted row 제외 | P1 | VALID | active partial unique 라 같은 표시 주문번호 재사용 가능 → 식별자 혼선 |
| DevOps D-3 `parseActorId` nil UUID fallback 일관성 | P2 | INVALID / 해소됨 | 신규+기존 컨트롤러 모두 `new UUID(0L, 0L)` fallback 일관 |

### Codex 자체 신규 발견

| 코드 | Agent | 우선순위 | 내용 |
|---|---|---|---|
| BE-C2-confirmedAt | BE | Medium (P2-1 동반) | P2-1 fix 시 `confirmedAt` 도 함께 정리 필요 |
| Codex-FE-C2-01 | FE | 정보 / mock coverage gap | `mock.ts` path id 미검증 후 항상 성공 응답. FE-C2-02 류 회귀 mock 가림. `decodeURIComponent(id)` 후 `2026/05/04-1` + `2026-05-04-1` alias 만 허용 권장 |
| C2-D1 | Designer | P2 | PNG 03 raw `CONFIRMING`/`NOT_REQUIRED` 노출 — "주문 상태: 확인 중 / 전표 발행: 불필요" 한국어 라벨 |
| QA2-P2-03 | QA | Medium | dev-report §2/§8 `system-partner-order-delete` 고정 표기 — 코드는 `resolveActorName(actorName)`, IT `deleted_by='영업담당자'` 기대. 문서 stale |

### Codex 사이클 1 자체 발견 추적

| Agent | 사이클 1 발견 | 사이클 2 상태 |
|---|---|---|
| BE | BE-1~4 | FIXED |
| BE | P1 outbox 미연결 | `SlipPublishStatus.NOT_REQUIRED` FIXED |
| FE | FE P1 5건 | FIXED |
| FE | route id 통일 후속 우려 | BE resolver 보정 — HOLD non-blocker |
| Designer | 9건 + PNG 03 P0 | FIXED |
| QA | Playwright 정적 / 409 reload | HOLD non-blocker |
| QA | FROM_ESTIMATE audit IT | FIXED |
| DevOps | reviewDecision "" | HOLD non-blocker |
| DevOps | Flyway V5 정합 | FIXED |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 3 필요 |
| FE | APPROVE (FE-C2-02 invalid, 신규 P1 없음) |
| Designer | REQUEST CHANGES (D1/D2 valid P1) |
| QA | 조건부 APPROVE |
| DevOps | APPROVE (D-3 해소, D-1/D-2 후속) |

### TM 결정

- **종합**: 사이클 2.5 fix 권고. 사용자 정책 N=3 사이클 한도 내 통합 fix commit 1회.
- **양쪽 합의 핵심 fix (사이클 2.5 통합 commit)**:
  - BE P2-1: `createFromEstimate` `status=DRAFT` + `confirmedAt=null` 정리 (Codex 동반 권고)
  - BE P2-2: `HttpHeaderConstants.X_USER_ROLE` 상수 추가 + 하드코딩 치환
  - Designer D1: `--state-success` fallback `#065f46` (4.5:1 보장)
  - Designer D2: PNG 04 한국어 에러 문구로 재생성 (코드 enum 노출 제거)
  - Codex Designer C2-D1: PNG 03 raw `CONFIRMING`/`NOT_REQUIRED` → "주문 상태: 확인 중 / 전표 발행: 불필요" 한국어 라벨
  - QA2-P2-01: 첫 요청 body/orderNumber/lines/status 단언 추가
  - QA2-P2-02: dev-report §6 IT 11건 + PNG 5장 갱신
  - Codex QA2-P2-03: dev-report §2/§8 `system-partner-order-delete` 표기 → `resolveActorName(actorName)` + `영업담당자` 일치 갱신
- **invalid 무시**:
  - BE P2-3 (active 정책 일치)
  - Claude FE-C2-02 (BE resolver 보정)
  - Claude QA-Nit-01 (PNG 정합)
  - Claude Designer D3 (`--line-default` 정의됨)
  - DevOps D-3 (fallback 일관성 해소)
- **후속 슬라이스**:
  - FE-C2-01 (BE contract 결정), FE-C2-03 (queryKey 통일), Codex FE mock coverage
  - DevOps D-1 (Phase 11), DevOps D-2 (식별자 정책)
  - QA-Nit-02 (Javadoc)

**tech-manager — 2026-05-17**
