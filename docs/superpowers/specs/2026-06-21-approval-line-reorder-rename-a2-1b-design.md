# A2-1b — 결재라인 역할 순서 변경 + 라벨 이름 변경 설계

> A2-1(PR #552 머지, 결재라인 설정 메뉴)의 증분. A2-1 은 역할별 **권한 그룹 + 필수여부** 만 편집 가능했고, **순서변경·라벨변경을 "후속(seed 고정셋)" 으로 미뤘다**(개발책임자 원래 결정 "권한그룹·필수·순서 변경까지" 와 어긋난 한정). 본 증분이 그 갭을 해소한다.
>
> 선행: [A2-1 spec](2026-06-21-approval-line-config-a2-design.md) · [에픽 spec](2026-06-21-document-approval-workflow-design.md)

## 목표

인사 그룹 "결재라인 설정" 메뉴에서 전표 종류별 결재 역할의 **순서를 드래그로 변경**하고 **역할 라벨을 인라인 편집**한다. enforcement(A2-2)는 본 증분 범위 밖 — 선언적 config 의 sequence/label 만 변경.

## 개발책임자 결정 (2026-06-21 AskUserQuestion)

| 결정 | 내용 |
|---|---|
| **이름 변경 대상** | **역할 라벨만**(출고인→출고담당, 검수인→품질검수 등). 결재라인 전체 이름(라인명) 도입 안 함 — 전표 종류명이 곧 라인 식별. |
| **순서 UX** | **드래그 정렬**(design-system 드래그 핸들, #495 세트 구성품 정렬 패턴 재사용). |
| **작성자(CREATOR) 처리** | **고정** — 항상 1순위 잠금(드래그 X) + 라벨 변경 불가. 출고인/검수인(GROUP)만 reorder + rename. |

## 아키텍처

A2-1 의 `auth.approval_line_config` 테이블 재사용. **스키마 변경 없음** — `label`·`sequence` 컬럼은 이미 존재(A2-1 에서 `updatable=false` 였던 것을 `true` 로). 신규 엔드포인트 2개(rename, reorder) + FE 드래그/인라인편집. group_page_permissions 미조작(A2-1 선언 모델 유지).

### 데이터 모델 (변경)
- `ApprovalLineConfig.label` — `@Column(updatable = true)` 로 전환. 도메인 `rename(String label)` 추가(blank 거부, trim).
- `ApprovalLineConfig.sequence` — `@Column(updatable = true)` 로 전환. 도메인 `changeSequence(int seq)` 추가.
- **불변 유지**: `documentType`·`stepType` 은 여전히 `updatable=false`(역할 정체성 불변).

### CREATOR 가드 확장
A2-1 후속(PR #553)에서 `updateRole` 이 CREATOR 의 group/required 변경을 이미 거부. 본 증분에서 **rename·reorder 도 CREATOR 거부**로 확장:
- `renameRole`: stepType==CREATOR 면 `BusinessException(INVALID_INPUT, "작성자 역할은 변경할 수 없습니다")`.
- `reorderRoles`: 결과 순서에서 **CREATOR 가 1순위(최소 sequence)가 아니면 거부**(`BusinessException(INVALID_INPUT, "작성자는 항상 첫 순서여야 합니다")`).

### sequence unique 제약 — swap 안전 처리
`uq_approval_line_config_doctype_seq_active (document_type, sequence) WHERE is_deleted=false` 존재 → 두 역할의 sequence 를 직접 교환하면 **중간 상태에서 unique 충돌**. 해법(2-phase, 단일 트랜잭션):
1. 대상 documentType 의 전 역할을 **임시 음수 오프셋**(`sequence = -(sequence+1)`)으로 일괄 UPDATE → 충돌 없는 음수 공간 이동.
2. 요청 순서대로 `sequence = 0,1,2,...` 재할당.
`@Transactional` + `saveAllAndFlush` 로 phase 사이 flush. (self-invocation 프록시 우회 주의 [[feedback_self_invocation_transactional_bypass]] — 컨트롤러→서비스 public 메서드 단일 진입.)

### 부분요청 가드 ([[feedback_defect_family_sweep_fix]] / #495 display-orders 패턴)
`reorderRoles(documentType, orderedIds)` 의 `orderedIds` 는 **해당 documentType 의 활성 역할 전체와 정확히 일치**해야 함(집합 동일성 검증). 누락/잉여/타 documentType id → `BusinessException(INVALID_INPUT, "결재라인 역할 전체를 순서대로 전달해야 합니다")`. silent partial 손상 방지.

## API

| 메서드 | 경로 | 권한 | 본문 |
|---|---|---|---|
| PUT | `/auth/admin/approval-line-configs/{id}/label` | admin.approval-line-config UPDATE | `{ "label": "출고담당" }` |
| PUT | `/auth/admin/approval-line-configs/reorder?documentType=SLIP_OUTBOUND` | admin.approval-line-config UPDATE | `{ "orderedIds": ["<작성자id>","<검수인id>","<출고인id>"] }` |

- rename 응답: 갱신된 `ApprovalLineRoleView` 단건.
- reorder 응답: 갱신된 역할 리스트(sequence 재정렬됨).
- 기존 GET `/approval-line-configs?documentType=`·`/groups`·PUT `/{id}`(group/required) 유지.

## FE (clients/desktop)

`routes/ApprovalLineConfigPage.tsx`:
- **드래그**: 비-CREATOR 행에 드래그 핸들(⠿). design-system 드래그(#495 `ProductSetComponentReorder` 패턴 재사용). 드롭 시 `reorder` 호출(작성자 1순위 고정 — 작성자 위로는 드롭 불가). 낙관 업데이트 + onError 롤백(PR #553 패턴 일관).
- **라벨 인라인 편집**: 비-CREATOR 행의 역할 라벨을 인라인 텍스트(또는 편집 아이콘 ✎). blur/Enter 시 `rename` 호출(blank 거부). CREATOR 라벨은 정적 텍스트.
- **자동저장**: rename(blur/Enter)·reorder(drop) 즉시 저장(저장 버튼 없음, A2-1 자동저장 일관). 낙관/롤백.
- `api/approvalLineConfigApi.ts`: `renameApprovalLineRole(id, label)` · `reorderApprovalLineRoles(documentType, orderedIds)`. mock(`mock.ts`) 동반(stateful: label/sequence 갱신).
- **동시 편집**: 낮은 동시성 전제로 별도 잠금/버전 충돌 처리는 미지원. 각 mutation 의 `onSettled` invalidate 로 최종 서버 상태에 수렴한다.

## 테스트

- **단위**(`ApprovalLineConfigServiceTest`): rename(정상/blank거부/CREATOR거부) · reorder(정상 swap·sequence 재할당 / CREATOR 1순위 위반 거부 / 부분요청 거부 / unique 무충돌).
- **IT**(`ApprovalLineConfigControllerIT`, 실HTTP @MockBean 없음 [[feedback_enforcement_real_http_test]]): 비-MASTER MANAGER 로 rename PUT 200·reorder PUT 200(순서 반영 확인) + CREATOR rename 4xx.
- **마이그레이션**: 스키마 변경 없음(updatable 은 JPA 레벨). V61 불변([[feedback_applied_migration_immutable]]) — 신규 Flyway 없음.
- **FE**: vitest(rename onChange 계약, reorder 낙관/롤백). 
- **🐳 라이브 QA**(매 리뷰 라운드, [[feedback_temp_multimodel_workflow]]): 비-MASTER MANAGER(dev_manager) 결재라인 설정 → 출고인/검수인 드래그 순서 교환 + 출고인 라벨 "출고담당" 변경 → reload persist 캡처. 작성자 드래그 불가·라벨 고정 확인. (real-qa 헤드리스 드래그 flaky → 마우스 [[feedback_realqa_run_and_false_red]].)

## 범위 밖
- 역할 add/remove(고정 3역할 유지), page-code 신설, enforcement(A2-2), 결재라인 전체 이름(라인명), 전표 종류 추가(현 SLIP_OUTBOUND 1종).

## 워크플로우
Codex 구현 → 🔵Opus 5-agent+QA → 🟣Codex 5-agent+QA → fix→다음 라운드 → **양쪽 0 수렴까지 머지 금지**(개발책임자 명시) → 머지. 매 라운드 라이브 드래그/rename 캡처 인라인 게시.
