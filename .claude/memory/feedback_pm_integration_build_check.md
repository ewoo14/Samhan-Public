---
name: PM 통합 시 풀빌드 사전 검증 의무 (4-team parallel 통합 가드)
description: 4-team 병렬 디스패치 후 팀별 PR 발행 전, PM 은 BE+QA 합쳐서 ./gradlew test 풀빌드를 반드시 사전 실행. BE 의도적 변경이 QA IT 와 어긋나는 케이스 방어
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: PM 이 4-team parallel 디스패치 결과를 팀별 PR 로 발행하기 전, **반드시 다음 사전 검증을 수행**:

1. **BE worktree → 임시 통합 브랜치** (또는 일시적 main checkout) 으로 BE 코드 배치
2. **QA worktree 의 IT 도 같은 위치에 배치**
3. **`./gradlew :services:<svc>:compileTestJava` 사전 실행** — IT compile PASS 확인
4. (Docker 가용 시) **`./gradlew :services:<svc>:test` IT 실행** — 실패 시 가드
5. PASS 확인 후 팀별 PR 발행

**Why**: 2026-05-04 Phase 2 Product Service 첫 슬라이스에서 사고 발생.
- Plan §3.4: `ProductRepository.findByTagsContaining(String)` 명세
- QA agent: Plan 그대로 IT 작성 → `findByTagsContaining` 호출
- BE agent: **의도적 변경** — 모든 search 필터를 단일 native @Query 로 합쳐 `search(categoryId, status, q, tagFilter, Pageable)` 하나로 통합. 별도 `findByTagsContaining` 메서드 미생성
- PM 이 팀별 PR 발행 시 BE+QA 사전 컴파일 검증 안 함
- 4 PR 모두 발행됨 → BE/FE/DevOps 머지 후 QA PR 의 CI 가 첫 머지 시뮬레이션에서 컴파일 실패 (`cannot find symbol method findByTagsContaining(String)`)
- hotfix commit 으로 IT 를 `search(null, null, null, "{...}", PageRequest.of(0, 100))` 호출로 수정

**근본 원인**: 4-team parallel 패턴은 협업이 **artifact handoff** 만으로 일어남 (실시간 채팅 불가). BE 의 의도적 변경은 BE 보고서에는 명시되지만 **QA agent 가 BE 보고서를 읽지 않음** (BE 이전에 동시 디스패치되어 또는 자체 worktree 만 봄). 따라서 BE-QA contract drift 가 발생할 수 있고, **PM 이 통합 시점에 풀빌드로 catch 해야 함**.

**적용 절차** (모든 4-team 슬라이스):
```bash
# 1. main 또는 PM 통합 브랜치 만들기
git checkout -b __pm_integration_check__ main

# 2. BE worktree 복사 (예: services/product-service/src/main 등)
cp -r .claude/worktrees/<be-id>/services/<svc> services/

# 3. QA worktree 복사 (예: services/<svc>/src/test/...it/ 만)
cp -r .claude/worktrees/<qa-id>/services/<svc>/src/test/java/.../it services/<svc>/src/test/java/.../

# 4. settings.gradle / 루트 build.gradle leafProjects 에 모듈 임시 등록
# (PR 발행 전 롤백 또는 BE PR 에 포함)

# 5. 풀빌드 + IT 런타임 부팅 검증 (Docker 가용 시)
./gradlew.bat :services:<svc>:compileTestJava :services:<svc>:test --no-daemon

# 6. PASS 확인 후 임시 브랜치 삭제 + 정상 팀별 PR 발행 진행
```

**검증 단계 의무 (3 layer)**:
1. **컴파일 검증** (`compileTestJava`) — BE 시그니처와 QA IT import 일치
2. **IT 런타임 부팅 검증** (`test` + **Docker 가동 필수**) — Spring context 부팅 + Hibernate `ddl-auto=validate` schema↔entity 매핑 + **Mockito stub 시그니처 검증** + **BusinessException 가드 분기 (NOT_FOUND vs CONFLICT) assertion 검증**. Docker 미가용 환경에선 IT skip 되어 본 layer 가 무력화됨 → CI 에서야 fail 발견
3. **시나리오 시연** (옵션) — fixtures.http 또는 curl 로 happy path 1건 실행

**예외 없음**: BE/QA 가 동시 진행되는 모든 슬라이스에 적용. FE/DevOps 는 BE 코드 의존 없으면 사전 검증 skip 가능.

**Docker 미가용 환경 대응**: PM 환경에 Docker 가 없으면 **GitHub Actions CI 의 첫 가동 결과를 PR 머지 전에 반드시 확인**. 본 단계 skip 하고 머지하면 main 이 broken 상태로 들어감. 본 메모리의 Layer 2 가 PM 환경에서 강제될 수 없을 때는 CI 결과가 사실상 Layer 2 역할 (단, 머지 전에 hotfix 가능).

**과거 위반 사례 누적**:
- **PR #13** (Team-Product QA, 2026-05-04): BE 의 의도적 변경 (`findByTagsContaining` → `search` 통합) 이 QA IT 와 mismatch → CI 컴파일 fail → hotfix commit `26e793e`
- **PR #15** (Product hotfix, 2026-05-04): `Product.currency CHAR(3) → bpchar` vs Hibernate `VARCHAR` mismatch → IT runtime boot fail. Layer 2 가 사전에 catch 했어야 함
- **PR #16** (Inventory 첫 슬라이스, 2026-05-04): 두 개의 사고가 PM 환경에서 catch 안 되고 GitHub Actions CI 에서야 발견 (Docker 미가용으로 IT skip):
  1. `ProductClient.requireExists(UUID)` 가 ProductSummary 반환 (void 아님) 인데 `Mockito.doNothing()` 사용 → IT 6건 fail. hotfix `008946d` 로 `when().thenAnswer()` 정정
  2. `StockService.deduct` 의 BE 가드 시맨틱 — balance 없으면 NOT_FOUND(404), lot 합계 부족이면 CONFLICT(409). IT 가 빈 productId 로 deduct → 404 반환되어 isConflict assertion 실패. hotfix `193fd2d` 로 inbound 선행 후 deduct 시도하도록 정정
  - **교훈**: Mockito API 오용 + BusinessException 가드 분기 정렬은 **컴파일에서 안 잡힘**. Docker 가용 IT 실행만이 catch 수단
- **PR #17** (Slip 첫 슬라이스, 2026-05-04): PM 통합 사전 검증으로 컴파일 mismatch 2건은 사전 catch 했으나 (SlipNumberService.next 반환 String + InventoryClient 5/6 인자) **IT runtime 사고 12건 CI 에서 fail**. hotfix `0f66873` 로 일괄 정정:
  1. SlipControllerIT + SlipLifecycleControllerIT 가 InventoryClient 만 `@MockBean` 등록, **ProductClient 누락** → SlipService.create 의 lookup 이 실제 product-service RestClient 호출 → DiscoveryClient 비활성 → 500 (10건 fail)
  2. SlipDomainIT.newDraftInbound() 가 DAY (OUTBOUND 전용 태그) 사용 → BE 의 createInbound 가 `IllegalArgumentException` 거부
  3. SlipDomainIT.applyDeliveryTagAutoMemo 가 yyyy 포함 가정, BE 는 `[야적] MM/dd 상차 MM/dd 하차` (MM/dd 만)
  - **교훈**: PM 환경 Docker daemon 켠 후 IT 사전 실행 의무화. Docker Desktop 정상 동작 확인됨 (29.4.0, 30GB) → 다음 슬라이스부터 PM 통합 단계에 `docker compose up -d` + `./gradlew :services:<svc>:test` 풀 IT 실행 강제. 신규 메모리 `feedback_it_mockbean_external_clients.md` 와 짝

## Docker 가용 환경에서 PM 통합 절차 (강화 — PR #17 회고 후 2026-05-04 추가)

```bash
# 1. Docker daemon 확인 (Windows: Docker Desktop, Linux: systemctl)
docker info | grep "Server Version"

# 2. 통합 worktree 진입 + 4 worktree 머지
cd .claude/worktrees/<be-id>
# (FE/QA/DevOps 신규 파일 복사)

# 3. 풀빌드 + 컴파일 검증
./gradlew assemble :services:<svc>:compileTestJava

# 4. *** 신규 의무 *** Docker 가용 IT 실행 — PR 발행 전 100% 통과 필수
./gradlew :services:<svc>:test --no-daemon
# 모든 IT (Repository/Controller/Lifecycle 등) PASS 확인
# fail 1건이라도 있으면 PR 발행 금지, 수정 후 재실행

# 5. fixtures.http 시연 (시간 허락 시) — docker-compose 풀스택 부팅 후 1~2 시나리오 curl

# 6. PR 발행 진행
```

**관련 메모리**: `feedback_multi_agent_team_pattern.md` (4-team 패턴 본문), `feedback_it_mockbean_external_clients.md` (외부 RestClient @MockBean 의무).

## Layer 5 (신규) — Hibernate schema validation 매핑 검증 (PR #23 회고 후 2026-05-05 추가)

**규칙**: 신규 entity 필드 추가 시 다음 두 가지 매핑 케이스를 PM 통합 단계 사전 검증.

### 5.1 `@Column(unique = true)` vs partial UNIQUE INDEX
- entity 에 `unique=true` 인라인 지정 + DB 마이그레이션이 **partial UNIQUE INDEX** (`WHERE col IS NOT NULL`) 인 경우 → Hibernate `validate` 가 full UNIQUE constraint 를 기대하나 partial index 는 매칭되지 않아 **SchemaManagementException** 발생
- 해결: `unique=true` 제거 + Javadoc 으로 partial INDEX 의도 명시 (DB 가 유일성 강제)
- 사례: PR #23 `Slip.signatureShareToken` (commit `d5622b7` hotfix)

### 5.2 `@Lob byte[]` vs PostgreSQL BYTEA
- Hibernate 6 + PostgreSQL 에서 `@Lob byte[]` 는 `oid` (large object) 매핑 default
- DB 마이그레이션이 `BYTEA` 컬럼이면 mismatch → **SchemaManagementException**
- 해결: `@Lob` 제거 (byte[] 가 자동으로 `BYTEA` 매핑됨)
- 옵션: `@JdbcTypeCode(Types.VARBINARY)` 명시
- 사례: PR #23 `Slip.signaturePng` / `driverSignaturePng` (commit `e2ee84c` hotfix)

### 5.3 PM 통합 사전 검증 추가 항목
신규 BYTEA / partial UNIQUE INDEX 필드 도입 시:
1. entity `@Column` 어노테이션 검토 — `unique=true` 또는 `@Lob` 사용 여부
2. DB 마이그레이션 SQL 검토 — `BYTEA` / `WHERE ... IS NOT NULL` 패턴
3. 충돌 시 entity 측 어노테이션 수정 (DB 마이그레이션 변경 X — 의도된 partial/BYTEA 유지)

**과거 위반 사례 누적** (Layer 5):
- **PR #23** (signature-slice-C, 2026-05-05): `unique=true` partial mismatch (74 IT fail) + `@Lob byte[]` BYTEA mismatch (다시 74 IT fail). Docker 미가용 PM 환경에서 사전 검증 안 됨 → CI 에서 두 차례 발견. 두 hotfix 모두 entity 어노테이션 1줄 수정으로 해결.

## Layer 4 (신규) — 도메인 메서드 의미 정렬 검증 (PR #21 회고 후 2026-05-04 추가)

**규칙**: 다단계 라이프사이클 (예: SlipStatus 10단계, TransferStatus 9단계 등) 신규 슬라이스에서 BE 가 도메인 메서드 (`accept()` / `complete()` / `inspect()` / `confirm()` 등) 의 status 매핑을 결정할 때, **PM 은 사용자가 평어로 표현한 의미와 BE 메서드 시맨틱이 일치하는지 별도 검증**해야 한다.

**Why**: 2026-05-04 PR #21 (Slip INSPECTING 단계 추가 슬라이스) 에서 발생.
- Plan: "complete = 출고 완료 → INSPECTING 진입, inspect = 검수 완료 → COMPLETED" (사용자 의도)
- BE 구현: `complete()` 가 `INSPECTING → COMPLETED` 로 작성되어 **반대로 매핑** (이름과 시맨틱이 거꾸로). `inspect()` 가 `PROCESSING → INSPECTING` 으로 작성됨
- QA IT: 사용자 의도 그대로 (`SlipInspectControllerIT.complete_transitionsToInspecting`)
- PM 통합 사전 검증: 컴파일 PASS / IT skip (Docker 미가용) → fail 4건 GitHub Actions CI 에서 발견
- 사용자 회신: "재발방지 요청" — 강한 어조

**근본 원인**: BE agent 가 plan 의 평어 ("complete = 출고 완료") 를 status enum 으로 매핑할 때, "complete = 끝 = COMPLETED" 라는 단순 직관에 의존. INSPECTING 라는 신규 중간 단계가 도입되면 메서드 이름과 의미가 어긋날 수 있음. QA agent 는 plan 의 jsonPath assertion 을 그대로 따라가지만, BE-QA contract drift 는 이름이 같아도 매핑이 다르면 발생.

**적용 절차**:
1. **Plan 작성 시 PM 의무**: 라이프사이클 단계가 4 이상 (또는 신규 단계 도입) 인 슬라이스에선 plan 본문에 다음 표를 강제 포함:
   ```
   | 도메인 메서드 | from status | to status | 부수 효과 |
   | ------------- | ----------- | --------- | --------- |
   | accept()      | SENT        | ACCEPTED  | dispatcherUserId 자동 |
   | complete()    | PROCESSING  | INSPECTING | (출고 완료 → 검수 진입) |
   | inspect()     | INSPECTING  | COMPLETED | inspectorUserId 자동, completedAt |
   ```
   사용자 평어 의미를 from/to status 로 명시 → BE/QA 모두 동일 행 참조
2. **BE 보고서 (`docs/dev-reports/<slice>.md`) 의무 항목**: Plan 표와 1:1 매핑된 BE 구현 표 — 다르면 즉시 PM 알림
3. **PM 통합 단계 (Layer 1~3 추가)**: BE 보고서 표를 plan 표와 diff 후 진행

**검증 단계 (단위 테스트로 catch 가능)**:
- BE 단위 테스트 (`SlipDomainTest`) 에서 의도된 시맨틱이 메서드 이름과 일치하는지 명시적으로 assert. 예: `complete_outbound_movesToInspectingAndDeducts` (이름에 to status 포함)
- 단위 테스트 이름이 plan 표의 to status 와 다르면 즉시 의심

**Docker 미가용 환경에서 catch 가능한가?**: YES — 본 layer 는 Layer 2 (IT runtime) 와 독립적. 단위 테스트 이름 + plan 표 diff 로 PM 환경에서도 사전 catch 가능.

**과거 위반 사례 누적** (Layer 4):
- **PR #21** (Slip INSPECTING 슬라이스, 2026-05-04): `complete()` / `inspect()` status 매핑 반대 → CI fail 4건. hotfix `e7c2733` 로 swap. 사용자 "재발방지 요청".

**Slack/카톡/이메일 등 별도 알림 채널 없음**: 본 메모리가 유일한 가드. PM 이 모든 슬라이스 plan 작성 시 라이프사이클 표 포함 의무.

## 관련 가드

- PM 통합 검증 시 stale main 회귀 가드 (`feedback_agent_origin_main_sync.md`) — PM 통합 worktree 진입 직후 `git fetch origin` + `git log origin/main` 으로 base branch stale 여부 확인. stale 일 경우 BE/QA 사전 컴파일 검증이 잘못된 base 위에서 PASS 할 수 있음 (3건 background agent 회고)
