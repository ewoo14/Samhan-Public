# SP-D4 QA Cycle 1 리뷰 — claude-qa

> 작성일: 2026-05-18
> 검토자: QA (Claude)
> PR: #244 `feat/sp-d4-remaining-pages-permission-migration`
> HEAD: `6d141002`
> 자가 산출물 기준 — 객관 발견 + 보강 의무

---

## 종합 판정

**CONDITIONAL APPROVE (cycle 2 fix 필요)**

| 카테고리 | 판정 | 상세 |
|---|---|---|
| Playwright 14 case 설계 | PASS (조건부) | T05/T14 URL 불일치 1건, T06/T09 시나리오 URL 불일치 1건 |
| IT 7신규 28 case 패턴 | FAIL | ArologisAdminPermissionIT 외부 client @MockBean 4종 누락 |
| domain integrity SQL | PASS | 4-way 정합 SQL 적절 |
| mock.ts ↔ V10 seed 정합 | FAIL | MANAGER admin.users view 불일치 (mock TRUE vs V10 FALSE) |
| 사이드바 스크린샷 | PASS | 7역할 PNG 존재 확인 |
| 회귀 가드 | PASS | false green 0건, SP-D3 cycle 3 X-User-Role 헤더 명시 |
| 시나리오 markdown | PASS | 한국어 본문, 단계 명료 |

---

## 결함 목록

### D1 (CRITICAL) — ArologisAdminPermissionIT @MockBean 외부 client 4종 누락

**위치**: `services/arologis-service/src/test/java/.../it/ArologisAdminPermissionIT.java`

**현상**:
ArologisAdminPermissionIT 선언부에 `@MockBean DynamicPermissionClient` 1개만 등록되어 있다.
arologis-service 의 실제 외부 client는 NotificationClient / PartnerClient / SlipClient / SlipServiceClient 4종이 추가로 존재한다 (`services/arologis-service/src/main/java/.../client/` 확인).

기존 SP-D3 에서 작성된 ArologisDynamicPermissionIT 는 이 4종을 모두 @MockBean 처리하였다. SP-D4 신규 ArologisAdminPermissionIT 는 DynamicPermissionClient 만 격리하고 나머지를 누락하였다.

**영향**: SpringBootTest 컨텍스트 로드 시 Eureka/실제 endpoint 없이 4종 client 초기화 실패 → ApplicationContext 로드 오류 또는 500 반환 (feedback_it_mockbean_external_clients.md 트랩).

**수정 필요**:
```java
@MockBean
private NotificationClient notificationClient;

@MockBean
private PartnerClient partnerClient;

@MockBean
private SlipClient slipClient;

@MockBean
private SlipServiceClient slipServiceClient;
```
ArologisAdminPermissionIT 클래스 내 위 4종 추가 필요.

---

### D2 (HIGH) — mock.ts MANAGER admin.users view 불일치

**위치**: `clients/desktop/src/renderer/api/mock.ts` 라인 5659

**현상**:
mock.ts SP_D1_DEFAULT_VIEW.MANAGER 배열에 `'admin.users'` 가 포함되어 있다 (view=true로 취급됨).

V10 seed (V10__sp_d4_remaining_domains_page_permissions.sql 라인 179) 에서는:
```sql
('MANAGER', 'admin.users', FALSE, FALSE, ...)
```
MANAGER admin.users 는 view=FALSE / edit=FALSE 이다.

SP_D4_PERMISSION_MATRIX (spec 파일 라인 167) 에도 MANAGER admin.users = `{ view: false, edit: false }` 로 정확히 명시되어 있다.

**영향**: mock.ts 기반 Playwright 테스트에서 MANAGER 가 admin.users 페이지에 접근 가능한 것으로 잘못 mock 됨. 사이드바 MANAGER 스크린샷에 계정 관리 메뉴가 노출될 수 있다.

**수정 필요**: mock.ts SP_D1_DEFAULT_VIEW.MANAGER 배열에서 `'admin.users'` 제거.

---

### D3 (HIGH) — T05/T14 시나리오 URL vs 실제 라우트 불일치

**위치**: `sp-d4-remaining-pages-permission-migration.spec.ts`

**T05 불일치**:
- 시나리오 문서(`sp-d4-scenarios.md`) T05 단계에 "/#/admin/users?mockRole=SALES 직접 진입" 으로 기술되어 있다.
- spec 파일 라인 348에 `ADMIN_USERS_SALES_URL = /#/admin/permission-matrix?mockRole=SALES` 로 실제 라우트 재매핑이 주석 처리되었고, T05 test.step 라인 688~727에서도 `/admin/permission-matrix` 를 사용한다.
- 시나리오 문서와 spec 실행 URL이 `/admin/users` vs `/admin/permission-matrix` 로 불일치한다.

**T14 불일치**:
- 시나리오 문서 T14 단계에 "/#/inventory/audit?mockRole=SALES 직접 진입" 으로 기술되어 있다.
- spec 파일 라인 354에 `INVENTORY_AUDIT_SALES_URL = /#/warehouse/audit?mockRole=SALES` 이며 T14 test.step 라인 1374에서 `/warehouse/audit` 을 사용한다.
- 시나리오 문서와 spec 실행 URL이 `/inventory/audit` vs `/warehouse/audit` 로 불일치한다.

**영향**: QA 시나리오 문서를 기준으로 수동 검증 시 실제 URL과 다른 경로를 검증하게 된다.

**수정 필요**: 시나리오 문서 T05/T14 단계의 URL을 spec 실행 URL 기준으로 정정.
- T05: `/#/admin/users` → `/#/admin/permission-matrix`
- T14: `/#/inventory/audit` → `/#/warehouse/audit`

---

### D4 (MEDIUM) — T06/T09 시나리오 URL vs spec URL 불일치

**위치**: `sp-d4-remaining-pages-permission-migration.spec.ts`

**T06 불일치**:
- 시나리오 문서 T06 단계: "/#/inventory/warehouses?mockRole=WAREHOUSE 진입"
- spec 파일 라인 350에 `INVENTORY_WAREHOUSES_WAREHOUSE_URL = /#/warehouses?mockRole=WAREHOUSE` (inventory/ 없음)
- routes/index.tsx 확인 결과 실제 라우트 path 는 `/warehouses` 이다.

**T09 불일치**:
- 시나리오 문서 T09 단계: "/#/inventory/warehouses?mockRole=DISPATCH 직접 진입"
- spec 파일 라인 351에 `INVENTORY_WAREHOUSES_DISPATCH_URL = /#/warehouses?mockRole=DISPATCH`

**영향**: 시나리오 문서 기준 수동 검증 시 존재하지 않는 `/inventory/warehouses` 경로를 검증한다. 실제 라우트는 `/warehouses` 이므로 시나리오 문서 수정 필요.

**수정 필요**: 시나리오 문서 T06/T09 URL을 `/#/warehouses?mockRole=...` 로 정정.

---

### D5 (MEDIUM) — PartnerAdminPermissionIT it-cross-check 가이드 미준수 (C5 케이스 누락)

**위치**: `services/partner-service/src/test/java/.../it/PartnerAdminPermissionIT.java`

**현상**:
it-cross-check.md 가이드(라인 116~119)에서 PartnerAdminPermissionIT 는 5개 케이스가 요구된다:
```
C1: MANAGER, partners.list canView=true → GET /admin/partners 200 OK
C2: DISPATCH, partners.list canView=false → GET /admin/partners 403
C3: ACCOUNTANT, partners.list canEdit=false → POST /admin/partners 403
C4: MASTER, partners.block canView=true → GET /admin/partners/block 200 OK
C5: SALES, partners.block canView=false → GET /admin/partners/block 403
```

실제 구현은 C1(SALES canView=true) / C2(SALES canView=false) / C3(MASTER canEdit=true) / C4(SALES canEdit=false) 4케이스다.

partners.block 관련 C4/C5 케이스가 아예 없다. 주석에도 파트너 차단 관련 검증은 존재하지 않는다.

**영향**: partners.block PageCode 의 BE 레벨 PermissionGuard 동작이 IT 로 검증되지 않는다.

**수정 필요**: PartnerAdminPermissionIT 에 partners.block canView deny case (C4 또는 별도 test) 추가.

---

### D6 (MINOR) — domain-integrity-check.md SQL 7번 쿼리 SP-D1~D3 PageCode 누락

**위치**: `docs/qa/sp-d4-remaining-pages-permission-migration/domain-integrity-check.md` 라인 163~173

**현상**:
SQL 7번 쿼리는 "알 수 없는 pageCode" 검출 목적이나 IN 절에 SP-D4 22개 PageCode만 포함되고 SP-D1~D3 기존 PageCode 가 추가되지 않았다. 주석에도 "SP-D1~D3 기존 PageCode 도 추가 필요" 라고 명시되어 있으나 실제로 추가되지 않았다.

**영향**: SQL 실행 시 SP-D1~D3 pageCode row 가 WHERE 절에서 제외되지 않아 false positive 0건 검증에 실패할 수 있다.

**수정 필요**: SQL 7번 NOT IN 절에 SP-D1~D3 기존 PageCode (V7/V8/V9 seed 기준) 전체 추가.

---

## 적합 항목 (PASS)

### P1 — IT 6개 서비스 @MockBean + lenient stub + X-User-Role 패턴 준수

EstimatePermissionIT / PartnerOrderListPermissionIT / WarehousePermissionIT / EmployeePermissionIT / ProductPermissionIT 5개 서비스 IT 모두:
- `@MockBean DynamicPermissionClient` 명시
- `@BeforeEach void setupLenientStubs()` lenient().when(...).thenReturn(true) 양쪽 등록
- 모든 mockMvc.perform() 호출에 `.header("X-User-Role", ...)` 명시
- deny override: `Mockito.when(...).thenReturn(false)` 패턴 C2/C4 케이스에 존재

SP-D3 cycle 3 회귀 트랩 0건. ArologisAdminPermissionIT 도 DynamicPermissionClient 패턴 자체는 준수 (외부 client 추가 누락은 D1 에 별도 기재).

---

### P2 — Playwright T01~T14 false green 패턴 0건

회귀 가드 테스트(라인 1450~1493)가 spec 파일 자체를 읽어 `|| true` / `test.skip(!ok)` / `page.setContent(` 패턴을 검출한다. spec 코드 검토 결과 해당 패턴 0건 확인.

PLAYWRIGHT_SKIP_UI=1 로 전체 skip 경로는 존재하나, 이는 CI skip 에 사용되는 정상 패턴이다.

---

### P3 — SP_D4_PERMISSION_MATRIX 22 PageCode × 7 역할 매트릭스 V10 seed 정합

spec 파일 SP_D4_PERMISSION_MATRIX (라인 128~297) 와 V10 seed SQL 을 대조 검증:
- MASTER: 22개 전체 TRUE/TRUE - 정합
- MANAGER admin.users: spec matrix FALSE/FALSE / V10 seed FALSE/FALSE - 정합 (mock.ts만 불일치 - D2)
- DISPATCH arologis.admin: spec matrix TRUE/TRUE / V10 seed TRUE/TRUE - 정합
- INVENTORY arologis.admin: spec matrix FALSE/FALSE / V10 seed FALSE/FALSE - 정합
- SALES inventory.audit: spec matrix FALSE/FALSE / V10 seed FALSE/FALSE - 정합

spec 파일 PERMISSION_MATRIX 와 V10 SQL 의 154 row 값이 전 역할 × 22 코드 범위에서 일치한다.

---

### P4 — 사이드바 7 역할 스크린샷 존재 확인

`docs/qa/sp-d4-remaining-pages-permission-migration/screenshots/` 에 sidebar-{master,manager,accountant,sales,warehouse,dispatch,inventory}.png 7개 파일 존재 확인.
`clients/desktop/playwright/sp-d4-remaining-pages-permission-migration/screenshots/` 에 T01~T14 PNG 14개 파일 존재 확인.

---

### P5 — domain-integrity-check.md SQL 1~6번 적절

SQL 1번(총 154 row 확인) / 3번(MASTER 전체 TRUE) / 4번(admin.users MASTER 전용) / 5번(arologis.admin DISPATCH 포함) / 6번(Idempotency 2회 재실행) 쿼리 모두 목적 적합 및 기대값 명시 완료.

---

### P6 — 시나리오 markdown 구조 적절

sp-d4-scenarios.md: T01~T14 각 시나리오 "사용자 / 단계 / 기대 / 스크린샷" 4-섹션 구조 한국어 작성 완료. 검증 기대 조건 명료. revoke 시나리오(T13) / URL redirect(T14) 엣지케이스 포함.

---

### P7 — EstimatePermissionIT 외부 client @MockBean 전체 확인

EstimatePermissionIT 는 slip-service 의 외부 client 7종(InventoryClient / ProductClient / PartnerInternalClient / PartnerBlockClient / NotificationClient / NotificationChatRoomClient / ArologisDispatchClient) 모두 @MockBean 처리 확인. it-cross-check.md 가이드 기재 목록과 일치.

---

## 회귀 가드 결과

| SP-D2 P04 트랩 | PASS (6/7 서비스) + D1(arologis 외부 client 누락) |
| SP-D3 cycle 3 X-User-Role 트랩 | PASS (전 IT @RequestHeader 명시) |
| false green 패턴 | PASS (|| true / skip(!ok) / setContent 0건) |

---

## Cycle 2 필수 수정 항목

| 우선순위 | 파일 | 수정 내용 |
|---|---|---|
| CRITICAL | ArologisAdminPermissionIT.java | NotificationClient / PartnerClient / SlipClient / SlipServiceClient @MockBean 4종 추가 |
| HIGH | mock.ts | SP_D1_DEFAULT_VIEW.MANAGER 에서 `'admin.users'` 제거 |
| HIGH | sp-d4-scenarios.md | T05 URL `/admin/users` → `/admin/permission-matrix`, T14 URL `/inventory/audit` → `/warehouse/audit` 정정 |
| MEDIUM | sp-d4-scenarios.md | T06/T09 URL `/inventory/warehouses` → `/warehouses` 정정 |
| MEDIUM | PartnerAdminPermissionIT.java | partners.block canView deny case (SALES) 추가 |
| MINOR | domain-integrity-check.md | SQL 7번 NOT IN 절에 SP-D1~D3 기존 PageCode 목록 보완 |
