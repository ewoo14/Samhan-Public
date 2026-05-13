# 다중 PC 개발 환경 셋업 가이드

> 작성일: 2026-05-13
> 대상: 개발책임자 — 집 PC + 회사 PC 양쪽에서 동일하게 SamhanLogis 개발

---

## 1. 양 PC 공통 일회성 셋업

### 1-A. Git clone + 메모리 sync

```powershell
# 신규 PC 인 경우 clone
git clone https://github.com/ewoo14/SamhanLogis.git c:\dev\SamhanLogis
cd c:\dev\SamhanLogis

# Claude 메모리를 사용자 홈으로 sync (Claude Code 빌트인 경로 호환)
.\scripts\sync-claude-memory.ps1
```

### 1-B. 환경 변수 (.env)

`.env` 는 `.gitignore` 처리되어 sync 되지 않습니다 (DB 패스워드 / API Key 등 secret 보호).

```powershell
# 1. .env.example 을 .env 로 복사
Copy-Item .env.example .env

# 2. 메모장 등으로 열어서 값 채우기
notepad .env
```

채워야 하는 주요 값:
- `POSTGRES_PASSWORD` — 로컬 PostgreSQL 비밀번호 (자유)
- `JWT_SECRET` — 로컬 dev 용 임의 문자열 (32자 이상)
- `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` — 로컬 MinIO 자격 증명
- (기타 외부 API 키는 dev 단계에서 mock 토글로 미설정 가능)

> 1Password / Bitwarden 같은 비밀번호 관리자에 `.env` 통째로 저장해두면 양 PC sync 편함.

### 1-C. 필수 런타임

| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | 17 | 한글 경로 트랩 — [feedback_korean_path_jdk.md](../.claude/memory/feedback_korean_path_jdk.md) 참조 |
| Node.js | 20+ | clients/web 빌드 |
| Docker Desktop | 4.30+ | PostgreSQL / Redis / RabbitMQ / MinIO / ES |
| PowerShell | 5.1+ | Windows 기본 |
| Git | 2.40+ | gradlew 실행 권한 확인 — [feedback_gradlew_exec_bit.md](../.claude/memory/feedback_gradlew_exec_bit.md) |

### 1-D. Docker 인프라 기동

```powershell
docker-compose up -d
# PostgreSQL 14개 DB + Redis + RabbitMQ + MinIO + Elasticsearch + Prometheus + Grafana
```

### 1-E. seeder 실행 (dev 데이터)

```powershell
.\gradlew :services:partner-service:bootRun  # 첫 기동 시 seeder 자동 적재
# 또는 통합 스크립트
npm run db:seed-all
```

---

## 2. 매일 작업 시작 시

### 2-A. 도착 PC (회사 PC 라고 가정)

```powershell
cd c:\dev\SamhanLogis
git pull origin main
.\scripts\sync-claude-memory.ps1   # 메모리 갱신 즉시 적용
docker-compose up -d                # 인프라 기동 (이미 떠 있으면 skip)
```

### 2-B. Claude Code 세션 시작

```powershell
claude   # 또는 VSCode 의 Claude 확장
```

새 세션에서 첫 질문:

```
docs/handoff/CURRENT-WORK.md 읽고 현재 진행 상황 알려줘
```

---

## 3. 작업 마치고 PC 떠나기 전

```powershell
# 1. 현재 진행 상황을 핸드오프 노트에 갱신
notepad docs\handoff\CURRENT-WORK.md
# (작업 슬라이스 / 다음 단계 / 미해결 항목 업데이트)

# 2. 메모리에 새 결정/규칙 추가됐다면 commit
git add .claude/memory/ docs/handoff/
git commit -m "handoff: <작업 슬라이스 진행 상황>"
git push
```

---

## 4. 이카운트 raw 데이터 처리 (gitignore 됨)

이카운트 Excel 파일은 실 데이터 (사업자번호 / 미수금 / 거래내역) 이므로 git 에 commit 안 됩니다. **양 PC 에서 각자 재다운로드**:

### 회사 PC 에서 처음 받는 경우

```
1. 이카운트 ERP 콘솔 로그인
2. Self-Customizing > 정보관리 > 데이터관리 > 백업 및 삭제
3. 기초코드 탭 → "자료올리기형태로생성"
4. 이카운트 메신저 알림 → Excel 다운로드
5. c:\dev\SamhanLogis\docs\migration\ecount-data\raw\ 에 저장
6. PM 에게 알리기
```

상세는 [docs/migration/ecount-data/README.md](migration/ecount-data/README.md) §3 참조.

---

## 5. 안 이어지는 것 점검 체크리스트

새 PC 에서 "이전 PC 와 다르게 동작" 한다면 아래 확인:

| 증상 | 원인 | 해결 |
|---|---|---|
| Claude 가 메모리 규칙 모름 | sync 스크립트 미실행 | `.\scripts\sync-claude-memory.ps1` |
| 빌드 실패 / 환경변수 누락 | `.env` 미생성 | `.env.example` 복사 후 값 채움 |
| DB 연결 실패 | Docker 미기동 | `docker-compose up -d` |
| 이카운트 Excel 없음 | raw 폴더 비어있음 | 이카운트 콘솔에서 재다운로드 |
| 현재 작업 상황 모름 | 핸드오프 노트 미확인 | `docs/handoff/CURRENT-WORK.md` 읽기 |
| gradlew Permission denied | 실행 비트 누락 | `git update-index --chmod=+x gradlew` |

---

## 6. 관련 메모리 규칙

- [project_dev_environment.md](../.claude/memory/project_dev_environment.md) — JDK 17 / Gradle 8.10.2 / Docker
- [feedback_korean_path_jdk.md](../.claude/memory/feedback_korean_path_jdk.md) — 한글 경로 회피
- [feedback_powershell_utf8_writes.md](../.claude/memory/feedback_powershell_utf8_writes.md) — PowerShell UTF-8 트랩
- [feedback_gradlew_exec_bit.md](../.claude/memory/feedback_gradlew_exec_bit.md) — gradlew 실행 권한
