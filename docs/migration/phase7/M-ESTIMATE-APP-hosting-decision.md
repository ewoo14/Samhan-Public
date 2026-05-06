# M-ESTIMATE-APP — estimate-app v2 호스팅 결정 plan (Phase 7)

## 0. 결론

**B 옵션 (Render.com Node.js Web Service) 를 권장한다.**

- 카페24 SSH 즉시 배포는 보류 (테스트만 진행) — `D-P6-04` 결정.
- A (Cloudflare Workers) 는 Express + EJS + googleapis 호환 검증 비용이 비교적 큼.

## 1. 배경

estimate-app v2 (`clients/web/estimate-app/`) 는 다음 stack:

- `package.json` `"main": "server.js"` + `express` + `ejs` + `googleapis` + `axios` + `dotenv`
- runtime Node.js process 필수 — `node server.js`
- build/dist step 부재 (정적 산출물 없음)

PR #77 (DEVOPS deploy workflow) 에서 정리 — Cloudflare Pages 는 정적 호스팅 전용으로 estimate-app v2 의 Cloudflare Pages 배포는 **기술적으로 불가**.

3안 비교 후 1건 채택 필요.

## 2. 옵션 비교

### A. Cloudflare Workers (workerd runtime)

| 항목 | 상세 |
|---|---|
| 비용 | 무료 tier (10ms CPU/req, 100k req/day) → 견적 trafic 충분 가능. 유료 $5/mo (10M req) |
| Latency | edge 분산, 한국 사용자 < 50ms |
| 운영 부담 | **중상** — Express → workerd 변환 필요 |
| 호환성 검증 | EJS 렌더링 (filesystem read 가능) / `googleapis` (Node.js HTTP module 의존 — workerd 의 `nodejs_compat` flag 필요) / Express middleware (Hono adapter 등 별도 layer) |
| 미지수 | EJS template 의 ~13MB 폰트 inlined 자산 → Workers 의 1MB script size 한계 ↔ R2 / KV 분리 필요 |

**판정**: 미지수가 다수. PoC (1~2주) 후 결정해야 안전.

### B. Render.com Node.js Web Service (권장)

| 항목 | 상세 |
|---|---|
| 비용 | Free (spin-down 15분 idle) → 운영 부적합. **Starter $7/mo** (always-on, 512MB RAM) 권장 |
| Latency | Oregon/Singapore region — 한국 사용자 ~150ms (CloudFlare 보다 ↑, 견적 사용 빈도 고려 시 허용 가능) |
| 운영 부담 | **하** — `package.json` + `server.js` 그대로 배포, build command `npm ci`, start command `node server.js` |
| 호환성 검증 | Express / EJS / googleapis 모두 native Node.js 18/20 → 검증 불필요 |
| 보안 | `.env` 환경변수 dashboard 관리, secret rotation gh actions 연동 가능 |

**판정**: 호환성 검증 없이 즉시 배포 가능. 운영 부담 낮음. 월 $7 비용 허용 가능.

### C. 카페24 SSH (Node.js + pm2)

| 항목 | 상세 |
|---|---|
| 비용 | 카페24 호스팅 plan 내 (추가 비용 X) |
| Latency | 국내 IDC, ~30ms |
| 운영 부담 | **상** — 1G RAM 에 samhan 공식 홈페이지 pm2 가 이미 운영 중. estimate-app + Express 추가 시 OOM 가드 필요 (`--max-old-space-size=512` + `pm2 max-memory-restart 400M`) |
| 호환성 검증 | Express / EJS / googleapis native — 검증 불필요 |
| 가드 | `D-P6-04` (카페24 SSH 즉시 배포 보류 — 테스트만) 적용 중. 활성화 시점 별도 답변 필요 (D6/D7/D8) |

**판정**: 비용 0 이지만 1G RAM 한계 + 기존 pm2 process 공존이 risk. Phase 7 활성화 결정 후 가능.

## 3. 비교 매트릭스

| 항목 | A Workers | B Render | C 카페24 |
|---|---|---|---|
| 비용 (월) | $0~5 | $7 | $0 (기존 plan 내) |
| Latency (한국) | < 50ms | ~150ms | ~30ms |
| 운영 부담 | 중상 (PoC 필요) | 하 | 상 (OOM 가드) |
| 호환성 검증 | 필요 | 불필요 | 불필요 |
| 즉시 배포 가능 | X (PoC 후) | O | X (D6/D7/D8 답변 후) |
| 보안 secret 관리 | dashboard + wrangler | dashboard | `.env` 파일 + permissions |

## 4. 권장 시나리오

### Phase 7 진입 즉시

- **B Render Starter $7/mo** 로 1차 운영 시작.
- DNS `estimate.samhan-air.com` → Render endpoint CNAME 연결.
- backend (Phase 7 staging) 도 Render reuse 가능 (Phase 7 backend 호스팅 결정과 동시 진행 권장).

### Phase 7 후속 (선택)

- A Workers PoC 진행 → workerd `nodejs_compat` flag + Hono adapter 검증 통과 시 무료 tier 로 이전 가능.
- C 카페24 SSH 활성화 시 (D6/D7/D8 답변 후) → 1G RAM OOM 검증 후 B → C 이전 가능.

## 5. Phase 7 진입 전 작업

| 작업 | 산출 | 담당 |
|---|---|---|
| Render Starter 계정 + Web Service 생성 | dashboard 설정 + endpoint URL | DEVOPS |
| `.env` Render dashboard 등록 (SAMHAN_API_BASE_URL / SLIP_SERVICE_URL / PARTNER_SERVICE_URL 등) | secret 등록 결과 | DEVOPS |
| `deploy-estimate-app.yml.template` 활성화 | `.template` 제거 + Render webhook trigger 주석 해제 | DEVOPS |
| DNS `estimate.samhan-air.com` CNAME → Render | DNS record | DEVOPS |
| Phase 7 backend staging 가동 후 estimate-app v2 e2e 검증 | Playwright 30 case → 90 case 확장 | QA |

## 6. 미결 항목

- backend Phase 7 staging endpoint 미정 (`SAMHAN_API_BASE_URL` 의 실 값) → backend 호스팅 결정 (`docs/migration/phase7/M-PHASE-7-readiness.md` § 4) 과 함께 확정 필요.
- Render free tier 의 spin-down 한계 검증 필요 시 PoC (Free → Starter 업그레이드 시점 판정).
