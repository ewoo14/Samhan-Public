# Tesseract OCR 네이티브 설치 가이드 (PR-F2)

> **목적**: `partner-order-service` 의 `TesseractOcrEngine` (PR-F2 BE) 가 네이티브 의존성 (Tesseract 5.x binary + 한국어 traineddata) 을 요구한다. 본 문서는 환경별 (Windows / Linux / Docker / macOS) 설치 절차와 production secret 가이드를 제공한다.
>
> **이식 결정**: Phase 10 W10-7 회고 — 사용자 결정에 따라 OCR 엔진은 **Tesseract (무료 on-prem)** 채택. 외부 클라우드 OCR (Google Vision / AWS Textract / Naver Clova) 의존성 0.

---

## 1. 사전 요구사항

- Tesseract 5.x (4.x 도 동작하나 일관성을 위해 5.x 권장)
- 한국어 traineddata (`kor.traineddata`)
- 영문 traineddata (`eng.traineddata`) — 영숫자 (수량 / 단가 / 일자) 인식 정확도 보강
- BE-1 환경변수: `SAMHAN_OCR_ENABLED=true` 활성 시에만 네이티브 호출 진입

---

## 2. Windows 개발 환경

### 2-1. Tesseract 5.x 설치 (UB Mannheim build)

1. 공식 빌드 다운로드 — https://github.com/UB-Mannheim/tesseract/wiki
2. installer 실행 (`tesseract-ocr-w64-setup-5.4.0.20240606.exe` 등 최신 안정 버전)
3. **언어팩 선택 화면**에서 다음을 체크
   - `Korean (kor)`
   - `English (eng)` — 기본 포함이지만 누락 시 명시 체크
4. 설치 경로: `C:\Program Files\Tesseract-OCR` (default 권장)

### 2-2. PATH 등록

PowerShell (관리자):

```powershell
[Environment]::SetEnvironmentVariable(
    "PATH",
    [Environment]::GetEnvironmentVariable("PATH", "Machine") + ";C:\Program Files\Tesseract-OCR",
    "Machine"
)
```

또는 GUI: `시스템 속성 → 환경 변수 → Path → 편집` 으로 `C:\Program Files\Tesseract-OCR` 추가.

### 2-3. 검증

새 PowerShell 창:

```powershell
tesseract --version
# tesseract 5.4.0 ...

tesseract --list-langs
# List of available languages (xx):
# eng
# kor
```

### 2-4. 환경변수 (개발용)

`.env.dev` 또는 사용자 환경변수:

```
SAMHAN_OCR_ENABLED=true
TESSDATA_PATH=C:\Program Files\Tesseract-OCR\tessdata
TESSERACT_LANGUAGE=kor+eng
TESSERACT_PSM=6
```

> `TESSDATA_PATH` 미설정 시 Tesseract 가 시스템 default tessdata 폴더 자동 탐색.

---

## 3. Linux production 환경 (Ubuntu 22.04 / Debian 12)

### 3-1. apt 패키지 설치

```bash
sudo apt-get update
sudo apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-kor \
    tesseract-ocr-eng

# 검증
tesseract --version
tesseract --list-langs
```

### 3-2. tessdata 경로 (Ubuntu/Debian default)

```
/usr/share/tesseract-ocr/4.00/tessdata/
# (apt 패키지 버전이 4.x 이면 4.00, 5.x 이면 5/tessdata)
```

> Ubuntu 24.04 / Debian 13 부터는 `/usr/share/tesseract-ocr/5/tessdata/` 일 수 있음. `dpkg -L tesseract-ocr-kor | grep traineddata` 로 실제 경로 확인.

### 3-3. production 환경변수 (`.env.prod`)

```
SAMHAN_OCR_ENABLED=true
TESSDATA_PATH=/usr/share/tesseract-ocr/4.00/tessdata
TESSERACT_LANGUAGE=kor+eng
TESSERACT_PSM=6
```

> **secret 정책**: Tesseract 자체에는 secret 이 없다 (전부 로컬 binary + traineddata). API key 부재가 본 엔진 채택의 핵심 이점.

---

## 4. Docker container (Phase 11 AWS 대비)

`partner-order-service` Dockerfile 보강 (예시):

```dockerfile
FROM eclipse-temurin:17-jre-alpine AS runtime

# Tesseract OCR (PR-F2) — 한국어 + 영문 traineddata 포함
# Alpine 의 경우 apk; Debian-slim base 사용 시 apt-get 으로 변경
RUN apk add --no-cache \
        tesseract-ocr \
        tesseract-ocr-data-kor \
        tesseract-ocr-data-eng

ENV TESSDATA_PATH=/usr/share/tessdata
ENV TESSERACT_LANGUAGE=kor+eng
ENV TESSERACT_PSM=6

# ... 이하 service jar copy / entrypoint
```

Debian-slim base (대안):

```dockerfile
FROM eclipse-temurin:17-jre AS runtime

RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-kor \
        tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

ENV TESSDATA_PATH=/usr/share/tesseract-ocr/4.00/tessdata
```

> Phase 11 AWS EC2 m5.xlarge 단일 환경 결정 (`project_phase11_aws.md`). 컨테이너 base 결정은 Phase 11 cutover 시점 별도 PR.

---

## 5. macOS (선택 — 개발자 옵션)

```bash
brew install tesseract tesseract-lang
# tesseract-lang 패키지가 100+ 언어 traineddata 일괄 설치 (kor/eng 포함)

tesseract --list-langs | grep -E "kor|eng"
```

기본 tessdata 경로:

```
/opt/homebrew/share/tessdata/      # Apple Silicon
/usr/local/share/tessdata/         # Intel
```

---

## 6. application.yml 연계

`services/partner-order-service/src/main/resources/application.yml` 의 OCR 섹션:

```yaml
samhan:
  partner-order:
    ocr:
      enabled: ${SAMHAN_OCR_ENABLED:false}
      tesseract:
        data-path: ${TESSDATA_PATH:/usr/share/tesseract-ocr/4.00/tessdata}
        language: ${TESSERACT_LANGUAGE:kor+eng}
        page-seg-mode: ${TESSERACT_PSM:6}
```

BE-1 의 `TesseractOcrEngine` 은 `@ConditionalOnProperty(name = "samhan.partner-order.ocr.enabled", havingValue = "true", matchIfMissing = false)` 으로 보호되며, **Tesseract 미설치 환경 (CI / 신규 개발자 첫 부팅) 에서는 OCR endpoint 가 503 Service Unavailable 응답** 을 반환한다 (graceful fallback).

---

## 7. PSM (Page Segmentation Mode) 권장값

| PSM | 의미 | 본 프로젝트 권장 |
| --- | ---- | ---------------- |
| 3   | 자동 페이지 분할 (default) | × |
| 4   | 단일 column 가변 폭 텍스트 | △ |
| **6** | **단일 균일 텍스트 블록** | **○ (default)** |
| 11  | 희소 텍스트 (단어 위치 무관) | 견적서 단가 / 수량 표 |
| 12  | 희소 + OSD | 회전된 스캔본 |

거래처 주문서 OCR 은 대부분 표 형태이므로 `PSM 6` default. 정확도가 떨어지는 경우 endpoint query param 으로 PSM override 지원 (BE-1 별도 처리).

---

## 8. CI 환경 (GitHub Actions)

`.github/workflows/ci.yml` 의 `accounting+partner` 그룹 step 에 다음 설치 step 이 추가되었다.

```yaml
- name: Tesseract OCR 설치 (PR-F2 — partner-order-service OCR 그룹만)
  if: matrix.group.name == 'accounting+partner'
  run: |
    sudo apt-get update
    sudo apt-get install -y \
        tesseract-ocr \
        tesseract-ocr-kor \
        tesseract-ocr-eng
    tesseract --version
    tesseract --list-langs
```

> CI 단위 테스트는 `samhan.partner-order.ocr.enabled=false` 가 default 이므로 Tesseract 호출이 mock 으로 격리되지만, 통합 테스트 (`@SpringBootTest`) 가 OCR endpoint 회귀 검증을 수행할 가능성에 대비해 native binary 를 사전 설치한다.

---

## 9. 검증 체크리스트

| 항목 | 검증 방법 | 기대 결과 |
| ---- | --------- | --------- |
| binary 설치 | `tesseract --version` | `tesseract 5.x.x` |
| 한국어 traineddata | `tesseract --list-langs` | `kor` 포함 |
| 영문 traineddata | `tesseract --list-langs` | `eng` 포함 |
| application.yml | `./gradlew :services:partner-order-service:assemble` | BUILD SUCCESSFUL |
| BE-1 endpoint | `curl http://localhost:8088/api/internal/ocr/parse-order` (`SAMHAN_OCR_ENABLED=false`) | 503 Service Unavailable |
| BE-1 endpoint | 동 endpoint (`SAMHAN_OCR_ENABLED=true` + 샘플 image multipart) | 200 + parsed JSON |

---

## 10. 트러블슈팅

### 10-1. `Error opening data file ./tessdata/kor.traineddata`

원인 — `TESSDATA_PATH` 미설정 또는 traineddata 누락.

해결:

```bash
# 실제 traineddata 위치 확인
dpkg -L tesseract-ocr-kor | grep traineddata     # Linux
where tesseract                                  # Windows (PowerShell)

# 환경변수 명시
export TESSDATA_PATH=/usr/share/tesseract-ocr/4.00/tessdata
```

### 10-2. Windows 개발자 — `tesseract` 명령 인식 안됨

새 PowerShell / CMD 창을 열어야 PATH 변경이 반영됨. IntelliJ / VS Code 의 통합 터미널은 IDE 재시작 필요.

### 10-3. CI 환경 timeout (apt-get install 1분 초과)

`accounting+partner` 그룹 timeout 30분 → 충분. 단, apt mirror 응답 지연 시 GitHub Actions runner 재시도 필요. 빈도 잦으면 self-hosted runner / docker layer cache 도입 고려 (Phase 11).

### 10-4. macOS Apple Silicon — `brew install tesseract` 후 인식 실패

`/opt/homebrew/bin` 이 PATH 에 포함되어야 함:

```bash
echo 'export PATH="/opt/homebrew/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

---

## 11. 참조

- [Tesseract OCR 공식 wiki](https://github.com/tesseract-ocr/tesseract/wiki)
- [UB Mannheim Windows build](https://github.com/UB-Mannheim/tesseract/wiki)
- [tessdata (한국어 모델)](https://github.com/tesseract-ocr/tessdata)
- [Phase 10 결정 근거](../../migration/decisions/DECISIONS.md) — D-P10-13 (예정)
- BE-1 구현 PR — feature/integrated-phase-10-step-13-vendor-ocr (PR-F2)
- 본 인프라 PR — feature/integrated-phase-10-step-13-vendor-ocr (PR-F2 DevOps)
