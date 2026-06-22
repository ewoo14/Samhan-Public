# 슬4d — 입고전표 설정기반 결재란 렌더 + 결재 서명자 이름 자동채움 Implementation Plan

> **For agentic workers:** 본 plan 은 **Codex 구현 → Opus/Codex 듀얼리뷰 0수렴** 워크플로우([[temp-multimodel-workflow]])로 실행한다. Codex 가 파일만 수정(git/gradle 금지), Claude(PM) 가 빌드·테스트·커밋·probe·라이브 QA 대행. 각 Task = 독립 테스트 가능 단위. 체크박스(`- [ ]`)로 추적.

**Goal:** 입고전표(SLIP_INBOUND) 정식 인쇄(PurchaseSlipPrintPage)의 빈 수기 검수란을 **설정기반 결재란**(작성자/입고자/검수자 + 추가단계)으로 전환하고, slip-service 가 결재 서명자 이름을 resolve 하여 **입고·판매 결재란 모두 서명자 이름을 자동표시**한다.

**Architecture:** 슬3에서 만든 generic read 엔드포인트(`GET /auth/approval-line-configs/{docType}/structure`)·V63 시드·DispatchDocument 결재란 골격을 재사용. (1) slip-service GET 상세에서 `dispatcherUserId`·`inspectorUserId`·`acceptedBy` 를 `ownerFullName` 과 동일 `UserInternalClient` 단건 GET 으로 resolve → `SlipDetailResponse` flat additive 필드. (2) FE 는 결재란 셀/서명자 매핑을 `print/approvalRoleCells.tsx` 공유 모듈로 추출(slipType 분기) → DispatchDocument(OUTBOUND)·PurchaseSlipPrintPage(INBOUND) 양쪽 사용. (3) 고아 InboundView 폐기.

**Tech Stack:** Spring Boot 3.3 / Java 17 / JUnit5 + Testcontainers (slip-service) · React 18 + TypeScript + Vitest + Playwright (clients/desktop).

## Global Constraints

- **BaseEntity 7 audit + Soft Delete only**; 도메인 메서드 chain(직접 set 금지); 한국어 Javadoc 의무.
- **Flyway 신규 마이그레이션 없음** — BE 변경은 코드(응답 DTO + resolve)뿐. 적용된 V*.sql 불변([[applied-migration-immutable]]).
- **additive·nullable** 필드만 — OUTBOUND/기존 응답 계약 무회귀(graceful fallback null).
- **UUID 사용자 비공개**([[uuid-no-user-visibility]]) — 결재란은 이름만, userId 노출 금지.
- **FE green = typecheck + lint + vitest 전부**([[desktop-typecheck-command]]). 라이브 QA = 실 게이트웨이 :8080·실 시드·mock off([[no-fake-data-ever]]).
- **실HTTP 회귀**([[restclient-contract-test-false-green]]) — UserInternalClient @MockBean 우회 금지, 신규 resolve 는 실 client 경로 IT.
- 사용자 노출 명칭 "전표"(슬립 금지 [[jeonpyo-not-slip]]). 기술 키 SLIP_INBOUND·SLIP_OUTBOUND 불변.

---

## File Structure

**BE (slip-service)**
- Modify `services/slip-service/.../web/dto/SlipDetailResponse.java` — flat 필드 `dispatcherFullName`/`inspectorFullName`/`acceptedByFullName` 추가 + 5-arg factory.
- Modify `services/slip-service/.../service/SlipService.java:1240-1243` — `resolveDetailResponse` 가 3 userId 이름 추가 resolve.
- Test `services/slip-service/.../it/SlipDetailNameResolveIT.java` (신규 또는 기존 상세 IT 확장) — INBOUND acceptedByFullName + OUTBOUND dispatcher/inspectorFullName 채움, mutation graceful null.

**FE (clients/desktop)**
- Create `src/renderer/print/approvalRoleCells.tsx` — `RoleCell`, `roleSignerName(slip, role, slipType)`, `fallbackRoles(slipType)`, `ApprovalRoleCells` 공유 presentational.
- Modify `src/renderer/print/DispatchDocument.tsx` — 결재란을 공유 모듈로 전환(OUTBOUND), 신규 flat 필드 사용.
- Modify `src/renderer/print/PurchaseSlipPrintPage.tsx` — 빈 검수란 제거 → 설정기반 결재란(SLIP_INBOUND structure fetch + ApprovalRoleCells).
- Modify `src/renderer/api/slip.ts` — SlipDetail 에 flat `dispatcherFullName`/`inspectorFullName`/`acceptedByFullName` 추가.
- Modify `src/renderer/styles/global.css` — 공유 결재란 클래스(`approval-role-*`) + 매입 양식 배치.
- Delete `src/renderer/print/InboundView.tsx`; Modify `src/renderer/routes/index.tsx:554` (라우트 제거) + 잔존 참조 sweep.
- Test `src/renderer/print/__tests__/approvalRoleCells.test.ts` (신규) + 기존 `DispatchDocument.test.ts`·`PrintRendererAppContract.test.ts` 갱신.
- Modify `src/renderer/api/mock.ts` — SLIP_INBOUND structure mock + 상세 응답 flat 필드.

---

## Task 1: BE — 결재 서명자 이름 resolve + SlipDetailResponse flat 필드

**Files:**
- Modify: `services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/SlipDetailResponse.java`
- Modify: `services/slip-service/src/main/java/com/samhanair/logis/slip/service/SlipService.java` (`resolveDetailResponse` ~1240, `resolveOwnerFullName` ~참조)
- Test: `services/slip-service/src/test/java/com/samhanair/logis/slip/it/SlipDetailNameResolveIT.java` (신규)

**Interfaces:**
- Produces (응답 JSON 신규 키): `dispatcherFullName: String|null`, `inspectorFullName: String|null`, `acceptedByFullName: String|null` (FE Task 4 가 소비).
- Consumes: 기존 `UserInternalClient.fetchFullName(String userId): String|null` (graceful null) — `resolveOwnerFullName` 이 이미 사용하는 단건 GET.

- [ ] **Step 1: 실패 IT 작성** — INBOUND 전표 GET 상세에서 acceptedByFullName 채움 + OUTBOUND dispatcher/inspectorFullName 채움. `UserInternalClient` 는 실 빈 + MockRestServiceServer 로 `/internal/users/{id}` 스텁(실HTTP, @MockBean 금지). mutation 응답은 세 필드 null.

```java
// SlipDetailNameResolveIT (Testcontainers Postgres + MockRestServiceServer)
// given: INBOUND 전표 acceptedBy=U_ACCEPT, inspectorUserId=U_INSPECT 저장
//        user-service GET /internal/users/U_ACCEPT → {fullName:"입고담당"} ,  U_INSPECT → {fullName:"검수담당"}
// when : GET /slips/{id}
// then : body.acceptedByFullName == "입고담당"  &&  body.inspectorFullName == "검수담당"
//        body.dispatcherFullName == null (INBOUND 미사용)
// given2: OUTBOUND 전표 dispatcherUserId=U_DISP, inspectorUserId=U_INSPECT
// then2 : body.dispatcherFullName=="출고담당" && body.inspectorFullName=="검수담당"
// given3: mutation(POST accept 등) 응답 → 세 *FullName 모두 null (graceful)
```

- [ ] **Step 2: IT 실패 확인** — `acceptedByFullName` 등 record component 미존재 → 컴파일 실패(Expected).
- [ ] **Step 3: SlipDetailResponse 필드 추가** — record 에 `String dispatcherFullName, String inspectorFullName, String acceptedByFullName` 추가(`ownerFullName` 인접, 한국어 Javadoc). 기존 `from(slip)`·`from(slip, ownerFullName)` 는 신규 3필드 null 위임. 신규 `from(Slip slip, String ownerFullName, String dispatcherFullName, String inspectorFullName, String acceptedByFullName)` 추가.
- [ ] **Step 4: SlipService.resolveDetailResponse 확장** — `resolveOwnerFullName` 패턴으로 3 userId 각 단건 resolve(null-safe: userId null 이면 호출 skip), 5-arg factory 호출.

```java
String ownerFullName    = resolveOwnerFullName(slip.getCreatedBy());
String dispatcherName   = resolveUserFullName(slip.getDispatcherUserId());
String inspectorName    = resolveUserFullName(slip.getInspectorUserId());
String acceptedByName   = resolveUserFullName(slip.getAcceptedBy());
return SlipDetailResponse.from(slip, ownerFullName, dispatcherName, inspectorName, acceptedByName);
// resolveUserFullName = resolveOwnerFullName 일반화(userId null/blank → null, client 실패 → null)
```

- [ ] **Step 5: IT 통과 확인** (CI Linux fresh — Windows 로컬 Testcontainers skip 가능 [[testcontainers-windows-docker]], CI 결과 의무 확인 [[changed-module-full-test-before-push]]).
- [ ] **Step 6: 커밋** — `feat(slip): 결재 서명자(출고/검수/입고) 이름 resolve + SlipDetailResponse flat 필드` (PM 대행).

---

## Task 2: FE — 공유 결재란 모듈 `approvalRoleCells.tsx`

**Files:**
- Create: `clients/desktop/src/renderer/print/approvalRoleCells.tsx`
- Test: `clients/desktop/src/renderer/print/__tests__/approvalRoleCells.test.ts`

**Interfaces:**
- Produces: `roleSignerName(slip: SlipDetail, role: ApprovalLineStructure, slipType: 'OUTBOUND'|'INBOUND'): string | null`; `fallbackRoles(slipType): ApprovalLineStructure[]`; `RoleCell` 컴포넌트; `ApprovalRoleCells({ slip, roles, slipType })` (sequence 정렬 + 매핑 셀 렌더).
- Consumes: `SlipDetail`(Task 4 flat 필드), `ApprovalLineStructure`(approvalLineConfigApi).

- [ ] **Step 1: 실패 단위테스트** — `roleSignerName` 5분기 × slipType.

```ts
// CREATOR → ownerFullName
expect(roleSignerName({ownerFullName:'홍길동'} as any, {stepType:'CREATOR',actionKey:null} as any, 'INBOUND')).toBe('홍길동')
// INBOUND_RECEIVE → acceptedByFullName
expect(roleSignerName({acceptedByFullName:'김입고'} as any, {stepType:'GROUP',actionKey:'INBOUND_RECEIVE'} as any,'INBOUND')).toBe('김입고')
// INBOUND_INSPECT → inspectorFullName
expect(roleSignerName({inspectorFullName:'이검수'} as any, {actionKey:'INBOUND_INSPECT'} as any,'INBOUND')).toBe('이검수')
// OUTBOUND_DISPATCH → dispatcherFullName
expect(roleSignerName({dispatcherFullName:'박출고'} as any, {actionKey:'OUTBOUND_DISPATCH'} as any,'OUTBOUND')).toBe('박출고')
// 추가단계 actionKey=null & not CREATOR → null(빈칸)
expect(roleSignerName({} as any, {stepType:'GROUP',actionKey:null} as any,'INBOUND')).toBeNull()
// fallbackRoles('INBOUND') = 작성자/입고자/검수자
expect(fallbackRoles('INBOUND').map(r=>r.label)).toEqual(['작성자','입고자','검수자'])
expect(fallbackRoles('OUTBOUND').map(r=>r.label)).toEqual(['작성자','출고자','검수자'])
```

- [ ] **Step 2: 실패 확인** (`npm run test -- approvalRoleCells`, desktop cwd) → 모듈 미존재 FAIL.
- [ ] **Step 3: 구현** — `RoleCell`(DispatchDocument 에서 이동, 클래스 `approval-role-cell`/`approval-role-label`/`approval-role-value`/`approval-role-stamp` 중립 명명), `roleSignerName`, `fallbackRoles`, `ApprovalRoleCells`(roles ?? fallbackRoles 후 sequence 정렬 → RoleCell 매핑, value=roleSignerName).
- [ ] **Step 4: 통과 확인.**
- [ ] **Step 5: 커밋** — `feat(desktop): 결재란 공유 모듈 approvalRoleCells (slipType 서명자 매핑)`.

---

## Task 3: FE — DispatchDocument 공유 모듈 전환 (OUTBOUND) + 슬3 계약테스트 갱신

**Files:**
- Modify: `clients/desktop/src/renderer/print/DispatchDocument.tsx`
- Modify: `clients/desktop/src/renderer/print/DispatchDocument.test.ts`, `clients/desktop/src/renderer/print/PrintRendererAppContract.test.ts`
- Modify: `clients/desktop/src/renderer/styles/global.css` (dispatch-role-* → approval-role-* 정합 or alias)

**Interfaces:**
- Consumes: Task 2 `ApprovalRoleCells`, `fallbackRoles`. Task 4 flat 필드(`slip.dispatcherFullName`/`inspectorFullName`).

- [ ] **Step 1: 계약테스트 갱신(실패)** — DispatchDocument 가 OUTBOUND 결재란에 dispatcher/inspector 이름을 렌더하도록 기대 변경(기존 공백 단언 → 이름 단언). `roles` 미전달 시 fallbackRoles('OUTBOUND').
- [ ] **Step 2: 실패 확인** (구 `slip.dispatcher?.fullName` 경로라 이름 공백 → FAIL).
- [ ] **Step 3: DispatchDocument 리팩터** — 인라인 `RoleCell`/`roleValue`/`fallbackRoles` 제거, 중앙 결재 그리드를 `<ApprovalRoleCells slip={slip} roles={roles} slipType="OUTBOUND" />` 로 교체(담당부서·결제예정일 wrapper 셀 유지). 신규 flat 필드 사용은 Task 2 모듈 경유.
- [ ] **Step 4: 통과 확인** + global.css 클래스 정합(결재란 시각 동일 유지 — 셀 폭/서명칸).
- [ ] **Step 5: 커밋** — `refactor(desktop): DispatchDocument 결재란 공유 모듈화 + 출고/검수자 이름 표시`.

---

## Task 4: FE — SlipDetail flat 필드 + PurchaseSlipPrintPage 결재란 전환

**Files:**
- Modify: `clients/desktop/src/renderer/api/slip.ts` (SlipDetail 인터페이스)
- Modify: `clients/desktop/src/renderer/print/PurchaseSlipPrintPage.tsx`
- Modify: `clients/desktop/src/renderer/styles/global.css`
- Modify: `clients/desktop/src/renderer/api/mock.ts` (SLIP_INBOUND structure + 상세 flat 필드)

**Interfaces:**
- Consumes: Task 2 `ApprovalRoleCells`, `fetchApprovalLineStructure('SLIP_INBOUND')`, Task 1 응답 flat 필드.

- [ ] **Step 1: SlipDetail 필드 추가** — `dispatcherFullName?: string | null`, `inspectorFullName?: string | null`, `acceptedByFullName?: string | null` (한국어 주석, nested `dispatcher`/`inspector` 타입은 미사용이므로 유지·미변경).
- [ ] **Step 2: PurchaseSlipPrintPage 실패 테스트/QA 기대** — 빈 검수란 제거, SLIP_INBOUND structure useQuery 추가, 결재란 섹션 렌더. (vitest 가능 범위: structure mock 주입 시 작성자/입고자/검수자 셀 + 추가단계 빈칸; 페치 실패 시 fallbackRoles('INBOUND').)
- [ ] **Step 3: 구현** — `검수란` section(`PurchaseSlipPrintPage.tsx:231-252`) 제거 → `<section className="purchase-print-approval"><div className="purchase-print-approval-title">결 재 란</div><ApprovalRoleCells slip={slip} roles={structureQuery.data ?? null} slipType="INBOUND" /></section>`. structure useQuery(`['approval-line-structure','SLIP_INBOUND']`, `fetchApprovalLineStructure('SLIP_INBOUND')`). 비고는 기존 footer(`:255-261`)로 충분 → 중복 없음. global.css `purchase-print-approval*` 추가(결재란 그리드).
- [ ] **Step 4: 통과 확인** (vitest + typecheck + lint).
- [ ] **Step 5: 커밋** — `feat(desktop): 매입전표 인쇄 검수란→설정기반 결재란 전환(SLIP_INBOUND)`.

---

## Task 5: FE — 고아 InboundView 폐기

**Files:**
- Delete: `clients/desktop/src/renderer/print/InboundView.tsx`
- Modify: `clients/desktop/src/renderer/routes/index.tsx` (`:554` 라우트 + import 제거, 주석 정리)
- Sweep: `InboundView`/`/print/inbound` 잔존 참조(playwright·테스트·문서) grep 제거/전환.

- [ ] **Step 1: 참조 grep** — `grep -rn "InboundView\|print/inbound" clients/desktop` 로 전 참조 인벤토리(라우트·import·playwright·테스트).
- [ ] **Step 2: 제거** — InboundView.tsx 삭제, routes/index.tsx 라우트·import·관련 주석 제거. playwright 잔존 케이스 제거(있으면).
- [ ] **Step 3: typecheck+lint+vitest 통과 확인** (미사용 import·라우트 깨짐 0).
- [ ] **Step 4: 커밋** — `chore(desktop): 고아 InboundView(/print/inbound) 폐기(매입전표 인쇄로 일원화)`.

---

## Task 6: 통합 검증 + mock 동기화 + 라이브 QA

- [ ] **Step 1: mock 동기화** — `mock.ts` SLIP_INBOUND structure 핸들러(슬3 SLIP_OUTBOUND 미러) + 입고 상세 응답 flat 필드(acceptedByFullName 등). in-process mock 3원칙([[inprocess-mock-principles]]).
- [ ] **Step 2: FE 풀 그린** — `npm run typecheck && npm run lint && npm run test`(desktop cwd, [[changed-module-full-test-before-push]]). 변경 모듈 전체 완주.
- [ ] **Step 3: BE 모듈 테스트** — slip-service 전체 test (CI Linux 결과 확인 — 신규 IT 로컬 skip 가능).
- [ ] **Step 4: 🐳 라이브 Docker 실QA** — 실 게이트웨이 :8080, 실 시드 INBOUND 전표 ID 조회(`GET /slips?slipType=INBOUND`로 실재 ID), "매입 전표 인쇄" → 설정기반 결재란(작성자/입고자/검수자 이름 자동채움) 실캡처 + 판매전표(OUTBOUND) 출고자/검수자 이름 채움 회귀 캡처. `docs/qa/inbound-approval-render-s4d/`. ([[per-round-live-qa]] 각 리뷰 라운드 QA agent 인라인.)
- [ ] **Step 5: 듀얼리뷰 0수렴** — 🔵 Opus 5-agent(QA 포함) → 🟣 Codex 5-agent → 양쪽 blocking 0([[rereview-converge-after-fix]]) → 머지.

---

## Self-Review (plan ↔ spec §7 슬4d)

1. **Spec 커버리지**: D-S4D-1(검수란→결재란)=Task4 ✔ / D-S4D-2(비고 footer 흡수)=Task4 Step3(중복 제거) ✔ / D-S4D-3(InboundView 폐기)=Task5 ✔ / D-S4D-4·4b(BE flat 이름 resolve, 입고+판매)=Task1 ✔ / 서명자 매핑(slipType)=Task2 ✔ / OUTBOUND 동반(계약테스트 갱신)=Task3 ✔.
2. **Placeholder**: 없음(각 Step 실 파일경로·테스트 단언·커밋 메시지 명시).
3. **타입 정합**: `roleSignerName(slip, role, slipType)`·flat 필드명(`dispatcherFullName`/`inspectorFullName`/`acceptedByFullName`)이 Task1(BE 응답 키)·Task2(FE 함수)·Task4(SlipDetail)에서 동일. `ApprovalRoleCells({slip, roles, slipType})` Task2 정의 = Task3/4 소비 일치.
4. **마이그 0 / additive only** 재확인 — Flyway 신규 없음, OUTBOUND 무회귀(이름 채움은 잠재갭 해소).
