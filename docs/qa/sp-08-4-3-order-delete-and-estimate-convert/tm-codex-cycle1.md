## Codex 5-agent 사이클 1 통합 리뷰 (head `97afca70`)

> tech-manager agent 가 Codex BE / FE / Designer / QA (후공정) / DevOps 5 agent 결과 종합.

### Claude 발견 평가 종합

| Claude 출처 | 우선순위 | Codex 평가 | 사유 |
|---|---|---|---|
| BE P1-1 actorName / deleted_by 분기 | P1 | valid | `softDeleteCascade(DELETE_ACTOR)` 고정값과 audit actorName 두 갈래 |
| BE P1-2 estimateId snapshot 불일치 | P1 | valid | path id 와 `snapshot.estimateId()` 동일성 미검증, 잘못된 client 시 다른 주문 생성 |
| BE P1-3 nextOrderNo race | P1 | valid | `findAllByOrderNoStartingWith` + max+1, DB unique 충돌 500 누설 위험 |
| BE P1-4 DeleteIT EstimateClient @MockBean | P1 → P2 | partially valid | 현재 fixture empty 라 즉시 장애 X, 컨벤션 준수 위해 P2 권장 |
| BE P2-5 idempotency_key full unique | P2 | **invalid** | V1 `ux_partner_orders_idempotency_active` 이미 partial unique, soft-delete 후 재변환 500 재현 X |
| BE P2-6 recomputeTotal 중복 | P2 | valid | `addLine()` 누적 + `recomputeTotal()` 재호출, 회귀 위험 |
| BE P2-7 FromEstimateIT DB 직접 단언 | P2 | valid | `source_estimate_id` / `due_date` / FROM_ESTIMATE audit DB 검증 부재 |
| BE Nit-1 / Nit-2 | Nit | valid | CALLER_*_HEADER controller 중복, import 순서 어긋남 |
| FE P1-1 / P1-2 danger variant | P1 | valid | `ButtonVariant.danger` 존재, 파괴 액션은 danger 정확 |
| FE P1-3 mock 404/422 부재 | P1 | valid | `mock.ts:3295` 무조건 success |
| FE P1-4 `orderNumber ?? ''` 빈 문구 | P1 | valid | `조회 중` fallback 일관 |
| FE P2-5 regex 경합 | P2 | **invalid** | list/detail/audit regex 각각 anchored, 실제 경합 X |
| FE P2-6 audit row key | P2 | valid-low | 같은 revision/field/timestamp 충돌 여지 |
| FE P2-7 / Nit-1 / Nit-2 | P2 / Nit | **over-engineering / invalid** | `query.data && canEdit` 중복은 동작 결함 X, `Modal` 자체 `role="dialog"` 있음, mock `fieldName` 은 `createAuditApi`가 `field` 로 정규화 |
| Designer P0~Nit 9건 | P0~Nit | **9건 모두 valid** | PNG 한글 깨짐 + API 디버그 노출 + danger variant + 조사 처리 + UUID 노출 + 토큰 혼용 모두 재현 |
| QA P1-01 PNG 03 success 충돌 | P1 | valid | 파일명 success / 내용 409 Conflict |
| QA P1-02 CANCELED 422 IT 누락 | P1 | valid | 코드 `DRAFT / CONFIRMING` 만 허용, CANCELED 차단 회귀 테스트 없음 |
| QA P2-01 첫 요청 body 미검증 | P2 | valid | `testFromEstimateAlreadyConvertedReturns409` 첫 요청 source/라인/상태 미검증 |
| QA P2-02 TOCTOU dead code | P2 | partially valid | "완전 dead code" 표현 약함, snapshot.estimateId 불일치 시 의미 생김, P2 유지 |
| QA Nit-01 / Nit-02 | Nit | valid | PNG 한글 깨짐, findById assertion 메시지 약함 |
| DevOps APPROVE | — | 동의 | diff/마이그레이션/빈 등록/CI matrix/dependency 추가 수정 X |

### Codex 자체 신규 발견 (사이클 1)

| 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|
| BE | **P1 신규** | `PartnerOrderFromEstimateService` | `createFromEstimate()` 가 `slipPublishStatus=PENDING_RETRY` 주문 생성하나 `SlipPublishOutbox` row 미생성 + slip-service 미호출 → 영구 pending. confirm 흐름처럼 outbox/발행 경로 연결 또는 별도 "전표 발행 전" 상태 도입 필요 |
| FE | **P1 신규** | `SalesPartnerOrderDetailPage.tsx:109` / `sales.ts:397` | `orderNumber` slash (`2026/05/04-1`) `encodeURIComponent` → `%2F` path. 서버/프록시 encoded slash 차단 시 404/400. mock 가림. 삭제/수정/audit path 인자 route `id` (hyphen) 통일 필요 |
| Designer | **P0 신규** | `03-from-estimate-success.png` 파일명 vs 내용 | 파일명 success / 내용 `409 Conflict` + `PARTNER_ORDER_FROM_ESTIMATE_ALREADY_CONVERTED`. 성공 케이스 QA 증거 누락 (Claude QA P1-01 동일, Designer 관점 P0) |
| QA | P2 신규 | `PartnerOrderFromEstimateIT` | FROM_ESTIMATE audit IT 검증 누락 — 서비스 `new ChangeEntry("FROM_ESTIMATE", null, snapshot.estimateNumber())` 호출, DELETE audit IT 와 symmetry 없음 |

### 사용자 명시 확인 (2026-05-17)

- "스크린샷 한글이 모두 깨짐" — Designer P0-D-01 사용자 직접 확인. Codex 도 valid 동의 (제목/본문/버튼 텍스트 `??`, 한자, 깨진 조합 렌더링). 스크립트는 `Malgun Gothic` 지정하나 실제 fallback 실패 또는 PowerShell UTF-16 트랩 (`feedback_powershell_utf8_writes.md`) 추정. 사이클 1.5 fix 최우선 — Playwright `locale: 'ko-KR'` + Pretendard preload 또는 PowerShell `-Encoding utf8` 강제.

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (Claude P1 4 + Codex 신규 P1 1 = P1 5) |
| FE | 사이클 2 필요 (Claude P1 4 + Codex 신규 P1 1 = P1 5) |
| Designer | 사이클 2 필요 (P0 3 + Codex P0 신규 1 = P0 4, P1 3) |
| QA | 사이클 2 필요 (P1 2 + Codex P2 신규 1) |
| DevOps | APPROVE |

### TM 결정

- **종합: 사이클 1.5 대규모 일괄 fix** — Claude TM 통합과 동일 결론. **Codex 신규 4건 추가** (총 결함 ~37건):
  1. P0: PNG 03 파일명/내용 충돌 (Claude QA P1-01 와 일괄, Designer P0 #3 재생성 시 자연 해소)
  2. P1: from-estimate outbox 미연결 (slipPublishStatus 운영 정합성)
  3. P1: orderNumber `%2F` URL 인코딩 (route id 통일)
  4. P2: FROM_ESTIMATE audit IT (BE-P2-7 와 일괄)
- **invalid 무시**: BE P2-5 idempotency_key (V1 이미 partial), FE P2-5/P2-7/Nit-1/Nit-2 (over-engineering)
- **사이클 1.5 fix 후보** (Claude TM + Codex 추가 통합):
  - **P0 PNG 재생성** (한글 + 실 UI + 파일명 정합)
  - BE P1: actor 통일 + estimateId 제거 + nextOrderNo race + DeleteIT @MockBean + **outbox 연결** + path id 통일 (FE 와 정합)
  - FE P1: variant danger 2건 + mock 404/422 + Modal fallback + **route id (hyphen) 통일**
  - Designer P1: 조사 처리, 강조
  - QA P1: PNG 03 교체 + CANCELED IT + **FROM_ESTIMATE audit IT 신규**
  - P2/Nit 묶음: 유효 항목만
- **DevOps**: APPROVE 유지

**tech-manager — 2026-05-17**
