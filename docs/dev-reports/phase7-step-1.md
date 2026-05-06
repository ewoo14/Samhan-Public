# Phase 7 1차 작업 — dev report

Phase 6 마무리 (PR #80 머지) 완료 직후 Phase 7 진입 1차 작업 통합 산출.

## 1. 작업 범위

| # | 항목 | 산출 |
|---|---|---|
| 1 | 카페24 SSH 연결 검증 script | `infrastructure/cafe24/test-ssh-connection.sh` (배포 X, dry-run only) |
| 2 | estimate-app v2 Render Blueprint | `infrastructure/render/render.yaml` + `README.md` + `deploy-checklist.md` |
| 3 | `deploy-estimate-app.yml` 활성화 | `.template` suffix 제거 + Render auto-deploy 분담 |
| 4 | QA 시나리오 30 → 60 cell 확장 | `tests/{dc,stock,history,tutorial}/` 12 spec 추가 |
| 5 | qa-e2e workflow timeout | 30 → 60 분 |

## 2. 카페24 SSH 테스트 script

### 2.1 동작 범위

본 script 는 다음 항목만 검증한다 (실 배포 step 미포함):

| 단계 | 검증 항목 |
|---|---|
| 0 | 환경변수 (CAFE24_HOST / CAFE24_USER / CAFE24_SSH_KEY) + private key 권한 |
| 1 | SSH 인증 (whoami / hostname / uptime) |
| 2 | 자원 (nproc / free -m / df -h) |
| 3 | 도구 (docker / pm2 / nginx / node / npm / rsync / git) |
| 4 | pm2 process 목록 (기존 운영 service 충돌 사전 검증) |
| 5 | /home 디스크 사용량 (배포 가용 디스크 확인) |

### 2.2 가드

- **CI 자동 실행 X** — 개발자 머신에서 수동 실행한다.
- **Secrets 노출 X** — private key 내용을 stdout 출력하지 않는다.
- **실 배포 X** — rsync / pm2 reload / pm2 start 명령 미포함.
- `BatchMode=yes` + `PasswordAuthentication=no` + `PubkeyAuthentication=yes` — interactive prompt 없이 종료.

### 2.3 활성화 시점

다음 모든 조건이 충족된 후에만 `.github/workflows/deploy-cafe24-ssh.yml.template` 의 `.template` suffix 를 제거한다:

| 조건 | 답변 항목 |
|---|---|
| D6 | 배포 대상 앱 (estimate-app v2 / order-app 정적 / 둘 다) |
| D7 | 카페24 호스트 내 배포 디렉토리 (`/home/samhan/apps/<name>`) |
| D8 | pm2 process 명명 규약 (`samhan-<app>`) |

본 단계는 **테스트만 진행**이며 위 답변과 활성 결정 전까지 workflow 비활성 유지한다.

## 3. Render Blueprint

### 3.1 정의 service 2개

| service | type | plan | region | autoDeploy |
|---|---|---|---|---|
| `samhan-estimate-app` | web (Node.js) | starter ($7/mo, 512MB) | singapore | true |
| `samhan-order-app` | static | free | (CDN) | false (mirror only) |

`samhan-order-app` 은 현재 Cloudflare Pages (PR #77) 가 production owner 이므로
Blueprint 정의에는 mirror 후보로 보존하되 autoDeploy 비활성으로 충돌 회피.

### 3.2 환경변수 (sync: false placeholder)

| Key | 등록 시점 |
|---|---|
| `SAMHAN_BACKEND_BASE_URL` | 14 backend MSA 호스팅 결정 후 |
| `PARTNER_AUTH_SERVICE_URL` | 동상 |
| `PARTNER_ORDER_SERVICE_URL` | 동상 |
| `SLIP_SERVICE_URL` | 동상 |
| `PRODUCT_SERVICE_URL` | 동상 |
| `DC_CONFIG_SERVICE_URL` | 동상 |
| `GOOGLE_SERVICE_ACCOUNT_KEY` | Google Cloud Console 발급 |
| `GOOGLE_SHEETS_SPREADSHEET_ID` | legacy 견적 spreadsheet URL |

모든 secret 은 Render dashboard 에서 직접 등록하며, 본 저장소에는 placeholder 만 보관한다.

### 3.3 yaml 구조 검증

- `services[]` 2개 정의 + 각 service 의 `type` / `runtime` / `repo` / `branch` / `rootDir` /
  `buildCommand` / `startCommand` (web) / `staticPublishPath` (static) 모두 명시.
- `healthCheckPath: /healthz` — `clients/web/estimate-app/routes/index.js` 의 `/healthz` endpoint 와 일치.
- `domains: [quote.samhan-air.com]` — DNS CNAME 등록 후 자동 SSL.

### 3.4 GitHub Actions 분담

- Render auto-deploy: main push 자동 감지 → 빌드 + 배포
- `.github/workflows/deploy-estimate-app.yml`: PR 시점 빌드 + 단위 테스트 + `node --check server.js`
  syntax 검증 게이트 (Render 측 trigger X — secret 노출 회피)

## 4. QA 시나리오 30 → 60 cell 확장

### 4.1 추가 spec 12개

| 카테고리 | spec | tests/spec |
|---|---|---|
| dc | dc-config-apply | 2 |
| dc | dc-rule-priority | 2 |
| dc | dc-snapshot-audit | 2 |
| stock | stock-reserve-on-confirm | 2 |
| stock | stock-deduct-on-slip-publish | 2 |
| stock | safety-stock-alert | 2 |
| history | slip-publish-history | 2 |
| history | stock-movement-history | 2 |
| history | draft-history | 2 |
| tutorial | tutorial-pc | 2 |
| tutorial | tutorial-mobile | 2 |
| tutorial | tutorial-staff | 2 |

### 4.2 카테고리별 spec 합계 (확장 후)

| 카테고리 | spec 수 | 비고 |
|---|---|---|
| auth | 3 | 변경 없음 |
| catalog | 4 | 변경 없음 |
| draft | 3 | 변경 없음 |
| confirm | 3 | 변경 없음 |
| history | 4 | 1 → 4 |
| tutorial | 4 | 1 → 4 |
| dc | 3 | 신규 |
| stock | 3 | 신규 |
| **합계 spec** | **27** | (기존 15 + 12 신규) |

### 4.3 project × 카테고리 매트릭스 (testMatch)

| project | 매칭 카테고리 | spec 수 | tests |
|---|---|---|---|
| web-order-app | auth+catalog+draft+confirm+history+tutorial+dc+stock | 27 | 54 |
| web-estimate-app | auth+catalog+draft+confirm+history+dc | 20 | 40 |
| electron-desktop | auth+catalog+confirm+stock | 13 | 26 |
| mobile-chrome | auth+catalog+draft+confirm+tutorial | 17 | 34 |
| mobile-safari | auth+catalog+draft+confirm+tutorial | 17 | 34 |
| **합계 spec×project** | — | **94** | **188** |

`npx playwright test --list` 실행 결과 188 tests 가 27 spec 으로 분포 확인 (typecheck PASS).

### 4.4 기존 30 → 60 cell 환산

기존 15 spec × 평균 2 project = 30 cell.
확장 후 27 spec × 평균 ~3.5 project = 94 spec×project 할당, 60 cell 목표 초과 달성.

## 5. workflow 변경 요약

| 파일 | 변경 |
|---|---|
| `.github/workflows/deploy-estimate-app.yml.template` → `.yml` | rename + Render auto-deploy 분담으로 재구성 |
| `.github/workflows/deploy-cafe24-ssh.yml.template` | 헤더 주석에 "테스트만 진행" 가드 명시 + test-ssh-connection.sh 참조 |
| `.github/workflows/qa-e2e.yml` | playwright job timeout 30 → 60 분 (cell 2배 확장 대응) |
| `qa/playwright/playwright.config.ts` | testMatch 에 `dc` / `stock` / `tutorial` 카테고리 정규식 추가 |

## 6. 후속 작업 (Phase 7 2차 ~)

| 항목 | 상세 | 의존 |
|---|---|---|
| Phase 7 backend staging 환경 구축 | 14 backend MSA 의 staging endpoint 5종 (M2/M3/M4/M5/product) | `M-PHASE-7-readiness.md` § 4 호스팅 결정 |
| 14 backend 별도 호스팅 결정 | X1 Hetzner / X2 카페24 업그레이드 / X3 Render / X4 AWS 중 1건 | 호스팅 결정 회의 |
| k6 부하 시험 (catalog / draft / slip) | k6 script + dashboard | backend staging |
| OWASP ZAP 보안 시험 | ZAP baseline scan 보고 | backend staging |
| Render production cutover | secret 등록 + DNS CNAME + smoke test | Render Blueprint 활성 + backend staging |
| 카페24 SSH 활성화 검토 | D6/D7/D8 답변 후 .template 제거 | SSH 검증 PASS |

## 7. 검증

- typecheck (qa/playwright): PASS (`npx tsc --noEmit` 무에러)
- spec 수 확인 (`npx playwright test --list`): 27 file / 188 tests / 5 project 분포 확인
- render.yaml syntax: services[] 2개 + 모든 필수 필드 명시 + healthCheckPath 가 `/healthz` 와 일치
- estimate-app server.js `/healthz` endpoint 가용 확인 (`routes/index.js:26`)
