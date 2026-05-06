# Service Account Key Rotation — 90일 주기

## 대상

| 항목 | 값 |
|---|---|
| Service Account | `samhan@samhan-homepage.iam.gserviceaccount.com` |
| 사용처 | estimate-app v2 (`GOOGLE_SERVICE_ACCOUNT_KEY`) — Google Sheets 직접 연동 (legacy v2 보존) |
| 주기 | 90일 (분기) |

## 절차

### 1. 신규 key 발급 (GCP 콘솔)

1. GCP 콘솔 → **IAM & Admin** → **Service Accounts**
2. `samhan@samhan-homepage.iam.gserviceaccount.com` 선택
3. **Keys** 탭 → **Add Key** → **Create new key** → **JSON**
4. 다운로드된 JSON 파일을 안전한 위치에 보관 (Bitwarden / 1Password 등)

### 2. Render dashboard 등록

1. Render dashboard → **samhan-estimate-app** → **Environment**
2. `GOOGLE_SERVICE_ACCOUNT_KEY` 항목 → **Edit**
3. 신규 key 의 JSON 전체 내용을 value 로 붙여넣기 (single-line escape 또는 multi-line 모두 허용)
4. **Save Changes** → 자동 redeploy 트리거 (autoDeploy: false 인 경우 수동 deploy 별도 실행)

### 3. 동작 검증

1. Render `/healthz` 엔드포인트 200 확인
2. estimate-app 의 견적 작성 → finalize 1회 실 시나리오 → Google Sheets row 정상 기록 확인

### 4. 기존 key disable

1. GCP 콘솔 → 동일 SA → **Keys** 탭
2. 기존 key (이전 회차) → **Disable** (즉시 폐기 X — 24시간 관찰 후 Delete)

### 5. 24시간 후 폐기

1. 운영 alert / error log 부재 확인
2. GCP 콘솔 → 동일 SA → **Keys** 탭 → 비활성 key → **Delete**

## 자동화

- `.github/workflows/sa-rotation-reminder.yml` — 분기 첫날 09:00 UTC (cron `0 9 1 */3 *`) Issue 자동 생성
- 생성된 Issue 의 checklist 따라 본 절차 수행

## 비상 절차 (key 누설)

1. GCP 콘솔 → 즉시 **Disable** + **Delete** (24시간 관찰 skip)
2. 신규 key 발급 → Render 등록 → 동작 검증 (위 1~3)
3. 누설 경위 incident log 작성 (`docs/security/incident-YYYYMMDD.md`)
4. Slack `#samhan-ops` 채널 보고
