# Render cutover 체크리스트 — estimate-app v2

본 체크리스트는 Render Blueprint 활성화 시 1회 실행한다.
이후 main push 시 Render auto-deploy 가 동작하므로 본 체크리스트는 cutover 전후 1회만 적용.

## 사전 준비

- [ ] Render 계정 + Starter plan 결제 등록 완료
- [ ] 저장소 `ewoo14/SamhanLogis` Render 측 연결 완료
- [ ] Phase 7 backend staging endpoint 5종 확정 (M2 / M3 / M4 / M5 / product-service)
- [ ] Google Cloud service account JSON 발급 + spreadsheet 권한 위임 (Editor)
- [ ] DNS 관리자 권한 확보 (`quote.samhan-air.com` CNAME 등록 가능 상태)

## Blueprint 등록

- [ ] dashboard `New +` → `Blueprint` → `infrastructure/render/render.yaml` 인식 확인
- [ ] `samhan-estimate-app` (Web Service) 만 활성. `samhan-order-app` 은 비활성 유지 (Cloudflare Pages 가 owner)

## 환경변수 등록 (sync: false 항목)

| Key | 출처 | 등록 |
|---|---|---|
| `SAMHAN_BACKEND_BASE_URL` | backend 호스팅 결정 | [ ] |
| `PARTNER_AUTH_SERVICE_URL` | backend 호스팅 결정 | [ ] |
| `PARTNER_ORDER_SERVICE_URL` | backend 호스팅 결정 | [ ] |
| `SLIP_SERVICE_URL` | backend 호스팅 결정 | [ ] |
| `PRODUCT_SERVICE_URL` | backend 호스팅 결정 | [ ] |
| `DC_CONFIG_SERVICE_URL` | backend 호스팅 결정 | [ ] |
| `GOOGLE_SERVICE_ACCOUNT_KEY` | Google Cloud Console | [ ] |
| `GOOGLE_SHEETS_SPREADSHEET_ID` | legacy 견적 spreadsheet URL | [ ] |

## DNS 연결

- [ ] Render 측 `samhan-estimate-app.onrender.com` endpoint 활성 확인
- [ ] DNS CNAME 등록: `quote.samhan-air.com` → `samhan-estimate-app.onrender.com`
- [ ] Render dashboard custom domain 등록 + DNS 검증 PASS
- [ ] SSL 자동 발급 (Let's Encrypt) PASS

## 1차 smoke test

- [ ] `curl -fsS https://quote.samhan-air.com/healthz` → `{"ok":true,...}`
- [ ] 브라우저로 `https://quote.samhan-air.com/` 진입 → legacy 견적 UI 정상 렌더링
- [ ] Google Sheets 데이터 연동 정상 (모델 목록 / 단가 표시)
- [ ] backend RPC 호출 1건 성공 (`POST /rpc/<fn>` → 200)

## QA 시나리오 PASS

- [ ] `qa/playwright/tests/` 의 `web-estimate-app` project 시나리오 60 cell PASS (Phase 7 staging 환경)

## Rollback 절차

배포 직후 critical 결함 발견 시:

1. Render dashboard → samhan-estimate-app → Manual Deploy → 직전 commit hash 선택
2. 또는 main 의 git revert 커밋 push → Render auto-deploy 가 직전 상태로 복원
3. 1G RAM 한계와 무관하므로 rollback latency = 빌드 1회 (~2~3분)
