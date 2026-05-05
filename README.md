# SamhanLogis - (주)삼한공조시스템 자체 물류 프로그램

> 삼성 시스템에어컨 공식 파트너사 (주)삼한공조시스템의 자체 물류·회계·그룹웨어 통합 플랫폼

---

## 📋 프로젝트 개요

| 항목 | 내용 |
|------|------|
| **프로젝트명** | SamhanLogis (삼한로지스) |
| **아키텍처** | MSA (Microservices Architecture) |
| **인프라** | 분산 Docker 서버, Eureka Gateway, Load Balancing |
| **배포 형태** | 내부: EXE (Electron), 외부: Web/App |
| **기술 스택** | Java 17 / Spring Boot 3, PostgreSQL, React + TypeScript, Electron |

## 🏗️ 시스템 구성

- **13개 마이크로서비스** (각각 독립 PostgreSQL DB, Eureka 등록)
- **마이크로서비스별 4-team 에이전트 구성**: TM(팀장) + BE + FE + QA, 총괄 PM 1명
- **전체 로드맵**: 33주 (Phase 1~7)
- **GitHub 워크플로우**: 슬라이스당 팀별 PR/Issue 분리, TM·PM 자동 승인 + 개발책임자 머지

## 📊 진척률 (2026-05-05 기준, main `40c8f9d`)

### 마이크로서비스 인벤토리

| # | 서비스 | 포트 | DB | 상태 |
|---|-------|-----|----|----|
| 1 | API Gateway | 8080 | — | ✅ Phase 1 |
| 2 | Eureka Server | 8761 | — | ✅ Phase 1 |
| 3 | Auth Service | 8081 | auth_db | ✅ Phase 1 + Internal API (Phase 2 후속 정리에서 Flyway 정상화) |
| 4 | User Service | 8083 | user_db | ✅ Phase 2 첫 슬라이스 (16명 시드, AuthClient 패턴) |
| 5 | Product Service | 8084 | product_db | ✅ Phase 2 본 작업 첫 슬라이스 (Product/Category 도메인, 14 endpoint, jsonb 태그) |
| 6 | Inventory Service | 8085 | inventory_db | ✅ Phase 2 본 작업 두 번째 슬라이스 (FIFO + 4-tier 창고 + 이동전표 22 endpoint, Plan §3.1 4-tier 채택, X-Internal-Token gateway 우회) |
| 7 | Slip Service | 8086 | slip_db | ✅ Phase 3 + sales-polish-2 + notification-slice-B + signature-slice-C/C2 (10단계 라이프사이클, dispatcher/inspector 자동 서명, 라인 specification, DeliveryBatch + Solapi SMS, 모바일 전자서명 (Canvas + SHA-256, 인수자/기사 양측 캡처), DispatchView 인쇄 통합, 30 endpoint) |
| 8 | Accounting Service | 8087 | accounting_db | ✅ Phase 4 첫 슬라이스 (한국 일반기업회계기준 65 row 시드, ChartOfAccount + Journal + JournalLine + 시산표, 7 endpoint, audit safe reverse 분개) |
| 9 | Partner Service | 8088 | partner_db | ⬜ Phase 4 |
| 10 | Groupware Service | 8089 | groupware_db | ⬜ Phase 5 |
| 11 | Notification Service | 8090 | (Redis) | ⬜ Phase 5 |
| 12 | Logging Service | 8082 | (Elasticsearch) | ✅ Phase 1 (RabbitMQ consumer 패턴) |
| 13 | Dashboard Service | 8091 | dashboard_db | ⬜ Phase 5 |
| 14 | Migration Service | 8092 | migration_db | ⬜ Phase 7 (ECount 마이그레이션) |

**완료 8 / 13 (62%)**.

### 클라이언트

| 항목 | 상태 |
|------|------|
| 디자인 시스템 (`clients/web/design-system`) | ✅ 21 컴포넌트 (+ SignaturePad, SignatureViewer — signature-slice-C 신규) |
| Electron 데스크톱 앱 | ✅ + signature-slice-C/C2-UX (16 라우트 / 모바일 서명 2-step 흐름 (기사→인수자) + 캔버스 fullscreen UX + 인수자 share view + DispatchView 양측 서명 PNG 자동 통합) |
| React 웹 앱 (외부 거래처용) | ⬜ Phase 6 |
| React Native 모바일 (창고원/거래처 듀얼) | ⬜ Phase 6 |

## 📁 프로젝트 구조

```
SamhanLogis/
├── docs/                    # 전체 문서
│   ├── PM/                  # PM 총괄 문서 + 프로젝트 plan
│   ├── backend/             # 백엔드 팀 문서
│   ├── frontend/            # 프론트엔드 팀 문서
│   ├── uiux/                # UI/UX 팀 문서
│   ├── devops/              # DevOps 팀 검토 리포트 (per-service)
│   └── qa/                  # QA 리포트 + 스크린샷 (per-slice)
├── services/                # 13개 백엔드 마이크로서비스
│   ├── api-gateway/         # Spring Cloud Gateway (reactive)
│   ├── eureka-server/       # Service discovery
│   ├── auth-service/        # JWT 발급, 계정, internal API
│   ├── user-service/        # 직원/조직도, AuthClient 패턴
│   ├── product-service/     # 품목/카테고리, jsonb 태그, GIN 인덱스 + internal API
│   ├── inventory-service/   # 4-tier 창고/FIFO/이동전표, ProductClient + InternalTokenFilter
│   ├── slip-service/        # 출고/입고 전표(STI), 9단계 라이프사이클, InventoryClient 연계
│   └── ...                  # (Accounting 부터 추가 예정)
├── clients/                 # 클라이언트 앱
│   ├── desktop/             # Electron (Phase 2 마무리)
│   ├── web/
│   │   └── design-system/   # ✅ 16 컴포넌트 + Storybook
│   └── mobile/              # Phase 6
├── infrastructure/          # Docker compose, Postgres init, Prometheus, Grafana
├── shared/
│   └── common/              # BaseEntity, Role enum 7-tier, JwtTokenProvider, ApiResponse, BusinessException
└── .github/workflows/       # CI (assemble + test + Testcontainers)
```

## 📌 의사결정 체인 (워크플로우)

```
4-team 에이전트(BE/FE/QA/DevOps) → TM 자동 승인 → PM 자동 승인 → 개발책임자 최종 승인(머지)
```

- **TM(팀장)** 과 **PM** 은 모두 Claude 에이전트가 수행 (자동 승인 코멘트)
- **개발책임자** 는 GitHub PR 머지 버튼 = 유일한 최종 승인
- 슬라이스 1건당 **팀별 PR 4건 분리** + 필요 시 hotfix PR
- 모든 PR/Issue/commit 한국어, 권한은 풀네임(MASTER/MANAGER/...)

## 🛠 개발 환경 셋업

### 사전 요구사항
- **JDK 17** (Eclipse Temurin 권장) — `JAVA_HOME` 설정 필수
- **Docker Desktop** — 인프라 스택 + Testcontainers IT
- **Node.js 24+** — 디자인 시스템 / Storybook
- **gh CLI 2.92+** — GitHub Issue/PR 자동화
- **Claude Max 구독** + 모바일 Claude 앱 — Remote Control 원격 지시 (옵션)
- Gradle / Maven은 별도 설치 불필요 — 프로젝트 내 `gradlew` 사용

### 빌드 & 실행

```bash
# 인프라 스택 기동 (PostgreSQL/Redis/RabbitMQ/Elasticsearch/MinIO + Prometheus/Grafana)
docker compose -f infrastructure/docker-compose.yml up -d

# 전체 모듈 빌드 + 단위 테스트
./gradlew build

# Testcontainers IT 포함 전체 테스트 (Docker 가동 필수)
./gradlew test

# 개별 서비스 실행
./gradlew :services:eureka-server:bootRun     # http://localhost:8761
./gradlew :services:api-gateway:bootRun       # http://localhost:8080
./gradlew :services:auth-service:bootRun      # http://localhost:8081
./gradlew :services:logging-service:bootRun   # http://localhost:8082
./gradlew :services:user-service:bootRun      # http://localhost:8083
./gradlew :services:product-service:bootRun   # http://localhost:8084
./gradlew :services:inventory-service:bootRun # http://localhost:8085
./gradlew :services:slip-service:bootRun      # http://localhost:8086
```

### 환경변수 (per-service)

서비스별 런타임 환경변수는 `infrastructure/env-templates/<service>.env` 에서 복사:

```bash
cp infrastructure/env-templates/slip-service.env services/slip-service/.env
```

**slip-service** (Slice B / notification-slice-B 후속) 추가 env:

| 변수 | 용도 | local (H2) | dev/staging/prod |
|------|------|-----------|------------------|
| `SOLAPI_API_KEY` | Solapi 인증 | 미사용 (Mock) | 필수 |
| `SOLAPI_API_SECRET` | Solapi 시크릿 | 미사용 (Mock) | 필수 |
| `SOLAPI_SENDER_PHONE` | 발신번호 (사전등록) | 미사용 (Mock) | 필수 |
| `SOLAPI_BASE_URL` | Solapi API 엔드포인트 | 미사용 (Mock) | `https://api.solapi.com` 기본값 |

H2 local 프로파일은 `MockSmsGateway` 자동 활성으로 SOLAPI 변수 미설정 가능.
CI 의 `gradle test` 도 test 프로파일이 Mock 활성 → SOLAPI env 주입 불필요.

### 디자인 시스템 (Storybook)

```bash
cd clients/web/design-system
npm install
npm run storybook    # http://localhost:6006
npm run build        # tsc + vite + dts
```

### CI

`.github/workflows/ci.yml` 가 PR / main push 마다 자동 실행:
- JDK 17 Temurin + Gradle 캐시
- assemble + test (Testcontainers IT 포함)
- 테스트 리포트 아티팩트 14일 보존
- JUnit 결과 PR check run 자동 표시

### 프로젝트 위치 권장

이 프로젝트는 **`C:\dev\SamhanLogis`** 같은 ASCII 전용 경로에 두는 것을 권장합니다.
한국어 경로(예: `바탕 화면`) 하위에 두면 JDK 17의 `@argfile` 인코딩 한계로
일부 Gradle 작업(특히 테스트)이 `ClassNotFoundException`으로 실패할 수 있습니다.

## 🧰 운영 가드 / 컨벤션

다음 가드들이 메모리(`~/.claude/projects/.../memory/`)에 영구 저장돼 모든 슬라이스에 자동 적용:

- **BaseEntity 7 audit 컬럼 필수** — created_at/by, modified_at/by, deleted_at/by, is_deleted
- **Soft-delete 전용** — `@SQLRestriction("is_deleted = false")`, hard delete 금지
- **권한 7단계 풀네임** — MASTER/MANAGER/DEVELOPER/SALES/ACCOUNTANT/WAREHOUSE/INVENTORY
- **DB 컬럼 타입 가드** — 짧은 문자열은 모두 `VARCHAR(N)`, `CHAR(N)` 금지 (PostgreSQL bpchar mismatch 회피)
- **Internal token 가드** — prod 프로파일에서 `dev-internal-token-change-me` 사용 시 부팅 거부 (`InternalTokenGuard`)
- **PowerShell 파일 쓰기 금지** — PR/Issue body 는 Write tool 또는 heredoc (UTF-16 BOM 한글 깨짐 회피)
- **PR 본문 commit-pinned 스크린샷** — `https://raw.githubusercontent.com/<owner>/<repo>/<sha>/<path>` 형식
- **gradlew 실행 권한** — Windows 커밋 시 `git update-index --chmod=+x gradlew` 필수
- **PM 통합 풀빌드 사전 검증** — 4-team PR 발행 전 BE+QA 풀빌드 + IT 컨텍스트 부팅(Hibernate validate) + 시나리오 시연 3 layer 의무

## 📄 라이선스

Proprietary - (주)삼한공조시스템 내부 사용 전용
