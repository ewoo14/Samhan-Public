# 시나리오 8 — slip-it nightly workflow plan (회귀 검증 분리)

> **목적**: PR #99 (W10-4) 의 6/7차 fix 회고 채택 — `slip-it-public + slip-it-core` IT group 이 GitHub Actions PR matrix (timeout 60분) 안에 못 끝나 cancel 되는 문제 회피용 nightly 분리 workflow plan
> **선행 PR**: #99 (`feature/integrated-phase-10-step-4-slip-signature-integration`, 머지 commit `3cc1e6d`) → 7차 fix `2509b1c` (옵션 B 채택, slip-it-public + slip-it-core PR matrix 제거)
> **본 plan 산출**: Phase 10 W10-5 본 PR (`feature/integrated-phase-10-step-5-retrospective`)
> **실 구현**: DevOps team 차후 PR (본 PR scope = plan docs only)

---

## 0. 회고 배경

### 0-1. PR #99 의 6/7차 fix 회고

| 차수 | 시도 | 결과 |
|---|---|---|
| 1차 | `slip-service` 단독 group 분리 (다른 service 와 분리) | 30분 timeout |
| 2차 | timeout 30 → 60분 | 60분 timeout (cancel) |
| 3차 | `slip-service` group 1 → 3 분할 (slip-units / slip-it-public / slip-it-core) | slip-it-public + slip-it-core 60분 timeout 회피 시도 |
| 4차 | timeout 30 → 60분 (slip-it-public + slip-it-core) | 여전히 timeout cancel |
| 5차 | Spring Context 통일 + Gradle daemon + setup-gradle v4 | 5차 fix 유해 — test 1건도 미완료 → 6차 revert |
| 6차 | revert (Spring Context 통일 → 4차 상태로 복원) | 4차 상태 + slip-it-public + slip-it-core 여전히 timeout |
| **7차 (옵션 B)** | **slip-it-public + slip-it-core matrix entry 제거** (PR matrix 에서 분리) | PR matrix CI green, **회귀 검증은 nightly workflow 또는 main merge trigger 로 분리** |

### 0-2. 옵션 B 채택 사유

GitHub Actions ubuntu-latest (2-core / 7GB RAM) 환경에서 **ApplicationContext 시작 자체가 60분 timeout 안에 못 끝나** 5/6차 모두 cancel ("Found and parsed 0 test report files" log).

→ slip-service IT 의 도메인 복잡도 (V1~V11 = 11 Flyway migration + signatureSource enum + driverPhone batch + 11 status 라이프사이클 + Internal API 7건 + admin / public / driver-app 3 channel + LINK/APP signature 직교 컨셉) 가 PR 단계에서 회귀 검증 부적절.

**Phase 10 W10-5 회고 backlog (본 시나리오 plan)**: nightly workflow 신규 발행 → main + feature branch 모두 회귀 검증.

---

## 1. nightly workflow 사양

### 1-1. 기본 동작

| 항목 | 값 |
|---|---|
| Workflow 이름 | `slip-it-nightly.yml` |
| Trigger | cron `0 17 * * *` UTC = `02:00 KST` 매일 |
| 추가 trigger | `workflow_dispatch` (수동 실행) + `push` to main (머지 후 즉시 1회 검증) |
| Runner | ubuntu-latest |
| Job | 2 group 병렬 |
| timeout | 60분 (group 별 — Phase 10 W10-4 시점 60분도 부족했지만 nightly = race condition 없음으로 충분 가정, fail 시 90분으로 점진 확대) |
| Branch matrix | `main` + 활성 `feature/integrated-phase-*` branch (옵션) |

### 1-2. 2 group 분할

| group | test pattern | 의미 |
|---|---|---|
| **slip-it-public** | `:services:slip-service:test --tests "com.samhanair.logis.slip.controller.public.*"` | 공개 endpoint (모바일 서명 / 거래처 link 수령 / SMS callback 등) IT |
| **slip-it-core** | `:services:slip-service:test --tests "com.samhanair.logis.slip.controller.internal.*" --tests "com.samhanair.logis.slip.controller.admin.*" --tests "com.samhanair.logis.slip.delivery.controller.*"` | Internal API + Admin + Delivery batch IT |

### 1-3. CI 환경변수

```yaml
env:
  SAMHAN_INTERNAL_TOKEN: dev-internal-token-change-me
  INTERNAL_AUTH_TOKEN: dev-internal-token-change-me
  SAMHAN_SLIP_SEED_TEST_DATA: false  # nightly = seeder OFF (IT 자체 fixture 사용)
  TESTCONTAINERS_REUSE_ENABLE: true   # Docker 재사용으로 startup 시간 절약
```

---

## 2. 신규 workflow YAML draft

> **본 PR scope** = plan docs only. 실제 `.github/workflows/slip-it-nightly.yml` 발행 = DevOps team 차후 PR (W10-5 backlog 위임).

```yaml
name: slip-it nightly (회귀 검증 분리)

on:
  schedule:
    - cron: '0 17 * * *'   # 02:00 KST = 17:00 UTC
  workflow_dispatch:        # 수동 실행 가능
  push:
    branches: [main]        # main 머지 직후 1회 검증

permissions:
  contents: read
  checks: write
  pull-requests: write
  issues: write             # fail 시 자동 Issue 생성

jobs:
  slip-it-nightly:
    name: slip-it nightly (${{ matrix.group.name }})
    runs-on: ubuntu-latest
    timeout-minutes: ${{ matrix.group.timeout }}
    strategy:
      fail-fast: false
      matrix:
        group:
          - name: slip-it-public
            timeout: 60
            test-tasks: ':services:slip-service:test --tests "com.samhanair.logis.slip.controller.public.*"'
          - name: slip-it-core
            timeout: 60
            test-tasks: ':services:slip-service:test --tests "com.samhanair.logis.slip.controller.internal.*" --tests "com.samhanair.logis.slip.controller.admin.*" --tests "com.samhanair.logis.slip.delivery.controller.*"'

    steps:
      - name: 저장소 체크아웃
        uses: actions/checkout@v4

      - name: JDK 17 설치 (Temurin)
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Gradle 캐시 + Wrapper 검증
        uses: gradle/actions/setup-gradle@v4

      - name: gradlew 실행 권한 보장
        run: chmod +x ./gradlew

      - name: Docker 가용성 확인 (Testcontainers 용)
        run: |
          docker version
          docker ps

      - name: assemble (slip-service)
        run: ./gradlew :services:slip-service:assemble

      - name: test (${{ matrix.group.name }})
        run: ./gradlew ${{ matrix.group.test-tasks }}
        env:
          SAMHAN_INTERNAL_TOKEN: dev-internal-token-change-me
          INTERNAL_AUTH_TOKEN: dev-internal-token-change-me
          SAMHAN_SLIP_SEED_TEST_DATA: false
          TESTCONTAINERS_REUSE_ENABLE: true

      - name: 테스트 리포트 아티팩트 업로드 (실패 포함)
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: nightly-test-reports-${{ matrix.group.name }}
          path: |
            services/slip-service/build/reports/tests/test/
            services/slip-service/build/test-results/test/

      - name: 테스트 결과 PR 코멘트 게시 (JUnit)
        if: always()
        uses: mikepenz/action-junit-report@v4
        with:
          report_paths: 'services/slip-service/build/test-results/test/*.xml'
          check_name: 'slip-it nightly (${{ matrix.group.name }})'

  notify-on-failure:
    name: nightly fail 시 자동 Issue 생성
    needs: slip-it-nightly
    if: failure()
    runs-on: ubuntu-latest
    steps:
      - name: GitHub Issue 자동 발행
        uses: actions/github-script@v7
        with:
          script: |
            const today = new Date().toISOString().slice(0, 10);
            const runUrl = `https://github.com/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`;
            await github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: `[slip-it nightly] ${today} fail`,
              body: `nightly slip-it 회귀 검증 fail 발생.\n\n- run: ${runUrl}\n- branch: ${context.ref}\n- commit: ${context.sha}\n\n@PM agent 확인 의무.`,
              labels: ['ci', 'nightly-fail', 'slip-service']
            });

      - name: Slack 알림 (옵션)
        if: env.SLACK_WEBHOOK_URL != ''
        env:
          SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK_URL }}
        run: |
          curl -X POST -H 'Content-type: application/json' \
            --data "{\"text\":\":rotating_light: slip-it nightly fail — ${{ github.run_id }}\"}" \
            $SLACK_WEBHOOK_URL
```

---

## 3. 회귀 검증 항목 매트릭스

### 3-1. slip-it-public (공개 endpoint)

| Test class | 검증 영역 | LOC 추정 |
|---|---|---|
| `SlipPublicControllerIT` | 모바일 서명 endpoint (LINK source) | ~150 |
| `SlipMobileSignatureControllerIT` | 모바일 canvas 캡처 + signatureChannel = MOBILE_CANVAS | ~180 |
| `SlipLinkAccessControllerIT` | 거래처 link 수령 (Aligo SMS callback) + 만료 검증 | ~120 |
| `SmsCallbackControllerIT` | Aligo SMS 응답 (`result_code=1`) 처리 | ~80 |
| 신규 (W10-4) | LINK source signature audit (전자서명법 §17) | ~100 |

### 3-2. slip-it-core (Internal + Admin + Delivery)

| Test class | 검증 영역 | LOC 추정 |
|---|---|---|
| `SlipInternalControllerIT` | partner-order-service / arologis-service 호출 (X-Internal-Token + ROLE_MASTER) | ~250 |
| `SignatureIntegrationIT` (W10-4 신규) | arologis driver-app sign → slip /internal/signatures (APP source 통합) | ~200 |
| `SlipByPartnerCodeIT` (W10-4 신규) | `/internal/slips/by-partner-code/{code}` 3 case (정상 / 빈 / 일부 미존재) | ~120 |
| `SlipAdminControllerIT` | 11 status 라이프사이클 (DRAFT → SAVED → SENT → ... → CONFIRMED → REVERSED) | ~300 |
| `DeliveryBatchControllerIT` | driverPhone 묶음 batch (5건 그룹 + APP/LINK 직교 분포) | ~200 |
| `JournalIntegrationIT` | CONFIRMED 시점 자동 분개 → accounting-service `/internal/journals/from-slip` | ~150 |

---

## 4. main + feature branch 모두 검증 정책

### 4-1. main (push trigger)

main 머지 직후 1회 자동 실행 — 머지 PR 의 회귀 효과 검증. 결과를 GitHub Actions main branch tab 에 표시.

### 4-2. nightly cron (모든 활성 branch)

매일 02:00 KST 1회 실행:
- `main` branch 검증 의무
- 활성 `feature/integrated-phase-*` branch (옵션, dispatch input 으로 선택)
- fail 시 GitHub Issue 자동 생성 (label: `ci`, `nightly-fail`, `slip-service`)

### 4-3. PR 단계 (제외)

PR matrix entry 에서 `slip-it-public + slip-it-core` 제거 (옵션 B 적용 후) — PR 시점 회귀 검증은 `slip-units` group 한정 (단위 + 빠른 client mock).

→ PR 머지 후 nightly + main push 2 trigger 로 회귀 검증 보장.

---

## 5. fail 시 처리 절차

### 5-1. GitHub Issue 자동 생성

```markdown
[slip-it nightly] 2026-05-10 fail

nightly slip-it 회귀 검증 fail 발생.

- run: https://github.com/ewoo14/SamhanLogis/actions/runs/12345678
- branch: refs/heads/main
- commit: <sha>

@PM agent 확인 의무.
```

label: `ci` + `nightly-fail` + `slip-service`

### 5-2. Slack 알림 (옵션)

`SLACK_WEBHOOK_URL` secret 설정 시 Slack 채널 자동 알림:

```
:rotating_light: slip-it nightly fail — <run-id>
```

### 5-3. PM agent 후속 처리

1. PM agent 가 자동 생성 Issue 확인
2. 5-team agent 병렬 디스패치 (`feedback_pr_review_workflow.md` 일관 적용)
3. 회귀 fix 통합 PR 발행
4. 본 nightly 시나리오 재실행 (`workflow_dispatch`) 으로 fix 검증

---

## 6. PR #102 회귀 검증 약속

> PR #102 (`fix/slip-v11-concurrently-deadlock`, 머지 commit `e6ac6dc`) = V11 CONCURRENTLY 제거 hotfix.
> Flyway 의 single-transaction 가정과 CONCURRENTLY 의 non-transactional 충돌로 idle in transaction deadlock 발생.

본 nightly 시나리오 발행 후 약속:
- nightly 첫 실행 (V11 fix 머지 후 첫 02:00 KST) 에서 `slip-it-public` + `slip-it-core` 모두 PASS 검증
- V11 fix 후에도 11 Flyway migration baseline 정상 적용 검증 (Flyway history 테이블 확인)
- 회귀 0 검증 = 11 status 라이프사이클 모두 PASS + signatureSource backfill 정상

→ PR #102 회귀 검증 = 본 nightly 의 핵심 첫 시나리오.

---

## 7. nightly 의 한계 + 차후 보강

### 7-1. 한계

| 한계 | 영향 | 보강 plan |
|---|---|---|
| GitHub Actions ubuntu-latest 2-core / 7GB RAM 한계 | ApplicationContext 시작 자체가 felt-time 30~60분 | self-hosted runner 도입 (Phase 11 P11-3 진입 시점, AWS EC2 m5.xlarge 등) |
| nightly = 시점 고정 (02:00 KST) | 머지 직후 즉시 검증 X (push trigger 보강) | push trigger 추가 (본 plan 적용) |
| fail 발생 시 후속 자동화 부족 | PM agent 수동 디스패치 필요 | `workflow_dispatch` 자동 retry 또는 PM agent webhook trigger 도입 |
| Docker layer caching 미적용 | Testcontainers postgres image pull 시간 추가 | `actions/cache` + Docker layer 재사용 |

### 7-2. Phase 11 진입 시 보강 plan

| 보강 항목 | Phase 11 P11-? | 비고 |
|---|---|---|
| self-hosted runner (AWS EC2 m5.xlarge) | P11-3 (Production cutover 직후) | nightly 시간 단축 + RAM 확장 |
| Testcontainers reusable container 일관 적용 | P11-2 (Discovery + Resilience) | startup 시간 절약 |
| nightly fail 시 PM agent webhook trigger | P11-3 (Production cutover) | Slack + GitHub Issue + PM agent 자동 호출 |
| nightly cron schedule 매트릭스 (02:00 / 14:00 KST 2회) | P11-3 (Production cutover) | 머지 후 12시간 이내 회귀 검증 보장 |

---

## 8. 본 plan 의 발행 시점 + 책임 분담

### 8-1. W10-5 본 PR scope

| 항목 | 본 PR scope | 비고 |
|---|---|---|
| 본 plan docs (`scenarios/08-nightly-slip-it.md`) | ✅ qa-tester 본 PR | 본 문서 |
| nightly workflow YAML 발행 (`.github/workflows/slip-it-nightly.yml`) | ⏳ devops-engineer 차후 PR | W10-5 backlog 위임 |
| GitHub Issue label (`ci` / `nightly-fail` / `slip-service`) 발급 | ⏳ devops-engineer 차후 PR | label 신규 발급 의무 |
| Slack webhook secret 설정 (`SLACK_WEBHOOK_URL`) | ⏳ 사용자 (개발책임자) | 옵션 |

### 8-2. 발행 후 첫 회귀 검증 약속 (qa-tester)

본 plan + nightly workflow 발행 머지 후 첫 02:00 KST 실행 시점:
- `slip-it-public` PASS 검증 (qa-tester 결과 확인)
- `slip-it-core` PASS 검증 (qa-tester 결과 확인)
- fail 시 → 자동 생성 Issue → 5-team 디스패치

→ 첫 회귀 검증 PASS 확인 후 본 plan 의 § 6 PR #102 회귀 검증 약속 완료 표기.

---

## 9. 진입 / 종료 기준

### 9-1. 진입 기준 (DoR)

- [ ] PR #99 머지 완료 (`3cc1e6d`)
- [ ] PR #102 V11 hotfix 머지 완료 (`e6ac6dc`)
- [ ] 본 plan docs 머지 완료 (W10-5 본 PR)
- [ ] DevOps team 의 `slip-it-nightly.yml` 발행 PR 머지 완료 (차후)

### 9-2. 종료 기준 (DoD)

- [ ] 첫 nightly 실행 결과 PASS (slip-it-public + slip-it-core 모두)
- [ ] PR #102 회귀 검증 PASS (V11 fix 후 11 Flyway migration baseline 정상)
- [ ] fail 시 자동 Issue 생성 + 5-team 디스패치 patten 정착 (1회 사이클 검증)
- [ ] dev-report 누적 (`docs/dev-reports/phase-10-retrospective.md` § 5-2 nightly plan 항목 완료 표기)

---

## 10. 관련 문서

- `docs/dev-reports/phase-10-retrospective.md` — Phase 10 종합 회고 (본 PR 신규)
- `docs/qa/local-test-seed-data/retrospective.md` — 로컬 4 issue 회고 (본 PR 신규)
- `docs/qa/local-test-seed-data/verification-report-2026-05-09.md` — 검증 결과 보고 (본 PR 신규)
- `.github/workflows/ci.yml` — 현행 PR matrix (옵션 B 채택, slip-it-public + slip-it-core 제거)
- `services/slip-service/README.md` — slip-service 도메인 + V1~V11 Flyway 매트릭스
- memory `feedback_pr_review_workflow.md` — 5-team agent → TM → CI → PM → 사용자 머지
- memory `feedback_pr_ci_monitoring.md` — PR 발행 후 PM 자동 CI 모니터링 가드
