# Render.com Blueprint — estimate-app v2 + order-app v4

본 디렉토리는 [Render.com](https://render.com/) Blueprint 기반 호스팅 정의를 보관한다.
`docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md` 의 **B 옵션 (Render Starter $7/mo)** 채택 결과
implementation 산출물이다.

## 파일 목록

| 파일 | 용도 |
|---|---|
| `render.yaml` | Blueprint 정의 (estimate-app Web + order-app Static mirror) |
| `deploy-checklist.md` | cutover 직전 체크리스트 (DNS / secret / smoke test) |
| `README.md` | 본 파일 |

## 배포 절차 (1차 — estimate-app v2 만)

1. **Render 계정 + 결제 등록**
   - [https://dashboard.render.com](https://dashboard.render.com) 가입 후 신용카드 등록
   - Starter plan ($7/mo, always-on, 512MB RAM) 활성

2. **Blueprint 등록**
   - dashboard 우상단 `New +` → `Blueprint`
   - 저장소 `ewoo14/SamhanLogis` 연결 + branch `main` 선택
   - `infrastructure/render/render.yaml` 자동 인식 → service 2개 (estimate / order) 검출
   - 1차에서는 `samhan-estimate-app` 만 활성화. `samhan-order-app` 은 `autoDeploy: false`
     로 정의되어 있으므로 default 비활성. (order-app 은 PR #77 의 Cloudflare Pages workflow 가 owner)

3. **Secret 환경변수 등록 (Render dashboard → service → Environment)**

   `sync: false` 로 지정된 항목은 Blueprint 가 등록하지 않는다. dashboard 에서 직접 등록한다:

   | Key | 값 (출처) |
   |---|---|
   | `SAMHAN_BACKEND_BASE_URL` | Phase 7 backend staging endpoint (호스팅 결정 후) |
   | `PARTNER_AUTH_SERVICE_URL` | M2 partner-auth-service staging URL |
   | `PARTNER_ORDER_SERVICE_URL` | M4 partner-order-service staging URL |
   | `SLIP_SERVICE_URL` | M5 slip-service staging URL |
   | `PRODUCT_SERVICE_URL` | M1a product-service staging URL |
   | `DC_CONFIG_SERVICE_URL` | M3 dc-config-service staging URL |
   | `GOOGLE_SERVICE_ACCOUNT_KEY` | Google Cloud service account JSON (legacy estimate-app v2 google sheets 연동) |
   | `GOOGLE_SHEETS_SPREADSHEET_ID` | legacy 견적 spreadsheet ID |

4. **DNS 연결**
   - 카페24 또는 Cloudflare DNS 콘솔에서 `quote.samhan-air.com` CNAME → Render 가 발급하는
     `samhan-estimate-app.onrender.com` 등록
   - Render dashboard 에서 custom domain 추가 + DNS 검증 통과 → 자동 SSL (Let's Encrypt)

5. **smoke test**
   ```bash
   curl -fsS https://quote.samhan-air.com/healthz   # {"ok":true,...}
   ```

## CI workflow 활성화

`.github/workflows/deploy-estimate-app.yml.template` 를
`deploy-estimate-app.yml` 로 rename 하여 활성. Render 의 main push 자동 deploy 가
`autoDeploy: true` 로 동작하므로, GitHub Actions 의 역할은 빌드 검증 + 단위 테스트 PASS 게이트 만이다.

## 미결 항목

- `SAMHAN_BACKEND_BASE_URL` 의 실 값은 14 backend MSA 호스팅 결정 (`M-PHASE-7-readiness.md` § 4) 의
  X1~X4 중 1건 채택 후 확정한다. 그 전까지는 Render 측에서 estimate-app 의 backend 호출
  endpoint 미구성 상태로 둔다 (frontend 정적 + Google Sheets 직접 연동만 동작).
- `samhan-order-app` Render 활성화는 Cloudflare Pages 와의 단일 owner 결정 후. 현재는 mirror 정의만 보관.
