# DEVOPS — GitHub Actions 자동 배포 workflow

## 범위

`.github/workflows/` 에 신규 deploy workflow 3개 추가:

| 파일 | 대상 | 상태 | 트리거 |
|---|---|---|---|
| `deploy-order-app.yml` | `clients/web/order-app` v4 (Vite SPA + PWA) | **활성** | `main` push + `paths: clients/web/order-app/**` + workflow_dispatch |
| `deploy-estimate-app.yml.template` | `clients/web/estimate-app` v2 (Express SSR) | **template (활성 X)** | workflow_dispatch (수동 검증만) |
| `deploy-cafe24-ssh.yml.template` | 카페24 SSH 대상 앱 (TBD) | **template (활성 X)** | workflow_dispatch (수동 검증만) |

기존 `ci.yml` (build + test 검증) 은 유지 — 본 작업과 분리.

## 1. order-app v4 → Cloudflare Pages

### 산출 구조

`clients/web/order-app/package.json`:
- `npm run typecheck` = `tsc -p tsconfig.json --noEmit`
- `npm run build` = `tsc --noEmit && vite build` → `dist/` 정적 산출

`vite.config.ts` 의 `build.outDir = 'dist'` + `vite-plugin-pwa` 로 PWA manifest + service worker 자동 포함.

### Workflow steps

1. checkout
2. Node 20 + npm cache (`clients/web/order-app/package-lock.json` 기준)
3. `npm ci`
4. `npm run typecheck` (PR 가드)
5. `npm run build` → `dist/`
6. `cloudflare/pages-action@v1` → projectName `samhan-order-app`, directory `dist`
7. Smoke test — `curl https://order.samhan-air.com/ | grep "주문서"` (legacy index.html title 매치)

### Concurrency

명시 X — Cloudflare Pages 자체가 deployment versioning 처리. 동시 push 시 Pages 가 마지막 deployment 를 production 으로 promote.

### 트리거 조건

- `main` push + `paths` 매치 (workflow 자체 변경 포함)
- `workflow_dispatch` 수동

PR 트리거 X — preview deploy 는 후속 슬라이스 (Cloudflare Pages preview environment 분리 필요).

## 2. estimate-app v2 — Cloudflare Pages 배포 불가 사유

`clients/web/estimate-app/package.json`:
- `"main": "server.js"` + `express` + `ejs` + `googleapis` 의존
- `"dev": "node server.js"` / `"start": "node server.js"`
- build/dist step 부재 — runtime Node.js process 필수

Cloudflare Pages 는 **정적 파일 호스팅 전용** (Pages Functions 는 Workers runtime, Express middleware 직접 미지원). 따라서 estimate-app v2 의 Cloudflare Pages 배포는 **기술적으로 불가**.

### 대안 후보 (호스팅 결정 대기)

1. **카페24 SSH (Node.js + pm2)** — 현재 1G RAM 에 samhan 공식 홈페이지 pm2 운영 중. estimate-app + Express 추가 시 메모리 검증 필요 (`--max-old-space-size=512` + `pm2 max-memory-restart 400M`).
2. **Cloudflare Workers** — Express → workerd 변환 필요 (`@cloudflare/workers-types` + adapter). EJS 렌더링 / `googleapis` 호환성 검토 필요.
3. **Hetzner / Render.com Node.js 인스턴스** — Phase 7 backend 호스팅과 동일 인프라 reuse 가능.

`deploy-estimate-app.yml.template` 에 빌드 검증 step + 후보 1) 의 SSH 배포 step 주석 포함 — 호스팅 결정 후 `.template` 제거 + 주석 해제로 활성.

## 3. 카페24 SSH workflow template

`deploy-cafe24-ssh.yml.template` — 활성 X. 다음 답변 (D6/D7/D8) 대기:

- D6: 배포 대상 앱 (estimate-app / 신규 백오피스 등)
- D7: 카페24 디렉토리 구조 (`/home/samhan/apps/<name>/`)
- D8: pm2 process 명명 규칙

활성화 절차는 파일 내 주석 + `devops-cloudflare-pages-secrets.md` 의 동일 패턴 참고.

### 1G RAM 한계 가드

- `concurrency.group: cafe24-ssh-deploy` + `cancel-in-progress: false` — 동시 배포 직렬화 (samhan 공식 홈페이지 + 추가 앱 OOM 방어)
- `NODE_OPTIONS=--max-old-space-size=512` 환경변수
- pm2 `--max-memory-restart 400M` 옵션

## 4. Secrets

`docs/dev-reports/devops-cloudflare-pages-secrets.md` 참고. 등록 대상:

| Secret | 사용 workflow |
|---|---|
| `CLOUDFLARE_API_TOKEN` | deploy-order-app.yml |
| `CLOUDFLARE_ACCOUNT_ID` | deploy-order-app.yml |
| `CAFE24_SSH_KEY` | (template 활성 후) deploy-cafe24-ssh.yml |
| `CAFE24_HOST` | (template 활성 후) deploy-cafe24-ssh.yml |
| `CAFE24_USER` | (template 활성 후) deploy-cafe24-ssh.yml |

본 PR 머지 시점에 `CLOUDFLARE_*` 2개만 우선 등록하면 order-app workflow 즉시 동작.

## 5. CI 트리거 영향

본 PR 의 변경 파일 = `.github/workflows/deploy-*.yml*` + `docs/dev-reports/devops-*.md`.

- `deploy-order-app.yml` 의 `paths` 필터 = `clients/web/order-app/**` + workflow 자체 — 본 PR 은 workflow 자체 변경 포함 → main 머지 시 트리거됨 (단 main 머지 후, PR 단계에서는 `push` 트리거 없음)
- `ci.yml` 은 PR 트리거 (전 모듈 build + test) — 정상 실행
- `*.template` 파일은 GitHub Actions 가 무시 (`.yml` 확장자 아님)

## 6. 후속 작업

| 항목 | 차기 슬라이스 |
|---|---|
| Cloudflare Pages preview environment (PR 별 임시 URL) | DEVOPS W5 |
| estimate-app v2 호스팅 결정 + workflow 활성 | D6/D7/D8 답변 후 별도 PR |
| 카페24 SSH workflow 활성 | D6/D7/D8 답변 후 별도 PR |
| Phase 7 backend 14 MSA Hetzner/Render 호스팅 workflow | Phase 7 별도 슬라이스 |
| 모니터링 / 알림 (Slack / Discord deploy hook) | DEVOPS W6 |

## 7. 검증 체크리스트

- [x] `deploy-order-app.yml` YAML syntax 정합 (jobs/steps/uses 정상)
- [x] `clients/web/order-app/package.json` scripts 와 step 명령 일치 (`typecheck` + `build`)
- [x] `vite.config.ts` `build.outDir = 'dist'` 와 deploy directory 일치
- [x] smoke test grep 문자열 ("주문서") `clients/web/order-app/index.html` `<title>` 에 존재 확인
- [x] estimate-app + 카페24 SSH 는 `.template` suffix → GitHub Actions 비활성
- [x] secrets 모두 `${{ secrets.* }}` placeholder, 실 값 X
