# 항목 1 — Tesseract OCR 설치 + vendor 발주 실 검증

> **선행 산출물** — `docs/dev-environment/tesseract-setup.md` (PR-F2 DevOps commit `f4232ba`)
> **본 문서** — Windows 개발자 환경 + partner-order-service 실 PDF 처리 dry-run 검증 절차

---

## 1. 사전 조건

- Windows 10/11 + PowerShell 5.1+
- 관리자 권한 PowerShell (PATH 등록 시)
- 인터넷 연결 (UB Mannheim installer 다운로드)

---

## 2. 사용자 작업 단계

### 2-1. Tesseract 5.x installer 다운로드 (UB Mannheim build)

1. https://github.com/UB-Mannheim/tesseract/wiki 접속
2. `tesseract-ocr-w64-setup-5.4.0.20240606.exe` (또는 최신 안정 버전) 다운로드

### 2-2. installer 실행

1. **언어팩 선택 화면** 에서 다음 체크 의무
   - `Korean (kor)` — 거래처 발주 PDF 의 본문 인식
   - `English (eng)` — 영숫자 (수량 / 단가 / 일자) 인식
2. 설치 경로: `C:\Program Files\Tesseract-OCR` (default 권장)

### 2-3. PATH 등록 (관리자 PowerShell)

```powershell
[Environment]::SetEnvironmentVariable(
    "PATH",
    [Environment]::GetEnvironmentVariable("PATH", "Machine") + ";C:\Program Files\Tesseract-OCR",
    "Machine"
)
```

> 새 PowerShell 창을 열어야 PATH 반영. IntelliJ / VS Code 통합 터미널은 IDE 재시작 필요.

### 2-4. 검증 — binary + 언어팩

```powershell
tesseract --version
# tesseract 5.x.x 출력 기대

tesseract --list-langs
# eng, kor 포함 기대
```

---

## 3. partner-order-service 실 PDF dry-run

### 3-1. OCR 환경 변수 활성

```powershell
$env:SAMHAN_OCR_ENABLED       = "true"
$env:TESSDATA_PATH            = "C:\Program Files\Tesseract-OCR\tessdata"
$env:TESSERACT_LANGUAGE       = "kor+eng"
$env:TESSERACT_PSM            = "6"
```

### 3-2. start-local-full.ps1 부팅 (14 service)

```powershell
.\infrastructure\scripts\start-local-full.ps1
```

partner-order-service (port 8088) 가 healthy 상태 확인.

### 3-3. JWT 발급 (kimmiseon = MASTER)

```powershell
$loginBody = '{"loginId":"kimmiseon","password":"samhan!2026"}'
$loginResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" `
    -Method POST -ContentType "application/json" -Body $loginBody
$token = $loginResp.data.accessToken
```

### 3-4. OCR endpoint 호출 (실 PDF / image multipart)

```powershell
# 샘플 발주서 image (PNG 또는 JPG, 100KB 이하 권장)
$samplePath = "tools\test-data\sample-vendor-order.png"
$form = @{ file = Get-Item $samplePath }
Invoke-RestMethod -Uri "http://localhost:8088/api/internal/ocr/parse-order" `
    -Method POST -Headers @{ Authorization = "Bearer $token" } -Form $form
```

> 샘플 image 부재 시 — 거래처가 보낸 실 발주서 PDF/image 1 건 사용. partner-order-service 가 OCR 결과를 JSON 으로 반환 (line items + 총액 추정).

---

## 4. 예상 결과 + 합격 기준

| 항목 | 기대 결과 | 합격 기준 |
| ---- | --------- | --------- |
| `tesseract --version` | `5.x.x` | major 5 |
| `tesseract --list-langs` | `eng`, `kor` 포함 | 둘 다 |
| `SAMHAN_OCR_ENABLED=false` 시 endpoint | 503 Service Unavailable | graceful fallback 정상 |
| `SAMHAN_OCR_ENABLED=true` + 샘플 image | 200 + JSON `{"items":[...], "totalEstimated":N}` | items.length ≥ 1 |
| Edge case — 손글씨 / 회전 image | 인식 정확도 < 80% | 운영자가 재업로드 / 수동 보정 필요 안내 |

---

## 5. 트러블슈팅 (자세한 내용은 PR-F2 가이드 §10 참조)

| 증상 | 원인 | 해결 |
| ---- | ---- | ---- |
| `tesseract` 명령 인식 안됨 | PATH 미반영 | 새 PowerShell 창 / IDE 재시작 |
| `Error opening data file ./tessdata/kor.traineddata` | `TESSDATA_PATH` 미설정 | `$env:TESSDATA_PATH` 명시 |
| OCR endpoint 503 | `SAMHAN_OCR_ENABLED=false` | env `true` 로 toggle 후 service 재기동 |
| OCR 정확도 낮음 | PSM 부적합 | `TESSERACT_PSM=11` (희소 텍스트) 시도 |

---

## 6. AWS 진입 (Phase 11) 영향

- EC2 m5.xlarge AMI 에 Tesseract 사전 설치 필요 (Phase 11 cutover PR 별도)
- partner-order-service Dockerfile 에 apt-get install 추가 (PR-F2 가이드 §4 의 Debian-slim 예시)
- production 환경변수 `TESSDATA_PATH=/usr/share/tesseract-ocr/4.00/tessdata` 설정 의무

---

## 7. 검증 완료 시 update

`docs/operational-validation/README.md` 의 §2 진행 상황 chart 의 항목 1 을 ✅ + 검증 일자 입력.
