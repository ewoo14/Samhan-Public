# SamhanLogis — (주)삼한공조시스템 자체 통합 플랫폼

> 삼성 시스템에어컨 공식 파트너사 (주)삼한공조시스템의 자체 물류·회계·견적·주문 통합 플랫폼.
> 14 backend MSA + 5 client (web 2 / desktop 1 / mobile 2) + legacy 마이그레이션 (견적서 / 주문서 / 장기미수) 으로 구성된다.

---

## 프로젝트 개요

| 항목       | 내용                                                                               |
| ---------- | ---------------------------------------------------------------------------------- |
| 아키텍처   | MSA (service-per-DB), Spring Cloud Gateway + Eureka + Resilience4j 회로차단        |
| 인증       | JWT HS256 (auth-service) + gateway HeaderAuthenticationFilter + Internal-Token     |
| 배포 형태  | 내부: Electron (Windows .exe) / 외부: Web (estimate / order) + Mobile (Expo)       |
| 진척률     | Phase 0 ~ 8 완료 (PR #88 / #89 / #90), Phase 9 진입 준비 완료                      |

---

## 기술 스택

### Backend
- Java 17 (Eclipse Temurin) + Spring Boot 3 + Spring Cloud
- PostgreSQL 15 (service-per-DB) + Flyway 마이그레이션
- Redis (세션/캐시) + RabbitMQ (이벤트 스트림) + Elasticsearch (로그)
- Resilience4j circuit breaker + Solapi/알리고 SMS 게이트웨이

### Frontend / Client
- `clients/desktop` — Electron 33 + electron-vite + React 18 + zustand
- `clients/web/design-system` — Vite + TypeScript + Storybook (21 컴포넌트)
- `clients/web/order-app` v4 — Vite + React + legacy `partner-order/index.html` 9427 라인 임베드 + PWA
- `clients/web/estimate-app` v2 — Node.js + Express + EJS + legacy estimate 18614 라인 1:1 변환 (B2 옵션)
- `clients/mobile` v4 — Expo SDK 53 + react-native-webview (order-app v4 임베드)
- `clients/mobile-staff` v3 — Expo SDK 53 + react-native-webview (estimate-app v2 임베드)

### DevOps / QA
- Docker / Docker Compose (인프라) + GitHub Actions (CI)
- Cloudflare Pages (order-app v4) / Render (estimate-app v2 + order-app mirror 정의) / 카페24 (테스트만, 배포 보류)
- Playwright (web + electron + mobile emul, 60+ cell)
- Detox (mobile / mobile-staff, iOS sim + Android emul)

---

## 디렉토리 구조

```
SamhanLogis/
├── README.md                  # 본 파일
├── ROADMAP.md                 # 단계별 로드맵 (Phase 0 ~ 10)
├── settings.gradle / build.gradle / gradlew
├── shared/
│   └── common/                # BaseEntity, Role enum 7-tier, JwtTokenProvider, ApiResponse, BusinessException
├── services/                  # 14 backend MSA (Spring Boot 3 / Java 17)
│   ├── eureka-server/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── user-service/
│   ├── product-service/
│   ├── inventory-service/
│   ├── slip-service/
│   ├── accounting-service/
│   ├── partner-auth-service/  # Phase 6 M2 (8091)
│   ├── dc-config-service/     # Phase 6 M3 (8089)
│   ├── partner-order-service/ # Phase 6 M4 (8088)
│   ├── logging-service/       # Phase 1 (8082)
│   └── ...                    # Phase 9 신규: groupware (8092) / notification (8093) / dashboard (8094) / partner (8095)
│                              # Phase 10 신규: migration (8096)
├── clients/
│   ├── desktop/               # Electron + electron-vite + React 18
│   ├── web/
│   │   ├── design-system/     # Storybook + 21 컴포넌트
│   │   ├── order-app/         # Vite + legacy partner-order 임베드 (v4)
│   │   └── estimate-app/      # Express + EJS + legacy estimate 임베드 (v2)
│   ├── mobile/                # Expo + RN WebView (order-app v4)
│   └── mobile-staff/          # Expo + RN WebView (estimate-app v2)
├── qa/
│   ├── playwright/            # web + electron + mobile emul e2e (60+ cell)
│   └── detox/                 # iOS/Android e2e (6 시나리오)
├── infrastructure/
│   ├── docker-compose.yml     # PostgreSQL + Redis + RabbitMQ + Elasticsearch + MinIO + Prometheus + Grafana
│   ├── postgres/init/         # 10 service DB 자동 생성 + extension
│   ├── prometheus/ + grafana/
│   ├── nginx/                 # 서브도메인 stub
│   ├── render/                # Render Blueprint (estimate-app + order-app mirror)
│   ├── cafe24/                # SSH 테스트 script (배포 X 보류)
│   ├── env-templates/
│   └── security/
├── migration/
│   └── decisions/DECISIONS.md # 누적 결정 기록
└── docs/                      # PM / backend / frontend / uiux / devops / qa / migration / dev-reports
```

---

## 빠른 시작

### 사전 요구사항

- JDK 17 (Eclipse Temurin) — `JAVA_HOME` 설정 필수
- Docker Desktop — 인프라 stack + Testcontainers IT
- Node.js 20+ (권장 22+) — client 빌드
- gh CLI 2.92+ — GitHub Issue/PR
- 영문 경로 권장 (`C:\dev\SamhanLogis`) — 한글 path 는 JDK 17 `@argfile` 인코딩 한계로 일부 Gradle 작업이 실패할 수 있음

### Service 인벤토리 + 포트 (Phase 8 기준 + Phase 9/10 예정 포함)

| Service                  | Port | DB                  | 도메인 / 비고                              | 상태             |
| ------------------------ | ---- | ------------------- | ------------------------------------------ | ---------------- |
| eureka-server            | 8761 | -                   | service discovery                          | Phase 1 (운영)   |
| api-gateway              | 8080 | -                   | reactive routing + HeaderAuthenticationFilter | Phase 1 (운영) |
| auth-service             | 8081 | auth_db             | JWT issuer + account                       | Phase 1 (운영)   |
| logging-service          | 8082 | logging_db          | RabbitMQ → Elasticsearch                   | Phase 1 (운영)   |
| user-service             | 8083 | user_db             | 16명 시드 + AuthClient saga                | Phase 2 (운영)   |
| product-service          | 8084 | product_db          | jsonb 태그 + GIN + Google Sheets cron + by-code | Phase 2 (운영) |
| inventory-service        | 8085 | inventory_db        | 4-tier 창고 + FIFO + 22 endpoint           | Phase 2 (운영)   |
| slip-service             | 8086 | slip_db             | 10단계 라이프사이클 + 전자서명 + M5 `/from-*` | Phase 3 (운영) |
| accounting-service       | 8087 | accounting_db       | 한국 일반기업회계기준 65 row 시드          | Phase 4 (운영)   |
| partner-order-service    | 8088 | partner_order_db    | confirm 흐름 + outbox + 16종 bootstrap     | Phase 6 (운영)   |
| dc-config-service        | 8089 | dc_config_db        | DC 5겹 가드 + Partner master owner         | Phase 6 (운영)   |
| partner-auth-service     | 8091 | partner_auth_db     | 거래처 자체 인증 7 endpoint                | Phase 6 (운영)   |
| **groupware-service**    | **8092** | **groupware_db** | **결재선 + 메신저 + 일정**                | **Phase 9 예정** |
| **notification-service** | **8093** | **notification_db** | **푸시/이메일/SMS 통합 라우터**         | **Phase 9 예정** |
| **dashboard-service**    | **8094** | **dashboard_db** | **KPI + 실시간 재고 + 매출**              | **Phase 9 예정** |
| **partner-service**      | **8095** | **partner_db**   | **거래처 마스터 + 신용한도 + 거래내역**    | **Phase 9 예정** |
| **migration-service**    | **8096** | (별도 결정)       | **ECount 일괄 이관 + 장기미수**            | **Phase 10 예정**|

> Phase 9 신규 4 service 의 포트 / DB 확정은 `migration/decisions/DECISIONS.md` D-P9-01 참조.
> Phase 10 migration-service (8096) 는 partner-service (8095) 와 충돌 회피한 신규 포트 — D-P9-01 cascade.

### 인프라 + backend 빌드

```bash
# 1) 인프라 stack
docker compose -f infrastructure/docker-compose.yml up -d

# 2) 전체 모듈 컴파일 (테스트 제외)
./gradlew assemble

# 3) 단위 + IT (Docker 가용 환경)
./gradlew test

# 4) 개별 서비스 실행
./gradlew :services:eureka-server:bootRun           # http://localhost:8761
./gradlew :services:api-gateway:bootRun             # http://localhost:8080
./gradlew :services:auth-service:bootRun            # http://localhost:8081
./gradlew :services:user-service:bootRun            # http://localhost:8083
./gradlew :services:product-service:bootRun         # http://localhost:8084
./gradlew :services:inventory-service:bootRun       # http://localhost:8085
./gradlew :services:slip-service:bootRun            # http://localhost:8086
./gradlew :services:accounting-service:bootRun      # http://localhost:8087
./gradlew :services:partner-auth-service:bootRun    # http://localhost:8091
./gradlew :services:dc-config-service:bootRun       # http://localhost:8089
```

### Client 빌드

```bash
# 디자인 시스템 + Storybook
cd clients/web/design-system && npm install && npm run storybook   # http://localhost:6006

# order-app v4 (Vite + 임베드)
cd clients/web/order-app && npm install && npm run dev             # http://localhost:5180

# estimate-app v2 (Express + EJS)
cd clients/web/estimate-app && npm install && npm run dev          # http://localhost:5183

# desktop (Electron)
cd clients/desktop && npm install && npm run dev

# mobile v4 (Expo, order-app 임베드)
cd clients/mobile && npm install --legacy-peer-deps && npm run start

# mobile-staff v3 (Expo, estimate-app 임베드)
cd clients/mobile-staff && npm install --legacy-peer-deps && npm run start
```

### QA 실행

```bash
# Playwright (web + electron + mobile emul)
cd qa/playwright && npm install && npx playwright install --with-deps && npm test

# Detox (iOS / Android)
cd qa/detox && npm install && npm run build:ios && npm run test:ios
```

---

## Phase 진행 상태

| Phase | 상태       | 머지 PR 범위           | 비고                                                                |
| ----- | ---------- | ---------------------- | ------------------------------------------------------------------- |
| 0     | 완료       | -                      | 가드 정립                                                           |
| 1     | 완료       | #2 / #3 / #5           | infrastructure + auth + eureka + logging + gateway                  |
| 2     | 완료       | #7 ~ #18 / #34 / #36   | user + product + inventory + Electron desktop 첫 슬라이스           |
| 3     | 완료       | #19 ~ #26              | slip-service 10단계 + 전자서명                                      |
| 4     | 완료       | #28                    | accounting-service (한국 일반기업회계기준 65 row 시드)              |
| 5     | 완료       | #30                    | SMS Aligo 마이그레이션                                              |
| 6     | 완료       | #38 ~ #80              | legacy 마이그레이션 (M1a / M2 / M3 / M4 / M5 + 5 client)            |
| 7     | 완료       | #81 ~ #87              | 호스팅 인프라 + e2e QA + 운영 가드 + UI 통합                        |
| 8     | **완료**   | **#88 / #89 / #90**    | AWS 호환성 가드 (12-factor + chained-default + ServiceDiscoveryClient + Secrets rotation spec + Phase 10 dry-run plan) |
| 9     | 진입 준비 | -                      | 잔여 도메인 (partner / groupware / notification / dashboard)        |
| 10    | 대기       | -                      | AWS 마이그레이션 + Migration Service (8096) + 운영 안정화           |

자세한 단계별 산출물 / 완료 조건 / PR 매트릭스는 `ROADMAP.md` 참조.

---

## Phase 6 ~ 8 머지된 주요 PR

### Phase 6 (legacy 마이그레이션 본격 구현)
- #38 M1a product-service 시드
- #50 / #53 web order-app v4 (Vite SPA + PWA)
- #51 / #54 desktop v4
- #52 mobile v4 (RN WebView)
- #58 estimate-app v2 (Express + EJS, B2 옵션)
- #67 / #70 legacy-v2 import + revert (별 프로젝트 분리)
- #68 / #75 product google sheets cron + 정정
- #69 RN client 통합 (Mobile + mobile-staff)
- #72 M2 partner-auth-service
- #73 estimate-app google sheets 직접 연동
- #76 Phase 6 backend 통합 (M2 + M3 + M4 + M5)
- #77 DEVOPS Cloudflare Pages workflow (order-app)
- #78 QA Playwright + Detox 셋업
- #79 client mock 일괄 제거
- #80 Phase 6 마무리 (회고 + DECISIONS + Phase 7 readiness)

### Phase 7 (완료)
- #81 Phase 7 1차 (카페24 SSH script + Render Blueprint + Playwright 60 cell)
- #82 Phase 7 2차 (CSP / Slack 비동기 / visual regression / Detox 6)
- #83 Phase 7 3차 (product by-code + QA tautology fix + render mirror + dark-mode)
- #84 Phase 7 4차 (DS 토큰 + body 바인딩 + toggleTheme + visual baseline)
- #85 Phase 7 5차 docs (README + ROADMAP + DECISIONS Phase 7)
- #86 Phase 7 4차 잔여 (통일 토큰 + Pretendard + RN graceful 폰트 hook)
- #87 Phase 7 5/6차 (self-host font + helmet+CSP + desktop CSP + 회고 + Phase 8 plan)

### Phase 8 (완료 — AWS 호환성 가드)
- #88 Phase 8 1차 (12-factor 12/12 + RDS 호환 22 file 검증 + 환경변수 표준 plan + AWS 서비스 매핑 17건)
- #89 Phase 8 2차 (`shared:discovery-abstraction` 신규 + chained-default 환경변수 + Secrets Manager rotation lambda spec)
- #90 Phase 8 3차 (AWS 마이그레이션 dry-run plan 14 section + Phase 8 회고 + Phase 9 진입 plan + 본 docs 누락 8 영역 보강)

---

## 운영 가드 / 컨벤션

다음 가드들은 메모리에 영구 저장되어 모든 슬라이스에 자동 적용된다.

- **BaseEntity 7 audit 컬럼** — created_at/by, modified_at/by, deleted_at/by, is_deleted
- **Soft-delete 전용** — `@SQLRestriction("is_deleted = false")`, hard delete 금지
- **권한 7단계 풀네임** — MASTER / MANAGER / DEVELOPER / SALES / ACCOUNTANT / WAREHOUSE / INVENTORY
- **DB 컬럼 타입 가드** — `VARCHAR(N)` 만 허용, `CHAR(N)` 금지 (PostgreSQL bpchar mismatch 회피)
- **Internal token 가드** — prod 프로파일에서 `dev-internal-token-change-me` 사용 시 부팅 거부
- **PowerShell 파일 쓰기 금지** — PR/Issue body 는 Write tool 또는 heredoc 사용 (UTF-16 BOM 한글 깨짐 회피)
- **PR 본문 commit-pinned 스크린샷** — `https://raw.githubusercontent.com/<owner>/<repo>/<sha>/<path>` 형식
- **gradlew 실행 권한** — Windows 커밋 시 `git update-index --chmod=+x gradlew` 필수
- **UUID 비공개** — 모든 클라이언트 화면에서 UUID 노출 금지, 비즈니스 식별자 (slipNo / 창고 코드 / modelCode / partnerName) 만 노출
- **한국어 commit / PR / Issue 의무** — prefix 와 trailer 만 영문 예외

---

## 참조 문서

| 분류                       | 위치                                                                |
| -------------------------- | ------------------------------------------------------------------- |
| 로드맵                     | `ROADMAP.md`                                                        |
| 누적 결정                  | `migration/decisions/DECISIONS.md`                                  |
| Phase 6 회고               | `docs/dev-reports/phase6-retrospective.md`                          |
| Phase 7 readiness          | `docs/migration/phase7/M-PHASE-7-readiness.md`                      |
| estimate-app 호스팅 결정    | `docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md`          |
| Phase 7 dev report         | `docs/dev-reports/phase7-step-{1,2,3}.md`                           |
| Phase 8 readiness / guards | `docs/migration/phase8/M-PHASE-8-readiness.md` + `M-AWS-COMPATIBILITY-guards.md` |
| Phase 8 환경변수 표준       | `docs/migration/phase8/M-ENV-STANDARDIZATION.md`                    |
| Phase 8 Secrets rotation 스펙 | `docs/migration/phase8/M-SECRETS-ROTATION-spec.md`               |
| Phase 8 회고               | `docs/dev-reports/phase8-retrospective.md`                          |
| Phase 9 readiness          | `docs/migration/phase9/M-PHASE-9-readiness.md`                      |
| Phase 10 AWS dry-run plan  | `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`                 |
| dev-reports 누적           | `docs/dev-reports/`                                                 |

---

## 라이선스

Proprietary — (주)삼한공조시스템 내부 사용 전용.
