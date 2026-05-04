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
| **기술 스택** | Java/Spring Boot, PostgreSQL, React, Electron |

## 🏗️ 시스템 구성

- **13개 마이크로서비스**: API Gateway, Eureka, Auth, User, Product, Inventory, Slip, Accounting, Partner, Groupware, Notification, Logging, Dashboard
- **26명 에이전트 팀**: PM 1, Backend 10, Frontend 7, UI/UX 3, DevOps 5
- **6단계 로드맵**: 약 28주(7개월) 예상

## 📁 프로젝트 구조

```
SamhanLogis/
├── docs/                    # 전체 문서
│   ├── PM/                  # PM 총괄 문서
│   ├── backend/             # 백엔드 팀 문서
│   ├── frontend/            # 프론트엔드 팀 문서
│   ├── uiux/                # UI/UX 팀 문서
│   └── devops/              # DevOps 팀 문서
├── services/                # 백엔드 마이크로서비스
├── clients/                 # 클라이언트 앱
│   ├── desktop/             # Electron 데스크톱 앱
│   ├── web/                 # React 웹 앱
│   └── mobile/              # React Native 모바일 앱
├── infrastructure/          # Docker, K8s 설정
└── shared/                  # 공유 라이브러리
```

## 📌 의사결정 체인

```
개발자 → TM(팀장) 승인 → PM 승인 → 대표 최종 승인 → 개발 착수
```

## 🛠 개발 환경 셋업

### 사전 요구사항
- **JDK 17** (Eclipse Temurin 권장) — `JAVA_HOME` 설정 필수
- **Docker Desktop** — 인프라 스택 (PostgreSQL, Redis, RabbitMQ, Elasticsearch, MinIO, Prometheus, Grafana)
- **Node.js 24+** — 프론트엔드 (Phase 2부터)
- Gradle / Maven은 별도 설치 불필요 — 프로젝트 내 `gradlew` 사용

### 빌드 & 실행

```bash
# 인프라 스택 기동
docker compose -f infrastructure/docker-compose.yml up -d

# 전체 모듈 빌드
./gradlew build

# 개별 서비스 실행
./gradlew :services:eureka-server:bootRun     # http://localhost:8761
./gradlew :services:api-gateway:bootRun       # http://localhost:8080
./gradlew :services:auth-service:bootRun      # http://localhost:8081
./gradlew :services:logging-service:bootRun   # http://localhost:8082
```

### 프로젝트 위치 권장

이 프로젝트는 **`C:\dev\SamhanLogis`** 같은 ASCII 전용 경로에 두는 것을 권장합니다.
한국어 경로(예: `바탕 화면`) 하위에 두면 JDK 17의 `@argfile` 인코딩 한계로
일부 Gradle 작업(특히 테스트)이 `ClassNotFoundException`으로 실패할 수 있습니다.

## 📄 라이선스

Proprietary - (주)삼한공조시스템 내부 사용 전용
