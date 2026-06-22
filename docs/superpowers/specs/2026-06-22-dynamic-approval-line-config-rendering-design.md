# 동적 결재라인 + 설정=결재란 진실원 (전 전표) — 에픽 설계 spec

> 작성일: 2026-06-22 · 작성: PM(Opus) brainstorming(superpowers) + 3 Explore 정찰 종합 · 상태: **설계 제시 → 개발책임자 spec 리뷰 대기**
>
> 상위 맥락: A2 결재 워크플로우 에픽([2026-06-21-approval-line-config-a2-design.md](2026-06-21-approval-line-config-a2-design.md))의 **연장**. A2-1~1c(설정 메뉴·드래그·라벨·다중 결재자) + A2-2/3/4(출고·입고·주문 enforcement)가 머지된 위에, 결재라인을 **동적(단계 추가/삭제)** + **설정이 문서 결재란을 직접 렌더(진실원)** + **전 전표 확장**으로 확장한다.
>
> 관련 메모리: [[restclient-contract-test-false-green]] · [[applied-migration-immutable]] · [[enforcement-real-http-test]] · [[slip-shipout-print-form]] · [[print-preview-standardization]] · [[temp-multimodel-workflow]] · [[chip-ui-multi-input]] · [[uuid-no-user-visibility]]

---

## 0. 검증 출처 (이 설계가 근거하는 사실 — 추측 아님)

3 Explore 에이전트 정찰(auth 데이터모델 / FE 설정 페이지 / 인쇄 결재란·전표종류)로 도출.

- **현 시스템엔 결재 시스템이 둘 공존**:
  - **A2 `approval_line_config`**(auth): 전표종류별 "누가 각 역할을 결재하나" **설정 + 인가 게이트**(`POST /auth/internal/approval-line/authorize`). 순차 chain 아님 — action 직전 **인가 판정**(opt-in).
  - **`approval-core` + 그룹웨어 `ApprovalLine`/`ApprovalStep`**(A1): 실제 **순차 결재 chain**(PENDING→APPROVED, 단계 상태, loose ref). 그룹웨어 결재문서만 사용. ← **본 에픽 비목표**(건드리지 않음).
- **결재라인 설정의 단계는 현재 시드 고정**: `approval_line_config` 단계 행은 V61(SLIP_OUTBOUND)·V63(SLIP_INBOUND)·V64(PARTNER_ORDER) 마이그가 시드. 가능한 쓰기: reorder(드래그)·rename(라벨)·required 토글·결재자(GROUP/USER) 추가·제거. **단계 추가/삭제 엔드포인트는 없음**(=본 에픽 신규).
- **`action_key`가 enforcement 앵커**: 각 게이트는 코드에서 특정 actionKey(OUTBOUND_DISPATCH·OUTBOUND_INSPECT·INBOUND_RECEIVE·INBOUND_INSPECT·PARTNER_ORDER_CONVERT)로 배선. `authorize(documentType, actionKey, userId)` → `{configured, allowed}`. **immutable(updatable=false)**. CREATOR 역할은 action_key=NULL.
- **인쇄 결재란 렌더 현황**:
  - `DispatchView`(`/sales/:id/print/dispatch`, 화면명 "출고전표 작업지시서"): **하드코딩** — `dispatch-roles` 행 5칸 = `담당부서` / **`작성자`·`출고인`·`검수인`** / `결제예정일`(DispatchView.tsx:150-156). 가운데 3칸이 결재 역할.
  - `OutboundView`(`/sales/:id/print/outbound`, 화면명 "출 고 전 표"): **금액 포함**(공급가/부가세/합계) 거래처 영수증형 + 출고인/인수자. **상세 인쇄 메뉴(SlipDetailPage)에서 미연결 = 사실상 고아 라우트**(playwright/audit만 참조).
  - `InvoiceView`/`SalesInvoicePrintPage`(`/sales/:id/print/invoice`, 거래명세서·세금계산서): 금액 포함 거래처 증빙. **라우트는 `SalesInvoicePrintPage`가 정본**.
  - `ApprovalDocView`(그룹웨어): **유일하게 동적** — `PrintLayout approvalDoc` + `approvalSteps[]`(2~5단계, label+name+서명+시각).
- **FE 설정 페이지**(`ApprovalLineConfigPage.tsx`): 드래그 reorder·인라인 rename·결재자 칩(GROUP/USER)·required 토글·**자동저장**(optimistic+rollback). DOC_TYPES = 3종(SLIP_OUTBOUND="출고전표"/SLIP_INBOUND="입고전표"/PARTNER_ORDER="주문"). **단계 추가/삭제 UI 없음**.

---

## 1. 에픽 목표

판매전표(출고)를 시작으로, 결재라인을 **사용자가 직접 단계를 추가/삭제/이름변경(즉시 적용)** 할 수 있게 하고, 그 **설정 구조가 문서·인쇄의 결재란을 직접 렌더(단일 진실원)** 하게 만든다. 출고전표 완결 후 전 전표로 확장.

### 비목표 (YAGNI — 명시 제외)
- 동적 추가 단계의 **동작 강제(제네릭 enforcement)**: 추가 단계는 표시·서명용. 기존 enforced 단계(출고인/검수인/승인자)는 코드 배선 유지.
- **순차 승인 chain**(approval-core 활성화, 단계별 PENDING→APPROVED 진행/반려/회수).
- **서명 이미지 실제 동결**(사원 서명 등록 슬라이스 별도). 추가 단계는 빈 서명칸으로 렌더.
- 전표종류 **기술 키(SLIP_OUTBOUND 등) 변경**: 명칭은 표시 라벨만 변경, DB documentType 키·enforcement 배선 불변(마이그레이션 회피).

---

## 2. 확정 결정 (브레인스토밍 — 개발책임자 2026-06-22)

| # | 항목 | 확정 |
|---|---|---|
| D1 | 렌더 진실원 | **설정 = 결재란 진실원**. 설정 단계 구조(추가/삭제/이름/순서)가 그 전표의 화면·인쇄 결재란을 렌더. 하드코딩 뷰를 설정 기반으로 전환 + 설정 페이지 실시간 미리보기. |
| D2 | 추가 단계 성격 | **표시·서명용**(action_key=NULL, 동작 강제 X). 기존 enforced 단계는 코드 배선 유지. **enforced/시드 단계 삭제 시 경고 모달**(삭제 시 그 동작 결재강제 해제). |
| D3 | 적용 방식 | **즉시 적용 = 자동저장**(기존 optimistic+rollback 패턴 유지, 별도 저장 버튼 없음). |
| D4 | 출고 양식 통일 | `DispatchView`(작업지시서)가 **유일 출고전표**. `OutboundView`(금액 단 "출고전표")는 **폐기**(거래명세서와 역할 중복). 거래명세서(`/print/invoice`)는 **별도 유지**. |
| D5 | 명칭 | 출고 전표의 사용자 노출 명칭 = **"판매전표"**(판매조회에서 조회 가능한 전표). "작업지시서"·"출고전표" 표기 → "판매전표"(사용자 노출 surface 한정, 기술 키 SLIP_OUTBOUND 불변). |
| D6 | 슬라이싱 | **출고(판매전표) 먼저 완결 → 전표 확대 → 정합 검증 capstone**. 슬1 양식통일+명칭 → 슬2 단계 추가/삭제+경고 → 슬3 결재란 설정기반 렌더+미리보기 → 슬4+ 전표 확대 → **슬5 메뉴↔권한설정 정합 + 권한설정 동작 검증**(개발책임자 2026-06-22 추가). |

---

## 3. 데이터 모델 (동적 단계)

기존 테이블 재사용 — **Flyway 신규 마이그레이션 없음**(updatable JPA + 신규 엔드포인트). [[applied-migration-immutable]] 준수(V61~V64 불변).

### `approval_line_config` (단계 카탈로그)
- 핵심 컬럼: `document_type`(VARCHAR), `sequence`(INT, UK per docType where !deleted), `label`(VARCHAR), `step_type`(CHECK CREATOR|GROUP|USER), `action_key`(VARCHAR nullable, **immutable**), `required`(BOOL), BaseEntity 7 audit + soft-delete.
- **단계 추가** = 신규 행 INSERT: `sequence = max(active sequence)+1`, `step_type='GROUP'`(비-CREATOR 관례), `action_key = NULL`(표시·서명용 — 동작 강제 X), `required` 기본 TRUE, `created_by = actor`(시드 'v61-seed' 등과 구별).
- **단계 삭제** = soft-delete(`is_deleted=true`) + **자식 `approval_line_approver` cascade soft-delete**. CREATOR(sequence 0) 삭제 금지.

### `approval_line_approver` (역할 → 결재자 N)
- `config_role_id`(FK), `approver_type`(CHECK GROUP|USER), `approver_ref_id`(UUID). 기존 추가/제거 엔드포인트 유지.

### enforcement 결합 (action_key) 처리
- **추가 단계**(action_key=NULL): `authorize`는 actionKey로 조회하므로 추가 단계는 어떤 게이트와도 매칭 안 됨 → 동작 무영향(표시·서명용 일관).
- **enforced 단계 삭제**(action_key≠NULL, 예 출고인=OUTBOUND_DISPATCH): soft-delete 시 `findFirstBy…ActionKey…IsDeletedFalse` → 빈 결과 → `authorize` `configured=false` → 게이트 **opt-in 통과**(결재 강제 해제). → **D2 경고 모달의 핵심 근거**.
- **시드/enforced 단계 식별**(경고 트리거): `action_key ≠ NULL` **또는** `created_by ∈ {v61-seed, v63-seed, v64-seed}`. BE 응답에 `enforced: boolean`(action_key 존재) 노출하여 FE가 경고 분기.

---

## 4. 슬라이스 분해

### 슬1 — 판매전표 양식 통일 + 명칭 정정 (FE only, BE 무변)
**범위**
- `OutboundView` + `/sales/:id/print/outbound` 라우트(routes/index.tsx:532) **폐기**. 컴포넌트 제거.
- 참조 정리: playwright(`supplier-profile-bank-stamp-real-qa`·`print-supplement-real-qa`·`print-preview-standardization`·`audit/full-screen-audit`)의 `/print/outbound` 케이스 → `/print/dispatch`로 전환 또는 제거.
- 인쇄 뷰 인벤토리 점검: 거래명세서(`/print/statement`=`SalesTransactionStatementPrintPage`)·세금계산서(`/print/invoice`=`SalesInvoicePrintPage`) 별도 유지 확인, 고아 컴포넌트(`InvoiceView` 사용처 0이면 정리 검토 — 별도 결정).
- **print-renderer 비범위(슬3 이연)**: `print-renderer/PrintRendererApp.tsx`(Phase F 헤드리스 사본 합성)는 OutboundView a4 레이아웃을 **자체 복제**(import 아님)하므로 OutboundView.tsx 삭제에 안 깨짐. 단 사본 인쇄가 여전히 금액 포함 "출고전표" → **판매전표(금액X) 사본 통일은 슬3**(DispatchView 재사용화 시 PrintRendererApp이 그 레이아웃 사용)로 이연. 슬1 후 인터랙티브 미리보기=판매전표(작업지시서)지만 헤드리스 사본=구 금액양식 잔존(슬3에서 해소).
- **명칭 정정(D5)** — 사용자 노출 surface 한정:
  - `DispatchView.tsx:105` 화면명 `'출고전표 작업지시서'` → `'판매전표'`. 인쇄 양식 제목 표기도 정합.
  - `SlipDetailPage.tsx:412` `'출고전표 상세'` → `'판매전표 상세'`(OUTBOUND), 인쇄 메뉴 항목 라벨.
  - `approvalLineConfigApi.ts:33` DOC_TYPES `label '출고전표'` → `'판매전표'`(설정 화면이 렌더 대상과 일관).
  - ※ `SLIP_INBOUND`("입고전표")·`PARTNER_ORDER`("주문") 라벨은 현행 유지(개발책임자 지정 시 별도). 기술 키·주석·식별자 불변.
**검증**: 데스크톱 실 QA — 판매전표 인쇄(작업지시서 양식) 정상, /print/outbound 404/redirect, 거래명세서 별도 정상. FE typecheck+lint+vitest.
**위험**: "출고전표"→"판매전표" 부분 rename 시 입고/출고 비대칭(입고전표 vs 판매전표) 사용자 혼동 가능 → 스펙 리뷰에서 명칭 범위 확정 필요.

### 슬2 — 결재라인 단계 동적 추가/삭제 + 경고 모달 (auth BE + FE)
**BE (auth-service)** — Codex 구현([[temp-multimodel-workflow]])
- `POST /auth/admin/approval-line-configs` (단계 추가): body `{documentType, label}` → §3 규칙으로 행 INSERT(action_key=NULL). page-code `admin.approval-line-config` UPDATE 가드.
- `DELETE /auth/admin/approval-line-configs/{id}` (단계 삭제): soft-delete + 자식 결재자 cascade. CREATOR 거부(400/409). 멱등.
- 조회 응답(`ApprovalLineRoleView`)에 `enforced: boolean`(action_key≠NULL) + `seedManaged: boolean`(created_by 시드) 추가 → FE 경고 분기.
- **Flyway 신규 없음**. 기존 IT(`ApprovalLineConfigControllerIT`)에 add/delete 케이스 + `authorize` 회귀(추가 단계 무게이트·enforced 삭제 후 configured=false) 추가. **실HTTP**([[restclient-contract-test-false-green]]·[[enforcement-real-http-test]]).
**FE**
- 전표종류별 "단계 추가" 버튼 + 단계별(non-CREATOR) 삭제 아이콘. 추가 → 라벨 입력 → POST → optimistic 추가. 결재자는 기존 칩 UI([[chip-ui-multi-input]])로 후속 부여.
- 삭제 분기: `enforced || seedManaged` → **design-system Modal 경고**("이 단계는 [출고 처리/검수] 결재 강제와 연결됩니다. 삭제하면 해당 동작이 더 이상 결재 강제되지 않습니다. 계속할까요?"); 일반 추가 단계 → 단순 확인. 확인 시 DELETE(자동저장·optimistic+rollback).
- mock: 단계 추가/삭제 lifecycle 반영(in-process mock 3원칙 [[inprocess-mock-principles]]).
**검증**: Docker 실서버 라이브 — 단계 추가/삭제 persist, enforced 단계 삭제 시 경고 모달 + 삭제 후 해당 동작 게이트 해제 실증, 추가 단계는 게이트 무영향. 캡처 PR 인라인.

### 슬3 — 판매전표 결재란 설정기반 렌더 + 실시간 미리보기 (FE)
**범위**
- `DispatchView` `dispatch-roles` 가운데 3칸(작성자/출고인/검수인) → **SLIP_OUTBOUND 설정 기반 렌더**. 설정 조회(역할 목록, sequence 순) → 역할별 셀 = `{설정 label} + 매칭 서명자`.
  - **서명자 매핑**: `step_type=CREATOR` → 작성자(`slip.ownerFullName`); `action_key=OUTBOUND_DISPATCH` → `slip.dispatcher?.fullName`; `action_key=OUTBOUND_INSPECT` → `slip.inspector?.fullName`; **추가 단계(action_key=NULL)** → 빈 서명칸(이름·서명 공백, 수기/후속 서명 등록 대상).
  - `담당부서`·`결제예정일`은 결재 역할 아님 → **정보칸으로 현행 유지**(설정 비편입).
- 설정 페이지: 편집 중 결재란 **실시간 미리보기 패널**(라벨/순서/추가/삭제 즉시 반영). 가능하면 `PrintLayout` approval grid 패턴 재사용.
- **print-renderer 재타깃**(슬1 이연분): `DispatchView`를 props 기반 재사용 컴포넌트로 분리하면서 `print-renderer/PrintRendererApp.tsx`(헤드리스 사본 합성)가 OutboundView 금액 클론 대신 **판매전표(작업지시서) 레이아웃 + 설정기반 결재란**을 사용하도록 전환. 사본(창고/기사/인수자) 인쇄도 판매전표 단일 양식으로 통일.
**검증**: 설정에서 라벨 변경/단계 추가 → 판매전표 인쇄 결재란 반영 라이브 캡처. UUID 비공개([[uuid-no-user-visibility]]) — 이름만, action_key/UUID 비노출.
**위험**: 매핑이 action_key 의존 → 사용자가 enforced 단계 삭제 후 재추가(action_key=NULL)하면 서명자 자동매핑 끊김(빈칸). 경고 모달로 사전 고지(D2).

### 슬4+ (이후) — 전 전표 확장
- 슬2~3을 입고전표(INBOUND)·주문(PARTNER_ORDER)·회계전표·견적·배차·거래명세서로 순차 적용. 전표별 (a) 설정 DOC_TYPE 시드/노출 (b) 인쇄 뷰 결재란 설정기반 전환 (c) 서명자 매핑.
- 회계전표·견적·배차·그룹웨어는 enforcement 모델 상이([[document-approval-workflow]] 참조) — 결재란 **표시 렌더만** 우선 적용(동작 강제는 기존/별도). 각 전표 착수 시 brainstorming.

### 슬5 — 메뉴↔권한설정 정합 + 권한설정 동작 검증 (capstone, 개발책임자 2026-06-22 추가)
에픽으로 명칭 변경(판매전표)·신규 페이지/엔드포인트가 누적되므로, **전 슬라이스 완료 후** 권한 체계 정합을 점검한다.
**(a) 메뉴 ↔ 권한설정 목록 정합**
- **실 메뉴/라우트 인벤토리** vs **권한설정 메뉴(권한 매트릭스·page-code 목록)** 양방향 대조:
  - 실 메뉴/페이지인데 권한설정에 page-code 누락(가드 없는 노출) 없는지.
  - 권한설정엔 있으나 실 메뉴/엔드포인트가 없는 **고아 page-code**(예 폐기된 `/print/outbound`, 명칭변경 잔재) 없는지.
  - FE `canAccess` page-code ↔ BE `@RequirePermission` **정확 일치**([[fe-canaccess-pagecode-be-match]], 테마틱 금지).
- 본 에픽 신규(슬2 단계 추가/삭제 엔드포인트)·명칭변경(판매전표) 반영 여부 포함.
**(b) 권한설정 메뉴 정상 동작**
- 권한 grant/revoke → `group_page_permissions` + `account_page_permissions` **materialize 둘 다**([[local-stack-qa-gotchas]] 락아웃 함정) → 실 계정 접근 반영 라이브 검증.
- 비-MASTER(dev_manager) 위임 경로·system-master 보호 회귀.
**산출**: 정합 표(메뉴 N ↔ page-code N, 불일치 목록) + 발견 결함 fix(계열 단위 전수 sweep [[defect-family-sweep-fix]]) + Docker 라이브 실 QA 캡처. 결함 규모 크면 별도 슬라이스로 분리.

---

## 5. 테스트/회귀 전략
- **실HTTP 회귀 필수**(슬2): `authorize` 계약 — 추가 단계 무게이트, enforced 단계 삭제 후 게이트 해제, 입고/주문 기존 enforcement 무회귀. @MockBean 우회 금지([[restclient-contract-test-false-green]]).
- **CI 필터**: 신규 auth 테스트 패키지 ci.yml 등재 확인([[ci-test-filter-false-green]]).
- **Docker 라이브 실 QA**(머지 전, 각 슬라이스): 가짜 데이터 금지([[no-fake-data-ever]]), 실 게이트웨이 :8080 + 실 로그인. 듀얼리뷰 라운드마다 QA agent + 라이브 캡처 인라인([[temp-multimodel-workflow]]).
- **마이그 없음** → fresh probe 불요(슬2 BE는 코드만).

## 6. 미해결 / 스펙 리뷰 확인 사항
1. **명칭 범위(D5)**: "판매전표"를 (a) 인쇄 양식만, (b) + 설정 라벨, (c) + SlipDetailPage 화면명 전부 — 중 어디까지? (현 스펙=b+c, 출고 surface 한정). 입고전표/주문 명칭 동반 변경 여부.
2. **OutboundView 88mm 영수증 수요**: 폐기 시 88mm 열전사 출력 필요성 없는지(현 가정=불요, A4 판매전표 단일).
3. **추가 단계 step_type**: 'GROUP' 고정 vs GROUP/USER 선택(현 스펙=행은 GROUP, 결재자는 혼합 칩). 
4. **슬4 전표 우선순위**: 입고 다음? (현 스펙=입고→주문→회계/견적/배차 순, 개발책임자 지정 가능).
