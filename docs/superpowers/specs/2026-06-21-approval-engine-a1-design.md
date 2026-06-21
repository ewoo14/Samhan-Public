# A1 — 공통 결재 엔진 일반화 (approval-core 추출 + groupware 이관) — 설계 spec

> 작성일: 2026-06-21 · 작성: PM(Opus) brainstorming + 6렌즈 적대검증(wf_49f53117) 종합 · 상태: **설계 확정(개발책임자 2026-06-21) → 구현 plan 대기**
>
> 상위 에픽: [2026-06-21-document-approval-workflow-design.md](2026-06-21-document-approval-workflow-design.md) (전 전표 명시 결재 워크플로우). 본 문서는 그 **A1 기반 슬라이스**의 상세 설계이며, 에픽 spec §5 의 A1 스케치(및 §3 의 "collab-core 동형 단순 추출" 전제)를 **정정·대체**한다.
>
> 관련 메모리: [[project_global_collab_epic]] · [[feedback_identity_header_authz_antipattern]] · [[feedback_applied_migration_immutable]] · [[feedback_migration_fresh_postgres_probe]] · [[project_kst_timezone_standard]] · [[feedback_enum_expansion_check_constraint]]

---

## 0. 검증 출처 (이 설계가 근거하는 사실)

본 설계는 **추측이 아니라 실제 코드 정찰 + 적대검증**으로 도출됐다.

- **6렌즈 적대검증 워크플로우**(wf_49f53117, 7-agent): JPA 영속성 / E8 권한그룹 step 모델 / MSA 경계 / Flyway / A1 범위·시퀀싱 / collab-core 충실도 + 완전성 비평. → **9 BLOCKER** 도출, 초기 설계의 핵심 전제 2개("collab-core 동형 base 가 steps 보유" · "단순 추출, 테이블 동일")를 코드로 반증.
- **권한 계승 검증**(general-purpose agent): page-code enforcement(경로B)의 린치핀인 "계정→권한그룹 편입 시 page-code 권한 계승"을 `EffectivePermissionMaterializer` 추적으로 **참 확정**.

---

## 1. A1 목표 (재단됨)

**A1 = approval-core 공유 라이브러리 추출 + groupware 결재 엔진을 그 base 로 이관(회귀 무손실).** slip 출고전표 결재선 골격은 step 모델·연계가 확정되는 **A2 로 이연**(개발책임자 2026-06-21 결정, 아래 §2-결정7).

A1 의 산출은 그 자체로 검증 가능하다: **라이브 그룹웨어 결재가 base 상속 후에도 동일하게 동작**(BE IT + FE vitest 회귀 green). approval-core 는 후속 슬라이스(slip/회계/입고)가 상속할 토대가 된다.

**비목표(A1 제외)**: slip/회계/입고 결재선 배선, 결재라인 설정 메뉴(A2), 서명 실제 동결(A3), 출고 lifecycle 대체(A4), 결재 필수여부 enforcement(E11), 결재선 동적 변경 UI(E2/E5).

---

## 2. 확정 결정 (개발책임자 2026-06-21 brainstorming)

| # | 항목 | 확정 |
|---|---|---|
| 결정1 | 엔진 위치 | **분산** — 도메인/로직을 신규 `shared:approval-core` 로 추출. 각 서비스가 자기 DB 에 결재 테이블 보유(collab-core 와 동일 모듈 패턴). cross-service 호출은 서명 동결(user-service)·알림뿐, **결재선 생성/승인은 서비스 내부**. |
| 결정2 | groupware 이관 시점 | **즉시** — A1 에서 groupware `ApprovalLine`/`ApprovalStep` 을 approval-core base 상속으로 리팩터링. 테이블 **additive 확장(기존 컬럼 무변경, nullable 추가만)**, 라이브 결재 **회귀 무손실(BE+FE)**. |
| 결정3 | 전표↔결재선 연계 | **loose ref** — 결재선 테이블이 `document_type`(VARCHAR)+`document_id`(UUID) 보유. FK 없음. `CollabDocumentType` enum 재사용(APPROVAL_LINE 등 기존재). 전표 비연계 결재(그룹웨어 독립형)는 둘 다 **NULL**. |
| 결정4 | 서명 동결 자리 | `ApprovalStepBase` 에 `signature_png_snapshot`(bytea,nullable)+`signed_at`(timestamptz,nullable) **A1 에서 선신설**(값 null). 별도 테이블 안 만듦. 실제 동결은 A3. |
| 결정5 | 결재라인 관리 메뉴(A2) | **설정/통제 전용**(템플릿: 단계수·순서·명칭·권한그룹·필수여부 토글). 통합 진행 모니터링 현황판 없음(YAGNI). 설정↔실행 2층(E7). |
| 결정6 | E8 결재 권한 enforcement | **page-code 경로** — step 에 결재 page-code 부여, approve 시 `DynamicPermissionClient.check(actorAccountId, pageCode, APPROVE)` 로 검증. 권한그룹은 설정·표시용으로 병기. 신규 cross-service 0. (검증: 계정→그룹 page-code 계승 참 확정, §6) |
| 결정7 | A1 범위 | **축소** — approval-core 추출 + groupware 이관/회귀만. slip 출고 결재선 골격은 A2 로 이연(step 모델·lifecycle 미확정 + dead-weight 회피). |

---

## 3. 아키텍처 — base/concrete 분리 (정정됨)

> ⚠️ **초기 전제 폐기**: "collab-core 처럼 `ApprovalLineBase` 가 steps 컬렉션을 보유"는 **Hibernate 에서 불가능**하다. `@MappedSuperclass` 제네릭 base 는 per-service concrete 타입의 `@OneToMany` 를 매핑할 수 없다(erasure 로 targetEntity·mappedBy 대상 미해석). collab-core 는 이 문제를 **회피**한 것(parentId=plain UUID, 컬렉션 0개)이지 푼 게 아니다. 결재 chain(양방향 @OneToMany + cascade + orphanRemoval + @OrderBy)은 더 어려운 케이스다.

**정답 구조**: **base = 스칼라 컬럼 + 무상태 step 로직** / **concrete @Entity = 관계 매핑 + @Version + 서비스 전용 필드 소유**.

```
shared/approval-core  (신규 java-library — collab-core 와 동일 모듈 패턴)
  ├─ ApprovalLineBase  (@MappedSuperclass extends BaseEntity)
  │     스칼라만: approvalNo, requesterId, title, documentType, documentId, status
  │     무상태 로직: approve/reject/withdraw/currentStep — 단,
  │       abstract List<? extends ApprovalStepBase> stepsView()  ← concrete 가 자기 컬렉션 노출
  │       protected abstract void registerStep(ApprovalStepBase step)
  │     ✗ steps 컬렉션 보유 안 함  ✗ @Version 보유 안 함(collab 박제: concrete 가 선언)
  │     ✗ 그룹웨어 전용 필드(templateId/fieldValuesJson/content/overlay) 보유 안 함
  ├─ ApprovalStepBase  (@MappedSuperclass extends BaseEntity)
  │     stepType(CREATOR|GROUP|USER), sequence, status, decidedAt(timestamptz), reason,
  │     approverUserId(UUID,null), approverGroupId(UUID,null), requiredPageCode(VARCHAR,null),
  │     approvedByUserId(UUID,null), signaturePngSnapshot(bytea,null,LAZY), signedAt(timestamptz,null)
  │     ※ 결재자 식별 컬럼 전부 nullable — stepType 으로 분기, NOT NULL 절대 금지(§5)
  ├─ ApprovalLineService<L extends ApprovalLineBase>  (제네릭 1-파라미터)
  │     ApprovalRepositoryPort<L> 위임(save/findById/findByDocument). step 은 L 내부 캡슐화.
  ├─ ApprovalRepositoryPort<L> / ApprovalMembershipPort (멤버십·권한 판정 위임 SPI)
  └─ ApprovalCoreAutoConfiguration  (realtime 비의존 — collab 의 @ConditionalOnBean(RealtimeBroker) 복붙 금지)
        ▲ 상속 (concrete 가 관계·버전·전용필드 소유)
   ┌────────────────────────────┐
   groupware-service (A1 이관)         [A2+ : slip / accounting / inbound concrete]
   ApprovalLine(@Entity approval_lines)
     extends ApprovalLineBase
     + @OneToMany(cascade=ALL,orphanRemoval) @OrderBy List<ApprovalStep> steps
     + @Version Long version
     + templateId / fieldValuesJson(jsonb) / content / overlay*   ← 그룹웨어 전용, 잔류
     + stepsView()/registerStep() override
   ApprovalStep(@Entity approval_steps) extends ApprovalStepBase
     + @ManyToOne ApprovalLine approvalLine
```

**근거 코드 앵커**: `groupware ApprovalLine.java:101-103`(진짜 @OneToMany), `:96-98`(@Version concrete), `:276-285`(approve 단일사원 동치), `ApprovalStep.java:39-45`(@ManyToOne + approverId), `collab CollabCommentRecord.java:61-62`(컬렉션 회피=plain UUID), `CollabSuggestionService.java:18`(@Version=concrete 의무 박제), `CollabCoreAutoConfiguration.java:24-35`(인프라 빈만 autoconfig), `SlipCollabConfig.java:25-103`(consumer 가 generic service 를 new + record 어댑터로 Port 배선).

**제네릭 1-파라미터로 축소**: collab 의 모든 generic service 는 타입 1개(`CollabCommentService<T>`)다. `ApprovalLineService<L,S>` 2-파라미터는 배선 복잡도를 높인다 → **step 을 L 내부 메서드(`line.approve(...)`)로 캡슐화**하고 service 는 `<L>` 1개만 노출. 현 `ApprovalLine.approve/reject` 가 이미 그 캡슐화를 한다.

---

## 4. step 모델 — stepType union (E8 + 회귀 + 입고 단독라인 동시 수용)

현 `ApprovalStep.approverId`=특정 사원 1명, approve 는 정확 1:1 일치(`ApprovalLine.java:281`). 이대로는 (a) E8 권한그룹, (b) 출고 1단계 '작성자'(=requester, 현 엔진이 **불변식으로 차단** `ApprovalLine.java:147-149`), (c) 입고 '작성자' 단독 결재라인을 표현 못 한다. → **`stepType` enum 으로 일반화**:

| stepType | 결재자 식별 | approve 검증 | 용도 |
|---|---|---|---|
| **USER** | `approverUserId` | actor == approverUserId (현행 동치) | **groupware 기존**(전 사원 자유선택). 회귀 보존. |
| **GROUP** | `approverGroupId`(표시) + `requiredPageCode`(enforce) | `check(actorAccountId, requiredPageCode, APPROVE)` (경로B) | E8 전표 결재(출고인/검수인 등). |
| **CREATOR** | 전표 `createdBy` | 발의 시점 `approvedByUserId=createdBy` 자동 채움(또는 self-approve) | 출고/입고 '작성자' 1단계. requester-차단 불변식을 **CREATOR 에는 미적용**. |

- 모든 결재자 식별 컬럼(`approverUserId`/`approverGroupId`/`requiredPageCode`)은 **nullable**. stepType 으로 분기.
- `approvedByUserId`(실 승인자) = approve 시 채움. 누가 승인했는지 감사(현 모델엔 부재).
- **A1 구현 범위**: base 컬럼/enum/stepType 분기 골격 + USER 모드 approve(=현행 유지, groupware 회귀). GROUP 멤버십 검증·CREATOR 자동채움의 **실 배선은 A2/A4**(설정메뉴·전표 연계 시). A1 은 컬럼을 nullable 로 선반영해 A2~A6 가 base 재변경 없이 얹히게만 한다.
- requester-차단 불변식: 현 `ApprovalLine.java:147-149` 를 stepType 분기로 수정(USER/GROUP 에만 적용, CREATOR 예외).

---

## 5. Flyway / 마이그레이션 (additive · NOT NULL 금지)

- **groupware = 다음 V8**(현 최신 V7). slip = 다음 V49(A2 에서). service-per-DB 라 순서 의존 없음.
- **groupware V8 = ALTER `ADD COLUMN IF NOT EXISTS`(V4 컨벤션)·nullable 만**: `approval_lines` 에 `document_type`/`document_id`(nullable), `approval_steps` 에 `step_type`(VARCHAR)·`approver_group_id`·`required_page_code`·`approved_by_user_id`·`signature_png_snapshot`(bytea)·`signed_at`(TIMESTAMP) 전부 nullable ADD. `step_type` 만 기존행='USER' 결정적 backfill 후 NOT NULL. **기존 처리완료 USER step `approved_by_user_id = approver_id` 백필**(status IN APPROVED/REJECTED — USER 모드는 결재자=실승인자, 듀얼리뷰 R1 Codex 발견).
  - **NOT NULL backfill 절대 금지**: 기존 라이브 결재행의 `approver_id`(사원)→권한그룹 역매핑은 1:N 비결정적이라 불가. `approver_group_id` 등을 NOT NULL 로 못 박으면 기존행에서 SET NOT NULL 실패. 기존행=USER 모드(approver_id) 영속, document_type/id=NULL(독립형 결재).
  - 기존 `approver_id` 컬럼/엔티티 필드는 **그대로 유지**(USER 모드). `approverUserId` = 기존 `approverId` 재명명 없이 매핑 정렬.
- **시각 컬럼 규약 = plain TIMESTAMP**(듀얼리뷰 R1 확정 — 초기 timestamptz 권고 정정): `ApprovalStepBase.signedAt`/`decidedAt` 은 `LocalDateTime`(wall-clock) 매핑이라 plain TIMESTAMP 가 정합. 기존 `decided_at`(V1)·BaseEntity audit 전부 naive TIMESTAMP 이고 KST 전역표준은 postgres GUC(`-c timezone`)+JVM(`-Duser.timezone`)로 처리([[project_kst_timezone_standard]]). timestamptz 로 바꾸면 LocalDateTime 바인딩에 세션-TZ 변환이 끼어 오히려 위험. **timestamptz 는 향후 신규 service(slip A2) 가 `Instant`(절대시각) 컬럼을 쓸 때 그 컬럼에 한해 적용**(V44 decided_at 교훈=Instant 전용).
- **document_type CHECK 제약**: 신규 전표종류 추가 시 CHECK 재마이그가 필요해진다([[feedback_enum_expansion_check_constraint]]). A1 은 그룹웨어만이라 값은 NULL/APPROVAL_LINE 뿐 → **CHECK 생략하고 application-side(CollabDocumentType enum)로 가드**(loose-ref 정신과 일관, slip 도입 시 CHECK 누락 INSERT 거부 위험 회피). 결정 시 spec 에 명문화.
- **[[feedback_applied_migration_immutable]]**: 적용된 V*.sql(주석조차) 수정 금지. V8 은 신규 파일.
- **[[feedback_migration_fresh_postgres_probe]]**: V8 을 push 전 fresh Postgres + 라이브 유사 픽스처(approver_id 채워진 기존행)에 `cat V8.sql | psql ON_ERROR_STOP` 직접 적용 → NOT NULL 미강제·기존행 무손상 실증. + jar standalone 부팅으로 @MappedSuperclass 컬럼↔DDL 컬럼 validate 통과 확인.

---

## 6. E8 page-code enforcement (경로B) — 검증된 가드레일

**린치핀 검증 결과(참)**: 계정을 권한그룹에 편입하면 그룹의 page-code 권한을 계승한다.
- 그룹 다수 = 합집합(OR, `EffectivePermissionMaterializer.unionGroupPermissions:103-115`).
- **개인 권한 우선** — 개인 override 가 page 단위 완전 교체(명시 DENY 가능, `applyOverrides:117-121`).
- **"관련 있는 권한만"(scoped)** — 그룹에 행 있는 page-code 만 계승(blanket 누수 없음, `:106-112`).
- 공식: `effective(page) = override(page) ?? OR(group_page_permissions[page])`.

**구현 가드레일 2개(spec 박제)**:
1. **materialized 캐시 구조** — `check()` 는 `account_page_permissions` 캐시만 읽는다. 멤버십/그룹권한/override/role 변경 4축은 `materializeForAccount/Group` 으로 재계산 트리거됨(끊김 없음 확인). **신규 결재 page-code 를 그룹에 부여하는 A2 코드는 반드시 materialize 동반**(우회 시 stale → 계승 조용히 끊김).
2. **account 경로 사용 의무** — 결재 검증은 `DynamicPermissionClient.check(actorAccountId, pageCode, APPROVE)` 만 사용. `canView/canEdit(roleCode,…)`(role 경로, `role_page_permissions` 직접)는 **그룹 계승 무관**(arologis 전용/레거시) → 결재에 쓰면 안 됨.

→ approve 시점 cross-service 신규 0(모든 서비스가 DynamicPermissionClient 보유). 결정1 분산 경계 무위반.

---

## 7. groupware 이관 회귀 범위 (BE + FE)

결정2 "즉시 이관"이 진짜 refactor-in-place 가 되려면 **USER 모드 보존**이 핵심. 회귀 검증 대상:
- **BE**: `ApprovalLineServiceTest`(:136/144 approverId 동치 단언), `GroupwarePermissionControllerIT`(:210/216 approve/reject). base 상속 후 동일 통과 + fresh Postgres 부팅.
- **FE**(완전성 비평가 적발 — 6렌즈 BE 편중으로 누락됐던 것): `GroupwareApprovalCreatePage`(AsyncAutocomplete 사원 직접선택), `ApprovalDocView.tsx`, `approvalDoc.test.ts`, `groupwareApprovalApprover.ts` 등이 `approverId`=사원 UUID 계약에 묶임. **vitest + typecheck 회귀를 A1 범위에 편입**([[feedback_desktop_typecheck_command]]). USER 모드 유지라 FE 계약 무변경이 목표(깨지면 base 설계 오류 신호).

---

## 8. A1 범위 경계

| A1 포함 | A1 제외 (후속) |
|---|---|
| `shared:approval-core` 추출(base/service/port/autoconfig) | slip 출고 결재선 골격 → **A2** |
| **PoC 우선**: base/concrete 분리가 Hibernate 부팅 통과하는지(§3) 첫 작업 | 결재라인 설정 메뉴 → **A2** |
| groupware `ApprovalLine`/`Step` base 이관(USER 모드) | GROUP 멤버십 enforce 실배선 → A2 |
| groupware V8(additive nullable) + fresh probe | 서명 실제 동결 → **A3** |
| step 모델 컬럼(stepType union, nullable) 선반영 | 출고 lifecycle 대체 sweep → **A4** |
| 서명 컬럼 자리(null, LAZY) | 입고/회계/그룹웨어 placeholder → A5/A6/A7 |
| BE IT + FE vitest/typecheck 회귀 무손실 | 알림 신규 배선(§9) → A2+ |

---

## 9. 후속 슬라이스 미결 (완전성 비평가 도출 — A1 비포함이나 박제)

- **E10 알림 = "작업량 0" 거짓 확정**: `approve()`/`reject()` 경로는 현재 알림 **0건**(`resolveNotificationRecipients` 는 collab edit 흐름에서만 호출, 게다가 broadcast 라 "다음 결재자" 타깃 아님). → "연결만 확인"을 **"승인/반려 성공 후 다음 결재자 알림 신규 배선"**으로 정정. A1(이관/회귀 = 동작 보존)에는 미포함, A2+ 신규 기능으로.
- **E11 결재 필수여부 enforcement 지점 미검증**: "결재 미완 → 전표 확정 차단"이 어디(전표 confirm 경로 vs 결재선 vs 설정테이블)에 사는지 A2 에서 확정.
- **E2/E5 동적 변경 vs append-only**: 현 `ApprovalLine` 은 `appendStep` 만, `approver_id`/`sequence` updatable=false(불변). "발행된 인스턴스" 변경 vs "중앙 템플릿"만 변경(E7 2층)을 A2 에서 base 가변성 관점으로 설계.
- **결재선 구성 권한 게이트**: 현 그룹웨어는 "롤/위임 게이트 없음, 전 사원 자유선택"(에픽 spec §3). E5 page-code 위임(D-PB-01) enforce 지점을 A2 에서 확정.
- **배차 결재 경유**: 에픽 §1 "배차=출고 결재 경유(추가작업 0)" 주장 타당성 미검증 → 해당 슬라이스에서.
- **A3 서명 동결 무결성**: `/internal/users/signatures` 는 internal token=ROLE_MASTER 라 임의 user 서명 조회 가능 → "동결 대상=현 승인 actor 본인"을 **호출측(approval-core/서비스)이 강제**(endpoint 가 안 막아줌).
- **A3 서명 LAZY**: `ApprovalStepBase.signaturePngSnapshot` 의 `@Basic(LAZY)` 는 bytecode enhancement 없으면 EAGER 강등(현재 groupware 빌드 미적용). A3 서명 동결 시 **enhancement 또는 projection/별도 1:1 테이블 동반 필수**(목록 N건×PNG EAGER 로딩 회피).

**듀얼리뷰 R1(🔵Opus+🟣Codex) 도출 A2 박제 (A1 무회귀, 후속 의무)**:
- **approver_id DROP NOT NULL**: V8 은 `approver_id` NOT NULL 유지(USER 단독). A2 GROUP/CREATOR step(`approver_id`=null, `approver_group_id`/`required_page_code` 사용) INSERT 전 `ALTER COLUMN approver_id DROP NOT NULL` 마이그 필수.
- **`matchesActor` 확장 hook**: 현 `ApprovalStepBase.matchesActor` 는 package-private + USER 동일성 하드코딩. A2 에서 GROUP/CREATOR 권한 판정(page-code `DynamicPermissionClient.check`)을 위해 **protected hook 또는 권한 판정 port/context** 설계.
- **`resolveDisplayNames` null 필터**: groupware `ApprovalLineService.resolveDisplayNames` 의 `List.copyOf(ids)` 는 null 불허 → A2 GROUP/CREATOR(approverUserId=null) 시 NPE. null 필터 동반.
- **제네릭 엔진 실 통합 IT**: `ApprovalLineService<L>`/`ApprovalRepositoryPort` 는 A1 production 무소비(FakePort 단위만). A2 slip 배선 시 **Testcontainers 실 영속(@Version 낙관락·findByDocument 실 쿼리) IT** 첫 검증 의무.
- **loose-ref 인덱스**(A1 사후 보강 라운드 발견): `findByDocument(document_type, document_id)` 실배선 슬라이스(A2)에서 `CREATE INDEX IF NOT EXISTS ... ON approval_lines (document_type, document_id) WHERE is_deleted=FALSE` 동반(부분 인덱스 컨벤션). 미동반 시 전 결재선 풀스캔.

---

## 10. 코드 앵커 (정찰 검증)

- 엔진: `services/groupware-service/.../domain/ApprovalLine.java`(:101-103 @OneToMany, :96-98 @Version, :147-149 requester 차단, :174-183 종합전이, :276-285 단일사원 동치) · `ApprovalStep.java`(:39-45) · `service/ApprovalLineService.java`(:47-78)
- collab 패턴: `shared/collab-core/.../CollabCommentRecord.java`(:61-62 plain UUID) · `CollabCommentService.java`(:22,143-157 generic+Port) · `CollabCoreAutoConfiguration.java`(:24-35) · `CollabDocumentType.java`(:9-19 APPROVAL_LINE) · `services/slip-service/.../collab/SlipCollabConfig.java`(:25-103 배선 선례)
- 권한 계승: `shared/security/.../DefaultDynamicPermissionClient.java`(:73-76 check) · `services/auth-service/.../EffectivePermissionMaterializer.java`(:48-121 union+override) · `AccountPermissionService.java`(:53-61 check) · `web/PermissionInternalController.java`(:61-81) · `db/migration/V43__seed_role_groups.sql`(:11-78 그룹 시드+role 템플릿 매핑)
- 마이그/타입: `groupware db/migration` 최신 V7 · `slip` 최신 V48 · `V44__add_slip_collab_tables.sql`(:1-17 loose-ref, :68-71 timestamptz 교훈) · `Slip.java`(:178-190 dispatcher/inspector VARCHAR(50), :932/971 자동채움 — E12/A4)
