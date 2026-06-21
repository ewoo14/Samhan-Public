# 전 전표 명시 결재 워크플로우 (동적 결재라인 + 결재 시점 사원 서명) — 설계 spec

> 작성일: 2026-06-21 · 작성: PM(Opus) 재브레인스토밍 종합 · 상태: **설계 확정(개발책임자 2026-06-21) → 슬라이스 분해 대기**
>
> **에픽 재정의**: 기존 "사원 서명 인감 스탬프"([2026-06-21-employee-signature-stamp-design.md](2026-06-21-employee-signature-stamp-design.md))를 개발책임자 결정으로 **전 전표 명시 결재 워크플로우**로 격상. 출고전표 인감 자동 스탬프(실시간) 모델 폐기 → 그룹웨어식 명시 결재(승인 순차 + 결재 시점 서명 동결)로 통일.
>
> 관련 메모리: [[project_slip_shipout_print_form]] · [[project_global_collab_epic]] · 권한 위임(D-PB-01)

---

## 1. 배경 / 목표

개발책임자 재브레인스토밍 결정(2026-06-21):
> "결재란이 출고전표 외에도 입고전표, 회계, 배차, 그룹웨어 등 모두 전표단위임. 출고전표 및 모든 결재라인 전부 동적으로 변경할 수 있게. 물론 기본은 지금 정한 결재라인으로. 명시 결재 워크플로(그룹웨어식)로 통일."

**목표**: 모든 전표(출고·입고·회계·그룹웨어)가 **공통 동적 결재라인 엔진**을 공유한다. 전표 종류별 기본 결재라인을 두되 MASTER + 위임받은 MANAGER가 결재자·순서를 동적 변경하고, 각 결재자가 승인할 때 그 시점의 사원 등록 서명을 동결 삽입(Model B)한다.

**비목표 (YAGNI)**:
- 인감 자동 스탬프(실시간 조회) 모델 — **폐기**(명시 결재로 대체).
- 회사 공급자 인감(거래명세서/세금계산서) — 별도 트랙, 제외.
- 배차 독립 결재 — 배차는 전표 그룹화라 출고전표 결재 경유(추가 작업 0).

---

## 2. 개발책임자 확정 결정 (재브레인스토밍 2026-06-21)

| # | 항목 | 확정 |
|---|---|---|
| E1 | 결재 모델 | **전 전표 명시 결재 워크플로우 통일**(그룹웨어식 순차 승인). 인감 자동 스탬프 폐기. |
| E2 | 결재라인 구성 | **동적** — 전표 종류별 기본 템플릿 + MASTER·위임 MANAGER 가 결재자·순서 변경. |
| E3 | 기본 결재라인 | **결재라인 설정 메뉴에서 정의**(전표 actor 자동 매핑 아님). 출고=작성자/출고인/검수인, 입고=작성자(검수자 없음), 회계/그룹웨어=설정. 각 단계 = 명칭+순서+**권한 그룹**(E8). |
| E4 | 서명 | **결재 시점 동결(Model B)** — 결재자 승인 시 사원 등록 서명(C1 `Employee.signaturePng`) snapshot 삽입. 실시간 조회 아님. |
| E5 | 결재선 구성 권한 | **MASTER + 위임받은 MANAGER**. auth-service page-code 위임(D-PB-01) 재사용. |
| E6 | 엔진 | **그룹웨어 `ApprovalLine`/`ApprovalStep` 재사용·일반화**(전표 종류 무관 공통 실행 엔진). 신설 아님. |
| E7 | 중앙통제 메뉴 | **결재라인 설정 메뉴(인사그룹 신규)** — 모든 전표 종류의 결재라인(**인원수·순서·명칭·권한**)을 한 곳에서 정의·통제. MASTER + 위임 MANAGER. **설정(중앙 템플릿) ↔ 실행(전표별 `ApprovalLine` 인스턴스)** 2층 구조 — 전표 발행 시 설정을 조회해 인스턴스 생성. ad-hoc per-document 선택 아님. |
| E8 | 결재자 소스 = **권한 그룹** | 결재라인 설정에서 각 단계에 **권한 그룹**(기존 권한그룹 시스템) 지정 → 그룹 소속 사원에게 해당 단계 결재 권한 발생. 예: 권한 설정에서 "창고사원" 그룹 생성→실제 창고직원 계정 포함→출고전표 출고인/검수인 단계 권한 그룹="창고사원" → 그 그룹원이 결재. 특정 사원 직접 지정 아님(그룹 기반). |
| E9 | 설정 단위 | **전표 종류별 단일** 결재라인(거래처/금액 조건 분기 없음). |
| E10 | 알림 | **기존 구현 재사용** — 전표 관계자 + 다음 결재자 자동 알림(§7 collab `resolveNotificationRecipients`). 신규 없음, 결재 워크플로우 연결만 확인. |
| E11 | 결재 필수 여부 | **결재라인 설정에서 전표 종류별 설정 가능**(필수/선택 토글). 필수=결재 완료 후 전표 유효, 선택=결재란 표시용(미완 허용). |
| E12 | lifecycle 관계 | **대체** — 기존 출고 lifecycle(`accept`→`dispatcherUserId` 자동 채움 / `inspect`→`inspectorUserId`)을 **폐기**하고, 결재라인 설정의 권한 그룹 명시 결재로 일원화. 자동 채움 제거 → 명시 결재 단계로 전환. |

---

## 3. 아키텍처 — 재사용 기반 (조사 wf_8d35f9af 근거)

```
[공통 결재 엔진 = groupware-service ApprovalLine/ApprovalStep 일반화]
  - approverIds:List<UUID> → sequence 0-base 자동, 2~5칸 가변 (ApprovalLineCreateRequest)
  - 순차 승인 currentStep()→approve(approverId)→PENDING/APPROVED (ApprovalLine)
  - 결재자 동적 검색·추가·제거 UI (GroupwareApprovalCreatePage AsyncAutocomplete)
        ▲ 재사용
        │
[전표 ↔ 결재선 연계 (신설)]
  - 출고/입고/회계 전표 ↔ ApprovalLine (FK 또는 ref_doc_no 역참조)
  - 전표 종류별 기본 결재라인 템플릿 + MASTER/위임 MANAGER 동적 변경
  - 결재선 발의 internal API (현 POST /admin/groupware/approvals 는 admin UI 전용 → internal 확장)
        │
[서명 동결 (Model B)]
  - 결재자 approve() 시 user-service /internal/users/signatures 로 그 시점 Employee.signaturePng snapshot
  - approval_step_signature(또는 ApprovalStep 컬럼) 에 동결 저장 (재등록/무효화 무관 — 결재 시점 보존)
  - PrintLayout SignatureViewer(signaturePngBase64) 렌더 (이미 준비됨)
        │
[위임 (재사용)]
  - auth-service page-code grant/revoke 위임(GroupPermissionService, D-PB-01, MASTER 전용·재위임 차단)
  - "결재선 구성 권한" page-code 신설 → MASTER → MANAGER 위임
```

**조사 확정 사실**:
- 그룹웨어 동적 결재선 **완비**(`ApprovalLineCreateRequest.approverIds`, `ApprovalLine.appendStep` sequence 자동, 순차 승인, AsyncAutocomplete UI).
- page-code 위임 **완비**(auth-service `GroupPermissionService.updateDelegations`, MASTER 전용, 재위임 차단).
- 서명 저장소 **완비**(C1a `Employee.signature_*`, `SignatureViewer`, PrintLayout approvalSteps).
- 미비(신설 필요): ① 위임을 결재선 구성에 적용(현 그룹웨어는 결재자=전 사원 자유선택, 롤/위임 게이트 없음), ② 전표↔ApprovalLine 연계, ③ 결재선 발의 internal API, ④ 결재 시점 서명 동결.

---

## 4. 전표별 기본 결재라인 (E3)

| 전표 | 기본 결재라인 | 비고 |
|---|---|---|
| 출고전표(Dispatch/Outbound/Invoice) | 작성자 / 출고인 / 검수인 | 기존 createdBy/dispatcherUserId/inspectorUserId 가 기본 결재자 |
| 입고전표 | 작성자 | 검수자 없음(개발책임자 정정) |
| 회계전표 | (동적, 기본 최소) | 전용 print 신설 동반 |
| 그룹웨어 결재 | (동적, 기존) | 이미 동적 — placeholder 서명만 채움 |

전부 MASTER·위임 MANAGER 가 기본 위에서 결재자·순서 동적 변경.

---

## 5. 슬라이스 분해 (제안 — 각 슬라이스 착수 시 상세 brainstorming)

> ⚠️ C1a/C1b/C2(사원 서명 등록·mobile-public) **머지 완료** → 서명 소스로 재사용. C3(출고 인감 plan)은 본 에픽으로 **재설계**.

- **A1 (공통 결재 엔진 일반화)** — **상세 설계 확정: [2026-06-21-approval-engine-a1-design.md](2026-06-21-approval-engine-a1-design.md)** (6렌즈 적대검증 wf_49f53117 → 9 BLOCKER 반영). **재단됨(개발책임자 2026-06-21)**: A1 = `shared:approval-core` 추출 + **groupware 이관/회귀(BE+FE)만**. slip 출고 결재선 골격은 step 모델·lifecycle 확정되는 **A2 로 이연**(dead-weight 회피). ⚠️ 본 §3 의 "collab-core 동형 단순 추출, 테이블 동일" 전제는 **반증·정정**됨 — `@MappedSuperclass` base 는 steps 컬렉션을 보유 못 함(base=스칼라+무상태 로직 / concrete=관계매핑+전용필드). E8=**page-code enforcement**(경로B, 계승 검증 참). step 모델=`stepType(CREATOR|GROUP|USER)` union(nullable, NOT NULL 금지).
- **A2 (결재라인 설정 메뉴 — 인사그룹 중앙통제)**: 모든 전표 종류의 결재라인(**인원수·순서·명칭·권한**)을 정의하는 **신규 메뉴(인사그룹)**. MASTER + 위임 MANAGER(page-code 위임 D-PB-01) 통제. 전표 종류 ↔ 결재라인 설정(`approval_line_config` 류) 저장 + 결재선 구성 권한 page-code 신설. 전표 발행 시 이 설정으로 `ApprovalLine` 인스턴스 생성(**설정↔실행 2층**, E7).
- **A3 (결재 시점 서명 동결)**: approve() 시 `Employee.signaturePng` snapshot 저장(approval_step_signature) + PrintLayout SignatureViewer 실연동(placeholder 해소).
- **A4 (출고전표 결재 배선)**: 출고전표 발의/승인 + DispatchView/OutboundView/InvoiceView 결재란 + lifecycle 관계 확정.
- **A5 (입고전표 결재 배선)**: 입고전표(작성자 기본) + InboundView 결재란 신설.
- **A6 (회계전표 결재 배선)**: 회계전표 결재선 + 전용 print 라우트 신설.
- **A7 (그룹웨어 placeholder 해소)**: 그룹웨어 결재 서명 동결 실연동(A3 자동 수혜일 수 있음).

---

## 6. 미결정 (각 슬라이스 brainstorming 에서 확정)

- **결재 필수성**: 전표 발행/확정에 결재 승인이 **필수**(승인 완료 후 유효) vs **선택**(결재란 표시용)? 전표 종류별로 다를 수 있음.
- **lifecycle 관계**: 기존 출고 lifecycle(`accept`/`inspect` 자동 채움)과 명시 결재의 관계 — 병행(자동 채움 유지 + 결재 추가) vs 대체(자동 채움 폐기, 명시 결재만)?
- **전표↔결재선 연계 방식**: ApprovalLine 직접 FK vs ref_doc_no 역참조 vs 전표 도메인에 결재선 임베드.
- **결재 시점 서명 동결 위치**: approval_step_signature 신규 테이블 vs ApprovalStep 컬럼.
- **회계 결재 = 그룹웨어 엔진 재사용 vs 회계 자체**(조사 reuseVerdict=재사용 가능, 단 연계 설계).
> ✅ **2026-06-21 해소(개발책임자)**: 결재자 소스=**권한 그룹**(E8, 기존 권한그룹 재사용) · '권한'=단계별 결재 권한 그룹(E8) · 설정 단위=**전표 종류별 단일**(E9) · 결재자 범위=권한 그룹 소속(E8) · 알림=기존 재사용(E10).

> ✅ **2026-06-21 추가 해소(개발책임자)**: 결재 필수 여부=**결재라인 설정 토글**(E11) · lifecycle=**대체**(E12, 기존 출고 `accept`/`inspect` 자동 채움 폐기→명시 결재 일원화).

**잔여(순수 구현 상세, 각 슬라이스):** 전표↔결재선 연계 방식(FK vs ref) / 서명 동결 저장 위치(`approval_step_signature` vs `ApprovalStep` 컬럼) / 회계 재사용 경로(그룹웨어 엔진 직접 vs 일반화 공통). ⚠️ E12 대체로 출고 lifecycle 회귀 범위(기존 `dispatcherUserId`/`inspectorUserId` 소비처: progress-bar 등) sweep 필요 — A4 slice.

---

## 7. 관련 자산 / 코드 앵커 (조사 검증)

- `services/groupware-service/.../domain/ApprovalLine.java` (appendStep sequence 자동) · `ApprovalStep.java` (approve PENDING→APPROVED) · `dto/ApprovalLineCreateRequest.java` (approverIds 동적) · `service/ApprovalLineService.java`
- `clients/desktop/.../routes/GroupwareApprovalCreatePage.tsx` (AsyncAutocomplete 결재자 동적 구성) · `print/PrintLayout.tsx:175` (SignatureViewer placeholder)
- `services/auth-service/.../service/GroupPermissionService.java` (page-code 위임 D-PB-01) · `docs/superpowers/specs/2026-06-05-permission-groups-phase-b-delegation-design.md`
- `services/user-service/.../domain/Employee.java` (signature_png, C1a) · `/internal/users/signatures` 배치(C1a)
- 기존 인감 spec: `docs/superpowers/specs/2026-06-21-employee-signature-stamp-design.md` (C1/C2 머지·재사용, C3 재설계)
