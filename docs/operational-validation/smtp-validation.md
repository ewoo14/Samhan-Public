# 항목 2 — SMTP 설정 (notification-service) + 비밀번호 재설정 메일 발송 검증

> **선행 산출물** — PR-D commit `d7a201b` (notification-service `application.yml` SMTP 섹션 추가)
> **본 문서** — cafe24 SMTP / AWS SES 등 실 SMTP secret 입력 + auth-service 비밀번호 재설정 endpoint 호출 시 실 메일 1 통 도착 검증

---

## 1. SMTP 환경 변수 명세 (notification-service)

`services/notification-service/src/main/resources/application.yml` §54~63:

```yaml
smtp:
  host:      ${SMTP_HOST:smtp.cafe24.com}
  port:      ${SMTP_PORT:587}
  username:  ${SMTP_USERNAME:}
  password:  ${SMTP_PASSWORD:}
  from:      ${SMTP_FROM:noreply@samhan-air.com}
  starttls:  ${SMTP_STARTTLS:true}
```

> **NoOp 동작 조건** — `username` 또는 `password` 가 빈 문자열인 경우 `SmtpEmailAdapter` 가 본문/수신자 로그만 출력하고 실 송신은 skip (local dev default). 본 검증 단계에서는 둘 모두 명시 의무.

---

## 2. 사용자 작업 단계

### 2-1. SMTP 자격증명 확보

#### Option A — cafe24 메일 호스팅
1. cafe24 회원관리 → 메일관리 → SMTP 사용 신청
2. `noreply@samhan-air.com` 계정 생성 + 비밀번호 발급
3. SMTP 정보:
   - host: `smtp.cafe24.com`
   - port: 587 (STARTTLS) 또는 465 (SSL)
   - username: 전체 메일 주소 (`noreply@samhan-air.com`)
   - password: 메일 계정 비밀번호

#### Option B — AWS SES (Phase 11 cutover 시점 권장)
1. AWS Console → SES → SMTP credentials 생성
2. 도메인 인증 (`samhan-air.com` MX/TXT 레코드 추가) — 본 단계 검증 사전 의무
3. **production access 신청 의무** — 기본 sandbox 모드는 verified 주소만 송수신 가능
4. SMTP 정보 (Seoul region):
   - host: `email-smtp.ap-northeast-2.amazonaws.com`
   - port: 587
   - username: AWS SMTP credential ID (IAM user 와 별개)
   - password: AWS SMTP credential secret

### 2-2. 환경 변수 export (PowerShell)

```powershell
$env:SMTP_HOST     = "smtp.cafe24.com"
$env:SMTP_PORT     = "587"
$env:SMTP_USERNAME = "noreply@samhan-air.com"
$env:SMTP_PASSWORD = "<발급받은 비밀번호>"
$env:SMTP_FROM     = "noreply@samhan-air.com"
$env:SMTP_STARTTLS = "true"
```

> **secret 보호** — 본 환경변수는 PowerShell 세션 종료 시 자동 소멸. `.env.local` 등 git tracked 파일 절대 금지.

### 2-3. notification-service 재기동

start-local-full.ps1 가 이미 떠 있는 경우 — notification-service 만 재기동 (env 반영):

```powershell
# 기존 process 종료 후 단독 기동
Get-Process java | Where-Object { $_.MainWindowTitle -like "*notification*" } | Stop-Process -Force
.\gradlew.bat :services:notification-service:bootRun --console=plain
```

또는 14 service 일괄 재기동:

```powershell
.\infrastructure\scripts\stop-local-full.ps1
.\infrastructure\scripts\start-local-full.ps1
```

---

## 3. 비밀번호 재설정 메일 발송 검증

### 3-1. auth-service password reset request endpoint

```powershell
$resetBody = '{"loginId":"kimmiseon","email":"<수신할 실 메일>"}'
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/password/reset/request" `
    -Method POST -ContentType "application/json" -Body $resetBody
```

### 3-2. 기대 동작

1. auth-service 가 reset token 발급 + DB 저장
2. notification-service 의 SMTP 채널로 메일 발송 요청 publish
3. `SmtpEmailAdapter` 가 SMTP 서버 (cafe24 / SES) 로 실 메일 송신
4. 수신함에 본문 + reset 링크 도착

### 3-3. 합격 기준

| 항목 | 기대 결과 | 합격 |
| ---- | --------- | ---- |
| auth-service 응답 | HTTP 200 (token 발급 silent) | ✅ |
| notification-service 로그 | `SMTP send OK to <email>` 라인 | ✅ |
| 수신함 | 1 통 도착, 본문 한국어 정상, reset 링크 유효 | ✅ |
| reset 링크 클릭 후 confirm endpoint | 비밀번호 재설정 성공 | ✅ |

---

## 4. 트러블슈팅

| 증상 | 원인 | 해결 |
| ---- | ---- | ---- |
| `[NotificationStub] queued` 로그만 출력, 실 메일 미송신 | `SMTP_USERNAME` / `SMTP_PASSWORD` 빈값 | 환경변수 다시 export + 서비스 재기동 |
| `MessagingException: Could not connect to SMTP host` | port 차단 (회사 방화벽 25/465/587 차단 흔함) | 587 STARTTLS 시도, 또는 회사 외부 환경 |
| `AuthenticationFailedException` | username (전체 메일주소) 또는 비밀번호 오타 | cafe24 계정 활성 여부 확인 |
| AWS SES 송신 후 `MessageRejected: Email address is not verified` | SES sandbox 모드 + 미인증 수신자 | 수신자 verified 처리 또는 production access 신청 |
| 한글 본문 `?` 깨짐 | charset 미명시 | `JavaMailSenderImpl.setDefaultEncoding("UTF-8")` 확인 (이미 적용됨) |

---

## 5. AWS 진입 (Phase 11) 영향

- 본 검증 = **cafe24 SMTP 로 1 회 송신 의무** (개발 단계 충분)
- Phase 11 cutover 시점에 AWS SES 로 전환 — `SMTP_HOST=email-smtp.ap-northeast-2.amazonaws.com` 만 변경, application 코드 변경 0
- SES production access 신청 = Phase 11 cutover 1 주 전 사용자 작업 백로그 등록 권장 (승인 1~2 영업일 소요)

---

## 6. 검증 완료 시 update

`docs/operational-validation/README.md` 의 §2 진행 상황 chart 의 항목 2 를 ✅ + 검증 일자 + 사용한 SMTP provider (cafe24 / SES) 비고에 명시.
