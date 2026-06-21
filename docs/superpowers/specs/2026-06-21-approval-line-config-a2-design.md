# A2-1 — 결재라인 설정 메뉴(인사그룹 중앙통제) + 선언적 config — 설계 spec

> 작성일: 2026-06-21 · 작성: PM(Opus) brainstorming + 5렌즈 적대검증(wf_3f36aa36, 11 BLOCKER) 재설계 · 상태: **재분해·재설계 확정(개발책임자 2026-06-21) → plan 대기**
>
> 상위 에픽: [2026-06-21-document-approval-workflow-design.md](2026-06-21-document-approval-workflow-design.md)(E12 B 정정 반영) · 선행: [A1 approval-engine](2026-06-21-approval-engine-a1-design.md)(머지 PR #551)
>
> **재분해(개발책임자 2026-06-21)**: 초기 통합 A2(설정 메뉴+slip 배선)를 적대검증이 반증 → **A2-1(본 문서)=결재라인 설정 메뉴+선언적 config 만**, **A2-2=slip 출고 게이트 refactor**(별도 슬라이스, §8). slip 권한구조 엉킴(공유 accept/inspect·단일 slip.transfer.process 6전이·inbound.inspection dead-gate)이 메뉴와 분리되어야 함.
>
> 관련 메모리: [[feedback_chip_ui_multi_input]] · [[feedback_fe_canaccess_pagecode_be_match]] · [[feedback_enforcement_real_http_test]] · [[feedback_post_devlead_decisions_to_pr]]

---

## 1. 배경 / 목표 (적대검증 반영)

A1 이 `shared:approval-core` 엔진을 추출하고 groupware **명시 결재**를 이관했다. A2-1 은 **전 전표 결재라인을 한 곳에서 선언적으로 정의하는 중앙 설정 메뉴**(E7)를 신설한다.

**검증으로 정정된 전제**:
- **출고전표 = B(게이트) 모델**(에픽 E12 정정): 기존 출고 결재란은 명시 결재가 아니라 '처리=자동서명'(`accept`→`dispatcherUserId`, `inspect`→`inspectorUserId` 자동). 명시 결재 대체 아님 — **자동채움 유지 + 권한 그룹 게이트만**. 게이트 실배선은 **A2-2**(§8).
- **작성자 = `requesterId`**(전표 작성자 필드), `createdBy` 아님 — slip `createdBy`='system' 폴백(`JpaAuditingConfig`), 실 작성자=`requesterId`(X-User-Id). A1 CREATOR step `matchesActor`(UUID 비교)도 이 소스로 정정.
- **fillMode 폐기 → A1 `StepType(CREATOR/GROUP/USER)` 재사용**(어휘 이중화 제거).
- **config 자체 테이블**(approval_line_config) — `group_page_permissions` 직접 미조작(권한그룹 관리 메뉴와 split-truth 회피). 선언적 정의만.

**A2-1 목표**: 결재라인 설정 메뉴(인사 그룹, MASTER+위임) — 전표 종류별 결재 역할 ↔ 권한 그룹 매핑 + 필수여부를 **선언적으로 중앙 정의·저장**. (enforcement 는 A2-2+ 가 이 config 를 소비)

**비목표**: slip 출고 게이트 실배선(A2-2) · slip 권한 refactor(A2-2) · 명시 결재(approve/reject) · 입고/회계/배차(A5/A6).

---

## 2. 확정 결정 (개발책임자 2026-06-21)

| # | 항목 | 확정 |
|---|---|---|
| C1 | 재분해 | **A2-1(설정 메뉴+선언 config) / A2-2(slip 게이트 refactor)** 분리. slip 엉킴을 메뉴와 격리. |
| C2 | config 위치 | **auth-service**(RBAC 인접, 설정 메뉴=권한그룹 관리 형제). |
| C3 | 출고 모델 | **B(게이트)** — 자동채움 유지 + 권한 그룹 page-code 게이트(A2-2 실배선). 명시 결재 아님. |
| C4 | 작성자 | **`requesterId`**(전표 작성자), `createdBy` 아님. StepType.CREATOR 의 actor 해석. |
| C5 | config 저장 | **자체 테이블 approval_line_config**(선언적). `group_page_permissions` 미조작. |

---

## 3. 아키텍처 (A2-1 = 선언층만)

```
[선언층 — auth-service]  ← A2-1
  approval_line_config (전표종류별 결재 역할 카탈로그, 선언적)
    역할: {sequence, label, step_type, approver_group_id, required}
    + 결재라인 설정 메뉴(인사 그룹, MASTER+위임) — 역할별 권한 그룹 지정·필수 토글
        (자체 테이블만 write — group_page_permissions 미접촉)
        ▼ (A2-2+ 가 이 config 를 읽어 enforcement 배선)
[실행층]  ← A2-2(slip 출고 게이트) / A5/A6(입고·회계) — 본 슬라이스 밖
```

**핵심**: A2-1 은 **선언적 정의 + 메뉴**만. config 가 무엇을 enforce 하는지는 소비 슬라이스(A2-2)가 결정(출고=accept/inspect slipType 분기 게이트). A2-1 은 `group_page_permissions` 를 건드리지 않아 권한그룹 관리 메뉴와 **진실원 분리**(silent divergence 차단).

---

## 4. config 모델 (approval_line_config)

**`approval_line_config`** (auth-service): 전표 종류별 결재 역할 카탈로그(seed 카탈로그 + 편집 컬럼).

| 컬럼 | 의미 | A2-1 가변? |
|---|---|---|
| document_type | SLIP_OUTBOUND … (`CollabDocumentType` 재사용) | seed 고정 |
| sequence | 역할 순서 | seed(순서변경=후속) |
| label | 표시 명칭(작성자/출고인/검수인) | seed 고정 |
| step_type | `CREATOR`/`GROUP`/`USER` (A1 enum 재사용) | seed 고정 |
| approver_group_id | 역할 권한 그룹(GROUP 만, nullable) | **편집(메뉴)** |
| required | 결재 필수여부(E11, nullable→default) | **편집(메뉴)** |

- **출고 seed**: 작성자(CREATOR, group=null) / 출고인(GROUP, group 편집) / 검수인(GROUP, group 편집).
- **CREATOR 역할**: actor=전표 `requesterId`(C4). 그룹 지정 불가(메뉴에서 "전표 작성자 자동" 표시).
- ⚠️ **A2-1 편집 범위 = `approver_group_id` + `required` 만**. 역할 add/remove·순서변경·page-code 신설은 후속(seed 고정셋).
- **선언적**: A2-1 은 approval_line_config 만 write. 권한 그룹↔실제 권한(page-code grant)은 A2-2 가 config 를 읽어 처리(또는 별도 grant API). A2-1 단독은 **enforcement 무**(메뉴 CRUD 가 산출).
- Flyway: auth-service 다음 V번호 + seed. document_type CHECK 는 application enum 가드(loose, [[feedback_enum_expansion_check_constraint]] — CHECK 시 enum 확장 재마이그 부담).

---

## 5. 결재라인 설정 메뉴 (인사 그룹)

- **위치**: 데스크톱 좌측 "인사" 카테고리, 권한그룹 관리 형제(`AppLayout.tsx:1093-1150`). 신규 `/admin/approval-line-config`.
- **UI**: 전표 종류 선택 → 역할 리스트(순서·명칭·step_type 표시) + GROUP 역할별 **권한 그룹 지정**(AsyncAutocomplete 칩, [[feedback_chip_ui_multi_input]]) + 필수 토글. CREATOR 역할="전표 작성자 자동"(편집 불가).
- **page-code `admin.approval-line-config`**("결재라인 설정") — `PageCode` enum + Flyway seed + FE `permissionPageCatalog`.
  - **위임 정책(정찰 정정)**: **일반 page-code**(MANAGEMENT_PAGE_CODES **미편입**). 이유: `updateDelegations`(`GroupPermissionService`)는 기존 3개 management 코드(system.permission-admin/hr.role-management/admin.permission-groups)에 **하드코딩**이라 신규 코드는 그 위임 경로 불가. 일반 page-code 면 MASTER + 위임받은 MANAGER 가 `updateGroupMatrix` 의 정상 grant 경로로 다룰 수 있음(D-PB-01 의도 충족, [[feedback_pgc_c2_widening_option_a]] 류 정합). seed=MASTER+MANAGER 기본 부여.
- **BE**: auth-service `ApprovalLineConfigController`(admin: 카탈로그 조회/역할 group 지정/필수 토글) + `Service`(approval_line_config CRUD). FE canAccess page-code = 실제 BE `@RequirePermission` **정확 일치**([[feedback_fe_canaccess_pagecode_be_match]]).
- **권한 그룹 picker**: auth 권한 그룹 목록 조회 API(기존 `GroupPermissionService`/권한그룹 관리 화면 소비 API) 재사용.

---

## 6. 슬라이스 경계 + QA

| A2-1 포함 | A2-1 제외 |
|---|---|
| approval_line_config 테이블 + seed(출고 작성자/출고인/검수인) | slip 출고 게이트 실배선 → **A2-2** |
| 결재라인 설정 메뉴(역할 group 지정·필수 토글 CRUD) | slip 권한 refactor(slipType 분기·전용 page-code·4-eye) → A2-2 |
| `admin.approval-line-config` page-code + 위임 | 입고/회계/배차 → A5/A6 |
| 작성자=requesterId 해석 정합(CREATOR) | enforcement(게이트/명시 결재) — config 소비는 후속 |

**QA(듀얼리뷰 라운드마다 QA 에이전트 라이브 캡처)**: 결재라인 설정 메뉴 — 출고전표 출고인/검수인 역할에 권한 그룹 지정 + 필수 토글 실화면(저장·재조회 persist). **MASTER bypass 함정 회피**: 메뉴 접근 권한 검증은 비-MASTER 위임 MANAGER 계정으로([[feedback_enforcement_real_http_test]] 정신).

---

## 7. 미결 (plan/구현 상세)

- approval_line_config ↔ A2-2 enforcement 연결 방식: config 의 approver_group_id → (a) A2-2 가 읽어 전용 page-code 를 그룹에 grant(sync) vs (b) A2-2 가 게이트 시점 config 직접 조회. A2-2 설계에서 확정.
- approval_line_config 가 enforcement 와 분리된 동안 stale 가능성(설정 변경 vs 미반영) — A2-2 sync 설계 시 점검(검증 누락 렌즈: 캐시 무효화 타이밍).
- config=auth vs 자체 도메인 서비스(dc-config 선례) — C2 로 auth 확정(검증 NOTE: 근거 약하나 RBAC 인접 + 메뉴 형제로 수용).

---

## 8. A2-2 (slip 출고 게이트 refactor) — 후속 슬라이스 요건 박제

> 적대검증이 적발한 slip 엉킴 — A2-2 가 반드시 다룰 것:
- **공유 엔드포인트 분기**: `accept`(`SlipController.java:406`)·출고 `inspect`(:434)는 입·출고 공통. 출고 역할 게이트는 **slipType==OUTBOUND 경로에만** 적용(`checkEditPermissionBySlipType` :726 분기 패턴 재사용). 입고 처리 회귀 금지.
- **단일 코드 분리**: `slip.transfer.process`(단일 코드가 accept/process/inspect/complete/ship/deliver 6전이 공유, `PageCode.java`). 출고인=accept·검수인=inspect 전용 page-code(`approval.slip-outbound.dispatch`/`.inspect`) 신설 + 두 엔드포인트만 교체 → **4-eye(출고인≠검수인) 분리 가능**. 잔여 4전이·14 소비처(FE `slipActionPageCode`·`mock.ts`·`PermissionMatrixPage`·seed) 회귀 매트릭스.
- **dead-gate 정합**: inspect 본문 `checkEditPermission(inbound.inspection)`(:441)는 Samhan 에서 X-User-Role 미전송 → no-op + role 시 fail-open. 출고 검수 게이트는 **account 경로 `@RequirePermission`** 로 실효화(role 보조 게이트 추가 금지). arologis(roleBasedEnforcement=true)는 live gate 라 영향 점검.
- **실 HTTP 회귀 필수**([[feedback_enforcement_real_http_test]]): @MockBean `check()` stub 금지(false-green). MockRestServiceServer/Testcontainers 로 신규 page-code seed+grant round-trip + 미권한 403 + 그룹원 200 + **입고 accept/inspect 200** + grant 누락 fail-open negative.
- **위임 self-escalation 경계**: 위임 MANAGER 가 결재라인 설정에서 자기 그룹을 출고인 지정 → self-grant 경로 봉쇄 점검.
- **마이그 롤백 비용**: A2-2 seed(grant)는 [[feedback_applied_migration_immutable]] 로 보상 마이그만 가능 — 되돌림 비용 인지.

---

## 9. 코드 앵커 (정찰 검증)

- 메뉴: `clients/desktop/.../components/AppLayout.tsx:1093-1150`(인사 카테고리) · `routes/PermissionGroupMatrixPage.tsx`/`PermissionGroupManagePage.tsx`(형제) · `permissionPageCatalog.ts`
- 권한: `services/auth-service/.../service/GroupPermissionService.java`(updateGroupMatrix·updateDelegations :150-160) · `domain/PageCode.java`(:639-642 MANAGEMENT_PAGE_CODES) · `ManagementPageMutationGuard`(:99) · `EffectivePermissionMaterializer`(그룹 계승, A1 §6)
- 작성자 정정: `services/slip-service/.../domain/Slip.java:158-159`(requesterId) · `JpaAuditingConfig.java:17-24`(createdBy='system' 폴백) · A1 `ApprovalStepBase.java:89-93`(matchesActor UUID)
- A2-2 slip: `SlipController.java:406`(accept)·`:434-443`(inspect 이중게이트)·`:726`(checkEditPermissionBySlipType 분기)·`:90`(INBOUND_INSPECTION_PAGE_CODE) · `Slip.java:918-934/960-974`(자동채움)
