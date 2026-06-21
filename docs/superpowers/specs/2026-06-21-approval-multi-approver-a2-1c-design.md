# A2-1c — 결재 역할 다중 결재자(그룹 + 개인) 캡슐 설계

> A2-1/A2-1b(결재라인 설정 메뉴) 증분. **개발책임자 요구(2026-06-21)**: "권한그룹 말고도 **개인도** 설정. 하나만 말고 **캡슐 형태로 여러 개**. 그룹웨어 같은 경우 특정 문서는 특정 인물만 결재." → 역할당 결재자를 **단일 그룹 → 그룹·개인 다중(N)** 으로 확장.
>
> 선행: [A2-1 spec](2026-06-21-approval-line-config-a2-design.md) · [A2-1b spec](2026-06-21-approval-line-reorder-rename-a2-1b-design.md). 후속: A2-2(출고 enforcement).

## 목표

결재라인 설정 메뉴에서 각 APPROVER 역할(출고인/검수인 등)에 **권한 그룹과 개인 사원을 캡슐(칩)로 여러 개** 지정한다. 전 documentType 일반 모델(그룹웨어 결재문서 포함). enforcement(accept/inspect 게이트)는 A2-2.

## 개발책임자 결정 (2026-06-21)

| 결정 | 내용 |
|---|---|
| **다중 결재자** | 역할당 결재자 = 그룹 + 개인 **혼합 N개**(캡슐). |
| **UI** | design-system `AsyncAutocomplete` + `TagChip` 칩 ([[feedback_chip_ui_multi_input]], §7 `GroupwareApprovalCreatePage` 패턴 재사용). |
| **enforcement(A2-2 예고)** | 그룹·개인 균일 처리 위해 **동적 config 조회**(page-code grant E8 폐기 — 개인이 page-code grant 에 부적합). |
| **미지정 fallback(A2-2)** | opt-in — 결재자 0개면 기존 `slip.transfer.process` 권한자 유지(무중단). |
| **4-eye(A2-2)** | 권장만(강제 X, 동일인 허용). |

## 아키텍처

A2-1 의 `auth.approval_line_config`(역할 정의) + 신규 자식 테이블 `approval_line_approver`(역할당 N 결재자). 칩 UI 는 §7 패턴 재사용. **shared `StepType`(approval-core, 그룹웨어 공용) 불변** — 리네임 안 함(blast-radius 회피).

### 데이터 모델

**`approval_line_config`(역할 — 변경)**
- 유지: documentType, sequence, label, **stepType**(`CREATOR`=작성자 자동 / `GROUP`=결재 단계 — 값 불변, 의미만 "approver step"), required.
- **deprecate**: `approverGroupId`(NULL 허용 유지, 자식 테이블로 이관 후 후속 제거). 도메인 `assignGroup/clearGroup` 폐기.
- **신규 `actionKey`**(VARCHAR nullable, 안정 앵커): 라벨·순서가 바뀌어도 A2-2 가 이걸로 accept/inspect 매핑. 출고인=`OUTBOUND_DISPATCH`, 검수인=`OUTBOUND_INSPECT`, 작성자=NULL. **reorder/rename 무관**.

**`approval_line_approver`(결재자 — 신규)**
- `id`(UUID PK), `config_role_id`(FK → approval_line_config.id), `approver_type`(VARCHAR `GROUP|USER` — auth 로컬 enum, shared StepType 아님), `approver_ref_id`(UUID — 그룹 id 또는 account id), BaseEntity 7 audit + soft delete.
- CHECK `approver_type IN ('GROUP','USER')`. unique 활성 (config_role_id, approver_type, approver_ref_id) WHERE is_deleted=false(중복 칩 방지).
- 역할당 N행. CREATOR 역할은 결재자 0행(작성자 자동).

**마이그레이션 V62**(V61 불변 [[feedback_applied_migration_immutable]])
1. `ALTER approval_line_config ADD COLUMN action_key VARCHAR(40)`.
2. UPDATE action_key: 출고인(label 시드값/stepType=GROUP seq=1)=`OUTBOUND_DISPATCH`, 검수인=`OUTBOUND_INSPECT`. (V61 seed label '출고인'/'검수인' 기준 — A2-1b rename 전 fresh 적용 가정. 라이브 재적용은 fresh probe.)
3. CREATE TABLE `approval_line_approver` + 인덱스/CHECK.
4. 기존 `approverGroupId IS NOT NULL` 역할 → `approval_line_approver`(type=GROUP, ref=approverGroupId) 1행 INSERT(데이터 이관).

### CREATOR 가드 (A2-1/A2-1b 일관)
CREATOR 역할: 결재자 추가/제거·rename·reorder 모두 거부(기존 가드 확장). 작성자=전표 작성자 자동, 결재자 칩 없음.

## API (auth)

| 메서드 | 경로 | 권한 | 본문 |
|---|---|---|---|
| GET | `/auth/admin/approval-line-configs?documentType=` | VIEW | 역할별 **approvers 배열**(각 {id, type, refId, displayName}) 포함 |
| GET | `/auth/admin/approval-line-configs/groups` | VIEW | 그룹 옵션(기존) |
| GET | `/auth/admin/approval-line-configs/users?q=&limit=` | VIEW | **사원(account) 검색**(신규) — id, 표시명(이름·부서), loginId 비공개 가드 |
| POST | `/auth/admin/approval-line-configs/{roleId}/approvers` | UPDATE | `{type:'GROUP'\|'USER', refId}` — 추가(존재검증·system-master 그룹 거부·CREATOR 거부·중복 거부) |
| DELETE | `/auth/admin/approval-line-configs/{roleId}/approvers/{approverId}` | UPDATE | 제거 |
| PUT | `/auth/admin/approval-line-configs/{id}` | UPDATE | `{required}` — 필수여부만(approverGroupId 제거) |
| PUT | `/auth/admin/approval-line-configs/{id}/label` · `/reorder` | UPDATE | A2-1b 유지 |

- 응답 `ApprovalLineRoleView`: `approverGroupId/Name` → **`approvers: [{id, type, refId, displayName}]`** 로 교체. UUID 비공개(displayName 만 표시, refId 는 선택값).
- 사원 검색: auth account 검색(이름 contains, 활성). 신규 `AccountSearchService` 또는 기존 account repo 재사용.

## FE (clients/desktop)

`routes/ApprovalLineConfigPage.tsx`:
- APPROVER 역할 행 "권한 그룹" 컬럼 → **"결재자" 칩 컬럼**: `AsyncAutocomplete` (그룹+사원 검색, 타입 구분 표시) + 선택 시 `addApprover` → `TagChip`(`[그룹] 개발자 ✕` / `[사원] 홍길동 ✕`) 목록. 제거=`removeApprover`. **낙관 setQueryData + onError 롤백**(#553 패턴), 즉시 저장.
- §7 `GroupwareApprovalCreatePage`(AsyncAutocomplete<ApproverOption> + TagChip) 패턴·스타일 재사용.
- 작성자(CREATOR) 행: "전표 작성자 자동" 정적 유지. A2-1b 드래그·라벨 인라인·필수 토글 유지.
- `api/approvalLineConfigApi.ts`: `searchApprovalLineUsers(q)`, `addApprovalLineApprover(roleId, type, refId)`, `removeApprovalLineApprover(roleId, approverId)`. 기존 `updateApprovalLineRole` → required 전용. mock stateful 동반.

## enforcement 예고 (A2-2)
accept → `actionKey=OUTBOUND_DISPATCH` 역할의 결재자 집합(그룹∪개인), inspect → `OUTBOUND_INSPECT`. auth 내부 엔드포인트 `POST /internal/approval-line/authorize {documentType, actionKey, userId, userGroups}` → 결재자 집합 동적 검증(userId ∈ USER 결재자 OR userGroups ∩ GROUP 결재자). slip-service accept/inspect 게이트가 호출(slipType==OUTBOUND, opt-in: 결재자 0개면 skip). 별도 spec.

## 테스트
- **단위**(`ApprovalLineApproverServiceTest`): addApprover(GROUP/USER 정상·중복 거부·system-master 그룹 거부·CREATOR 거부·미존재 ref 거부) · removeApprover · 사원 검색.
- **IT**(`ApprovalLineConfigControllerIT` 확장, 실HTTP @MockBean 없음 [[feedback_enforcement_real_http_test]]): 비-MASTER MANAGER 로 approver POST(GROUP+USER) 200·DELETE 200·CREATOR 역할 approver POST 4xx·사원 검색 200.
- **마이그 V62**: fresh Postgres probe([[feedback_migration_fresh_postgres_probe]]) — action_key 컬럼·approval_line_approver 테이블·approverGroupId 이관 1행 검증.
- **FE**: vitest(addApprover/removeApprover onChange 계약·낙관 롤백·칩 렌더). 
- **🐳 라이브 QA**(매 리뷰 라운드): 비-MASTER MANAGER 결재라인 설정 → 출고인에 그룹 칩 + 사원 칩 여러 개 추가 → 제거 → reload persist 캡처. 작성자 칩 불가 확인.

## 범위 밖
- enforcement(A2-2), 그룹웨어 documentType config 시드(후속), 역할 add/remove, approverGroupId 컬럼 물리 제거(후속), 결재 순차/병렬 정책(단계 내 N결재자는 OR — 누구나 1인 처리, A1 1-인 모델 일관).

## 워크플로우
Codex(danger-full-access) 구현 → 🔵Opus 5-agent+QA → 🟣Codex 5-agent+QA → fix→다음 라운드 → **양쪽 0 수렴까지 머지 금지** → 머지. 매 라운드 라이브 칩 캡처 인라인 게시.
