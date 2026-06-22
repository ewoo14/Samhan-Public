# 슬3 — 판매전표 결재란 설정기반 렌더 + 미리보기 + print-renderer 통일 Implementation Plan

> **For agentic workers:** 구현 = **Codex**([[codex-implements-claude-reviews]]). Opus 계획/리뷰/PR.
> **🚨 듀얼리뷰 순차 강제([[temp-multimodel-workflow]] line29)**: 🔵 Opus 5-agent 라운드 **전체 완료 → PR 게시** → (그 다음) 🟣 Codex 5-agent 라운드 **전체 완료 → PR 게시** → 🟢 PM 종합. **두 모델 라운드 동시 실행 절대 금지.** Opus 라운드 fix=Opus(Claude) 직접 / Codex 라운드 fix=Codex. 양쪽 0 blocking 수렴까지 반복.

**Goal:** 판매전표(출고) 결재란을 **결재라인 설정 구조 기반으로 렌더**(D1 설정=진실원 실현). 인터랙티브 인쇄·헤드리스 사본·설정 미리보기 모두 단일 양식.

**Architecture:** ① auth 비-admin read 엔드포인트(구조 read) → ② DispatchView 결재란 설정기반 렌더(공유 presentational 컴포넌트 추출) → ④ print-renderer(PrintRendererApp)가 공유 컴포넌트 사용 → ③ 설정 페이지 실시간 미리보기.

**Tech Stack:** Spring Boot 3/Java 17 (auth-service), React+TS/@samhan/design-system/react-query (clients/desktop), electron-vite print-renderer.

## Global Constraints
- spec: `docs/superpowers/specs/2026-06-22-dynamic-approval-line-config-rendering-design.md` §4 슬3.
- **서명자 매핑**: CREATOR→`slip.ownerFullName`, action_key=OUTBOUND_DISPATCH→`slip.dispatcher?.fullName`, OUTBOUND_INSPECT→`slip.inspector?.fullName`, **추가단계(action_key=NULL)→빈 서명칸**. 담당부서/결제예정일=정보칸(설정 비편입, 현행).
- **구조 read = 비-admin**(인증 사용자, @RequirePermission 없음, 결재자 신원 제외). admin 편집 엔드포인트 불변.
- **graceful 폴백**: 구조 페치 실패 시 DispatchView 기존 3역할(작성자/출고자/검수자) 하드코딩 폴백 — 인쇄 깨짐 금지.
- UUID 비공개([[uuid-no-user-visibility]]) — 이름만, action_key/UUID 비노출.
- FE green = typecheck+lint+vitest(cwd clients/desktop). 변경 모듈 전체 test 완주 후 push([[changed-module-full-test-before-push]]). desktop typecheck=`npm run typecheck`([[desktop-typecheck-command]]).
- **print-renderer 빌드**: `PrintRendererApp` 변경 시 `npm run build:print-renderer` 통과 확인(별도 vite 빌드).
- **Flyway 없음**(읽기 전용 엔드포인트, 시드/스키마 무변).
- 머지 전 Docker 라이브 실QA([[no-fake-data-ever]]·[[overnight-live-capture]]).

---

### Task 1: BE — 비-admin 구조 read 엔드포인트 (auth-service)

**Files:**
- Create: `ApprovalLineStructureController.java`(또는 기존 비-admin 컨트롤러에 추가) — `GET /auth/approval-line-configs/{documentType}/structure`
- Create: `ApprovalLineStructureView.java`(DTO: sequence, label, stepType, actionKey — **결재자/권한 제외**)
- Modify: `ApprovalLineConfigService.java`(structure 조회 메서드 — 기존 list 재사용 가능하나 view 매핑은 구조 전용)
- Test: `ApprovalLineStructureControllerIT.java`

**계약:** `GET /auth/approval-line-configs/{documentType}/structure` → `ApiResponse<List<ApprovalLineStructureView>>`(sequence ASC). **인증 필수(유효 JWT), @RequirePermission 없음**(판매 사용자 누구나). is_deleted=false 만. 결재자/approver 미포함.

- [ ] Step 1: ApprovalLineStructureView DTO + service structure 조회(sequence ASC, deleted 제외).
- [ ] Step 2: 컨트롤러 — `/auth/approval-line-configs/{documentType}/structure`(NOT under /auth/admin, page-permission 가드 없음). 게이트웨이 /auth/** 라우팅·인증 확인.
- [ ] Step 3: IT — (a) 인증 dev_master GET 200 + SLIP_OUTBOUND 구조(작성자/출고자/검수자 sequence 순·actionKey 정확) (b) 비인증 401 (c) 결재자 신원 미포함.
- [ ] Step 4: `./gradlew :services:auth-service:test` PASS. 커밋 — `feat(approval): 결재라인 구조 비-admin read 엔드포인트 (슬3 Task1)`

---

### Task 2: FE — DispatchView 결재란 설정기반 렌더 + 공유 컴포넌트 추출

**Files:**
- Create: `clients/desktop/src/renderer/print/DispatchDocument.tsx`(props 기반 presentational — 작업지시서 양식 전체 + 설정기반 결재란). DispatchView 본문 로직 이관.
- Modify: `clients/desktop/src/renderer/print/DispatchView.tsx`(slip + 구조 useQuery → `<DispatchDocument>` 렌더, 라우트 래퍼로 축소)
- Modify: `clients/desktop/src/renderer/api/approvalLineConfigApi.ts`(`fetchApprovalLineStructure(documentType)` + ApprovalLineStructure 타입)
- Create/Modify: `clients/desktop/src/renderer/api/mock.ts`(structure 엔드포인트 mock)
- Test: `DispatchDocument.test.tsx`(결재란 렌더 + 서명자 매핑 + 추가단계 빈칸 + 폴백)

**Interfaces:**
- `ApprovalLineStructure = { sequence: number; label: string; stepType: 'CREATOR'|'GROUP'|'USER'; actionKey: string|null }`
- `<DispatchDocument slip={SlipDetail} roles={ApprovalLineStructure[] | null} sourceWarehouseName signatures?={...} />` — roles=null 이면 기존 3역할 폴백.

- [ ] Step 1: `fetchApprovalLineStructure` api + 타입 + mock 핸들러([[inprocess-mock-principles]]).
- [ ] Step 2: DispatchDocument 추출 — 현 DispatchView 본문(SAMSUNG strip·거래처·일련번호·라인표·하단)을 props 기반으로. 결재란 가운데 셀 = roles.map(서명자 매핑). 추가단계(actionKey=null)=빈 서명칸. CREATOR=ownerFullName / OUTBOUND_DISPATCH=dispatcher / OUTBOUND_INSPECT=inspector. roles=null→기존 3역할.
- [ ] Step 3: DispatchView = slip useQuery + 구조 useQuery → `<DispatchDocument>`. 구조 페치 실패=null 전달(폴백).
- [ ] Step 4: vitest — 서명자 매핑 정확·추가단계 빈칸·roles=null 폴백·UUID 비노출.
- [ ] Step 5: FE green. 커밋 — `feat(desktop): 판매전표 결재란 설정기반 렌더 + DispatchDocument 공유 컴포넌트 (슬3 Task2)`

---

### Task 3: FE — print-renderer(PrintRendererApp) 공유 컴포넌트 사용

**Files:**
- Modify: `clients/desktop/print-renderer/PrintRendererApp.tsx`(OutboundView 금액 클론 레이아웃 제거 → `<DispatchDocument>` 사용. 사본 서명(driver/recipient)은 DispatchDocument signatures props로 전달)
- Modify: `clients/desktop/print-renderer/main.tsx`(필요 시 props 배선)
- Modify: `global.css`(필요 시 — 단 .outbound-* 제거는 다른 사용처 확인 후. dispatch-* 클래스 재사용)

- [ ] Step 1: PrintRendererApp 이 `<DispatchDocument>`(판매전표 작업지시서 양식) 렌더하도록 전환. 금액(공급가/부가세/합계)·"출 고 전 표" 타이틀·`:273 출고인` 제거. 사본 서명자(용달기사/인수자) 배치 유지.
- [ ] Step 2: `npm run build:print-renderer` PASS(별도 vite 빌드 깨짐 금지). dist/print-renderer 산출 확인.
- [ ] Step 3: FE green(typecheck+lint+test). 커밋 — `feat(desktop): print-renderer 헤드리스 사본 판매전표 양식 통일 (슬3 Task3)`

---

### Task 4: FE — 설정 페이지 실시간 미리보기 패널

**Files:**
- Modify: `clients/desktop/src/renderer/routes/ApprovalLineConfigPage.tsx`(현재 전표종류 역할 목록 옆/아래 결재란 미리보기 패널 — 편집 중 roles 상태로 렌더)
- Test: `ApprovalLineConfigPage.test.ts`(미리보기 렌더 단언)

- [ ] Step 1: 미리보기 패널 — 현재 편집 중 roles(라벨/순서/추가/삭제 반영)를 결재란 모양으로 렌더(② 공유 컴포넌트 또는 PrintLayout grid 재사용, 서명자=placeholder). 즉시 반영.
- [ ] Step 2: vitest — roles 변경 시 미리보기 반영(라벨/단계수). ⚠️ 설정 페이지 라이브 QA는 admin-게이트 한계(슬2 동일) → vitest 로 검증(정직 명시).
- [ ] Step 3: FE green. 커밋 — `feat(desktop): 결재라인 설정 실시간 미리보기 패널 (슬3 Task4)`

---

### Task 5: Docker 라이브 실QA + 듀얼리뷰(순차) + 머지

- [ ] Step 1: auth 재빌드(read 엔드포인트, `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.local-all.yml build auth-service && up -d auth-service`) + 렌더러 :5175(mock off).
- [ ] Step 2: 라이브 캡처 — (1) 판매전표 인쇄(/print/dispatch) 결재란이 **설정 구조 기반**(작성자/출고자/검수자, 실 read 엔드포인트) 렌더 (2) 설정에 단계 추가 후 인쇄 결재란에 빈 서명칸 추가 반영(가능 시) (3) print-renderer 사본 산출(판매전표 양식·금액 없음). `docs/qa/approval-line-config-render-s3/`.
- [ ] Step 3: PR([[open-pr-early]]·[[pr-title-caps-bracket]]) `[FEAT] 판매전표 결재란 설정기반 렌더 + 미리보기 + print-renderer 통일 (슬3)`. spec/plan·변경요약·QA 캡처 인라인. CI watch.
- [ ] Step 4: **🔵 Opus 5-agent 순차(BE read/FE 렌더/print-renderer/미리보기/완전성) → 게시** → **🟣 Codex 5-agent 순차 → 게시** → PM 종합. **동시 금지.** 0 수렴. 각 라운드 QA 캡처 인라인.
- [ ] Step 5: CI green(GG false-positive 판정) → 머지.

## Self-Review (writing-plans 체크)
- Spec coverage: ①read(T1)·②DispatchView 렌더(T2)·④print-renderer(T3)·③미리보기(T4)·QA+순차듀얼리뷰(T5). spec §4 슬3 전부 매핑. ✅
- Placeholder: 계약·매핑·파일 경로 명시. BE 정확 배치는 Codex가 기존 패턴 확인.
- Type 일관: ApprovalLineStructure(sequence/label/stepType/actionKey) BE DTO ↔ FE 타입 ↔ mock 동일([[fe-option-type-matches-be-dto]]). DispatchDocument props 계약 T2 정의 → T3 소비. ✅
