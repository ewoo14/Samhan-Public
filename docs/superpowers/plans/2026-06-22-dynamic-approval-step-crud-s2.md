# 슬2 — 결재라인 단계 동적 추가/삭제 + 경고 모달 (+출고자/검수자 V65) Implementation Plan

> **For agentic workers:** 구현 = **Codex**([[codex-implements-claude-reviews]]·[[temp-multimodel-workflow]]). Opus 계획/리뷰/PR. 실행 = Codex 디스패치 → Opus·Codex 듀얼 5-agent 0수렴 → Docker 라이브 실QA 캡처 → PR.

**Goal:** 결재라인 설정에서 단계(역할)를 **추가/삭제**(즉시 적용)할 수 있게 하고, enforced/시드 단계 삭제 시 **경고 모달**을 띄운다. 더불어 출고 결재 역할 라벨 **출고인→출고자, 검수인→검수자**("인"→"자")를 V65로 정정한다.

**Architecture:** auth-service `approval_line_config`(단계 카탈로그) + `approval_line_approver`(결재자). 단계 추가=신규 행 INSERT(action_key=NULL=표시·서명용), 삭제=soft-delete+자식 cascade. 기존 테이블 재사용(추가/삭제용 Flyway 없음). V65는 **라벨 UPDATE 전용** 마이그. FE 설정 페이지에 단계 추가 버튼 + 삭제 아이콘 + 경고 모달.

**Tech Stack:** Spring Boot 3 / Java 17 / JPA / Flyway / PostgreSQL (auth-service). React + TypeScript / @samhan/design-system / react-query (clients/desktop).

## Global Constraints
- **에픽 spec**: `docs/superpowers/specs/2026-06-22-dynamic-approval-line-config-rendering-design.md` §3·§4(슬2). 결정 D2(추가 단계=표시·서명용·action_key=NULL / enforced·시드 삭제 시 경고).
- **추가 단계 = 표시·서명용**: action_key=NULL → authorize 게이트 무영향(동작 강제 X). 기존 enforced 단계(action_key≠NULL: OUTBOUND_DISPATCH/INSPECT 등)는 코드 배선 유지.
- **삭제 = soft-delete**(is_deleted), 자식 `approval_line_approver` cascade soft-delete. **CREATOR(sequence 0) 삭제 금지**(400/409).
- **enforced 단계 삭제 시 게이트 해제**: soft-delete → `authorize(documentType, actionKey)` 빈 결과 → configured=false → opt-in 통과(무강제). **경고 모달로 사전 고지**.
- **V61~V64 불변**([[applied-migration-immutable]]). 라벨 정정 = **신규 V65**. action_key는 sequence ROW_NUMBER 매핑이라 라벨 rename 무관(enforcement 무영향).
- **마이그 추가 시 fresh Postgres probe 검증**([[migration-fresh-postgres-probe]]): DROP/CREATE DB + 대상테이블 seed + `cat V65.sql | psql ON_ERROR_STOP`.
- **실HTTP 회귀**([[restclient-contract-test-false-green]]·[[enforcement-real-http-test]]): authorize 계약 — 추가 단계 무게이트, enforced 삭제 후 게이트 해제, 입고/주문 무회귀. @MockBean 우회 금지.
- **FE green = typecheck + lint + vitest**(cwd clients/desktop). **변경 모듈 전체 test 완주 후 push**([[changed-module-full-test-before-push]]).
- **머지 전 Docker 라이브 실QA**([[no-fake-data-ever]]·[[overnight-live-capture]]).

---

### Task 1: V65 — 출고 결재 역할 라벨 정정 (출고인→출고자, 검수인→검수자)

**Files:**
- Create: `services/auth-service/src/main/resources/db/migration/V65__approval_line_outbound_label_rename.sql`
- Modify(FE mock): `clients/desktop/src/renderer/api/mock.ts` (SLIP_OUTBOUND 역할 라벨 출고인/검수인 → 출고자/검수자, 핸드오프 참조 ~:12632/:12641)
- Modify(인쇄 결재란): `clients/desktop/src/renderer/print/DispatchView.tsx` (RoleCell label "출고인"→"출고자", "검수인"→"검수자", :153-154 부근)

**V65 내용** (created_by 가드로 사용자 rename 행 보존):
```sql
UPDATE approval_line_config SET label = '출고자', modified_at = now(), modified_by = 'v65-seed'
 WHERE document_type = 'SLIP_OUTBOUND' AND created_by = 'v61-seed' AND label = '출고인' AND is_deleted = FALSE;
UPDATE approval_line_config SET label = '검수자', modified_at = now(), modified_by = 'v65-seed'
 WHERE document_type = 'SLIP_OUTBOUND' AND created_by = 'v61-seed' AND label = '검수인' AND is_deleted = FALSE;
```

- [ ] Step 1: V65 작성 + fresh Postgres probe(별도 DB·V61~V64 적용 후 V65 `ON_ERROR_STOP`)로 적용 검증.
- [ ] Step 2: mock.ts SLIP_OUTBOUND 역할 라벨 출고자/검수자 반영.
- [ ] Step 3: DispatchView 인쇄 결재란 RoleCell 라벨 출고자/검수자.
- [ ] Step 4: 검증 — auth `gradlew :services:auth-service:test` 관련 IT(시드 라벨 단언 있으면 갱신) + FE typecheck/lint/test. 라벨 단언 grep("출고인"/"검수인") 갱신.
- [ ] Step 5: 커밋 — `feat(approval): V65 출고 결재 역할 라벨 출고자/검수자 정정 (슬2)`

**※ action_key(OUTBOUND_DISPATCH/INSPECT) 불변 → enforcement 무영향. 상세 화면 결재정보 카드(출고인/검수인 표기)도 라벨 소스가 config면 자동 반영, 하드코딩이면 동반 정정(grep 확인).**

---

### Task 2: BE — 단계 추가/삭제 엔드포인트 + enforced/seedManaged 노출 (auth-service)

**Files (기존 패턴 따름 — Codex가 실제 코드 확인):**
- Modify: `ApprovalLineConfigController.java` (POST 추가, DELETE 삭제 엔드포인트)
- Modify: `ApprovalLineConfigService.java` (addStep, deleteStep 메서드)
- Modify: `ApprovalLineRoleView`(응답 DTO) — `enforced`(action_key≠null)·`seedManaged`(created_by∈ v61/v63/v64-seed) 필드 추가
- Modify(IT): `ApprovalLineConfigControllerIT.java` + `ApprovalLineAuthorizeControllerIT.java`

**계약:**
- `POST /auth/admin/approval-line-configs` — body `{documentType, label}`. 동작: `sequence = max(active sequence for docType)+1`, `step_type='GROUP'`, `action_key=NULL`, `required=true`, `created_by=actor`. page-code `admin.approval-line-config` UPDATE 가드. 응답=신규 ApprovalLineRoleView.
- `DELETE /auth/admin/approval-line-configs/{id}` — soft-delete + 자식 approver cascade soft-delete. CREATOR(step_type=CREATOR/sequence 0) → 400/409 거부. 멱등(이미 삭제=204/404 일관). page-code UPDATE 가드.
- `ApprovalLineRoleView`에 `boolean enforced`(actionKey!=null)·`boolean seedManaged`(createdBy in {v61-seed,v63-seed,v64-seed}) 추가 → 기존 list 조회에 포함.

- [ ] Step 1: ApprovalLineRoleView enforced/seedManaged 필드 + 매핑. 기존 list IT 에 단언 추가(출고자=enforced·seedManaged true, 신규 추가 단계=false).
- [ ] Step 2: addStep 서비스+컨트롤러. IT: 추가 후 list 에 신규 단계(sequence max+1·action_key null·enforced false) 존재.
- [ ] Step 3: deleteStep 서비스+컨트롤러(soft-delete+cascade·CREATOR 거부). IT: 삭제 후 list 미포함·자식 approver 미포함·CREATOR 삭제 400.
- [ ] Step 4: **authorize 회귀 IT(실HTTP)** — (a) 추가 단계(action_key null)는 어떤 actionKey 와도 매칭 안 됨(게이트 무영향) (b) enforced 단계 soft-delete 후 `authorize(SLIP_OUTBOUND, OUTBOUND_DISPATCH)` → configured=false (c) 입고/주문 enforcement 무회귀.
- [ ] Step 5: 변경 모듈 전체 test 완주(`gradlew :services:auth-service:test`). 커밋 — `feat(approval): 결재라인 단계 동적 추가/삭제 엔드포인트 + enforced/seedManaged (슬2)`

---

### Task 3: FE — 단계 추가/삭제 UI + 삭제 경고 모달 (clients/desktop)

**Files:**
- Modify: `clients/desktop/src/renderer/routes/ApprovalLineConfigPage.tsx` (단계 추가 버튼 + 단계별 삭제 아이콘 + 경고 모달)
- Modify: `clients/desktop/src/renderer/api/approvalLineConfigApi.ts` (addStep/deleteStep API + 타입에 enforced/seedManaged)
- Modify(mock): `clients/desktop/src/renderer/api/mock.ts` (단계 추가/삭제 핸들러, enforced/seedManaged 필드, [[inprocess-mock-principles]] 3원칙)

**UI:**
- 전표종류별 "단계 추가" 버튼 → 라벨 입력(design-system) → `POST` → optimistic 추가(rollback). 결재자는 기존 칩 UI로 후속 부여.
- 단계별(non-CREATOR) 삭제 아이콘. 클릭 시:
  - `enforced || seedManaged` → **design-system Modal 경고**: "이 단계는 [동작] 결재 강제와 연결됩니다. 삭제하면 해당 동작이 더 이상 결재 강제되지 않습니다. 계속할까요?" (취소/삭제).
  - 일반 추가 단계(enforced=false·seedManaged=false) → 단순 확인.
  - 확인 시 `DELETE` → optimistic 제거(rollback).
- CREATOR 단계: 삭제 아이콘 미노출(기존 disabled 패턴).

- [ ] Step 1: api 타입 enforced/seedManaged + addStep/deleteStep 함수.
- [ ] Step 2: 단계 추가 버튼 + mutation(optimistic+rollback, 기존 패턴).
- [ ] Step 3: 삭제 아이콘 + 경고 모달 분기(enforced/seedManaged) + mutation.
- [ ] Step 4: mock 핸들러(추가/삭제 lifecycle + enforced/seedManaged) + 기존 vitest 갱신/추가.
- [ ] Step 5: FE green(typecheck+lint+test). 커밋 — `feat(approval): 결재라인 단계 추가/삭제 UI + 삭제 경고 모달 (슬2)`

---

### Task 4: Docker 라이브 실QA + PR

- [ ] Step 1: auth-service 재빌드(`docker compose build auth-service && docker compose up -d auth-service`, BE 변경 반영) + 렌더러 :5175(mock off).
- [ ] Step 2: 라이브 캡처 — (1) 결재라인 설정에서 단계 추가(라벨 입력→추가) (2) 추가 단계 삭제(단순 확인) (3) enforced 시드 단계(출고자) 삭제 시 **경고 모달** (4) 출고전표 결재란/설정 라벨 "출고자/검수자" 반영. `docs/qa/dynamic-approval-step-crud-s2/`.
- [ ] Step 3: PR([[open-pr-early]]·[[pr-title-caps-bracket]]) `[FEAT] 결재라인 단계 동적 추가/삭제 + 경고 모달 (슬2)`. spec/plan 링크·변경요약·QA 캡처 인라인. CI watch.
- [ ] Step 4: 듀얼 5-agent(Opus 게시→Codex 게시→PM 종합, 0수렴) + 각 라운드 QA 캡처 인라인.

## Self-Review (writing-plans 체크)
- Spec coverage: 슬2(spec §4) = 단계 추가(T2/T3)·삭제+경고(T2/T3)·enforced 보호(T2 authorize 회귀)·V65 라벨(T1). ✅
- Placeholder: V65 SQL·계약·UI 분기 명시. BE 정확 메서드명은 Codex가 기존 코드 확인(패턴 명시). 
- Type 일관: enforced/seedManaged BE DTO ↔ FE 타입 ↔ mock 동일([[fe-option-type-matches-be-dto]]). ✅
