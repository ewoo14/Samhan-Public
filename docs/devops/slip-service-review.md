# Slip Service 첫 슬라이스 — DevOps 검토 리포트

> 슬라이스: slip-first-slice
> base: c6c0f9a
> 작성일: 2026-05-04
> 작성: Team-DevOps (worktree `agent-a0edf29e7c15a2fa5`)

---

## 1. 인프라 변경

### 1.1 신규 자원 (예정)
- **신규 마이크로서비스 모듈**: `:services:slip-service` (port 8086, DB `slip_db`)
- BE 가 별도 worktree 에서 아래를 작성 중:
  - `services/slip-service/build.gradle` (Spring Boot + JPA + Flyway + RestClient + Testcontainers + springdoc)
  - `services/slip-service/src/main/resources/application.yml`
  - `services/slip-service/src/main/resources/db/migration/V1__init.sql`
  - 도메인/서비스/컨트롤러 레이어 (Slip + SlipLine + DeliveryTag, STI 패턴)

### 1.2 점검 결과 — 이미 준비된 자원

| 항목 | 위치 | 상태 |
|---|---|---|
| `slip_db` PostgreSQL DB | `infrastructure/postgres/init/01-create-databases.sql:9` | **존재** (추가 작업 불필요) |
| `uuid-ossp` / `pgcrypto` 확장 (slip_db) | `infrastructure/postgres/init/02-extensions.sql:21-23` | **존재** |
| API Gateway 라우트 `/api/slips/**` | `services/api-gateway/src/main/resources/application.yml:38-44` | **존재** (`lb://slip-service` + StripPrefix=1 + JwtAuthentication) |
| `INTERNAL_AUTH_TOKEN` env var | `infrastructure/.env.example:31` | **존재** |
| `JWT_SECRET` env var | `infrastructure/.env.example:36` | **존재** |
| Eureka 클라이언트 자동 등록 | `application.yml` 의 `eureka.client.service-url.defaultZone` 표준 패턴 | BE 측에서 동일 패턴 채택 시 자동 |

```sql
-- 01-create-databases.sql:9 (이미 등재)
CREATE DATABASE slip_db        OWNER samhan;

-- 02-extensions.sql:21-23 (이미 등재)
\c slip_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

```yaml
# api-gateway/application.yml:38-44 (이미 등재)
- id: slip-service
  uri: lb://slip-service
  predicates:
    - Path=/api/slips/**
  filters:
    - StripPrefix=1
    - JwtAuthentication
```

### 1.3 결론 — DevOps 직접 수정 없음
- 인프라 3 파일 (DB init, extensions, gateway route) 모두 사전에 등록됨 → **본 슬라이스에서 인프라 파일 수정 불필요**
- `infrastructure/.env.example` 점검: `SLIP_SERVICE_PORT` 같은 신규 항목 불필요. `application.yml` 의 `${SLIP_SERVICE_PORT:8086}` 형태로 default 처리되면 충분 (BE 측 표준 패턴)

---

## 2. 보안 가드

### 2.1 Internal Token (서비스 간 직접 호출)
slip-service 는 gateway 를 우회해서 다음 두 서비스를 직접 호출:
- **slip-service → inventory-service** (4 endpoint)
  - `POST /inventory/reserve` — 수락 시 재고 예약
  - `POST /inventory/release` — 수락 후 거부 / 반려 시 예약 해제
  - `POST /inventory/deduct` — 처리 완료 시 차감
  - `POST /inventory/lots/inbound` — 입고 전표 처리 시 LOT 입고
- **slip-service → product-service** (1 endpoint)
  - `POST /products/internal/lookup` — 라인 productId 검증

### 2.2 InternalTokenFilter 부재 — **inventory-service 에 보강 필수**

| 서비스 | InternalTokenFilter | InternalTokenGuard | InternalAuthProperties |
|---|---|---|---|
| auth-service | 존재 (`.../auth/config/InternalTokenFilter.java`) | 존재 | 존재 |
| product-service | 존재 (`.../product/config/InternalTokenFilter.java`) | 존재 | 존재 |
| **inventory-service** | **부재** | 존재 (`InternalTokenGuard.java:17`) | 존재 (`InternalAuthProperties.java`) |
| slip-service | (BE 작성 의무) | (BE 작성 의무) | (BE 작성 의무) |

**문제**: inventory-service 의 SecurityConfig (`SecurityConfig.java:23-27`) 는 `HeaderAuthenticationFilter` 만 등록하고 있음. 즉 `/inventory/**` 는 gateway 가 주입하는 `X-User-*` 헤더만 신뢰하는 구조. slip-service 가 gateway 를 우회해서 `/inventory/reserve` 등을 호출할 때, `X-Internal-Token` 헤더 검증 servlet filter 가 없으면 **단순히 X-User-* 헤더를 위조해서 호출 가능**한 상태가 됨.

**권고 (PM 통합 시점 또는 BE 슬라이스 보강)**:
1. `services/inventory-service/src/main/java/com/samhanair/logis/inventory/config/InternalTokenFilter.java` 신규 — product-service 의 `InternalTokenFilter.java:23-61` 패턴 그대로 (path prefix 만 `/inventory/internal/` 또는 reserve/release/deduct/lots/inbound 4 endpoint 매칭으로 변경)
2. `SecurityConfig.java:27` 의 `addFilterBefore` 체인에 `InternalTokenFilter` 를 `HeaderAuthenticationFilter` 보다 앞에 등록
3. inventory controller 의 reserve/release/deduct/lots/inbound 4 endpoint 가 `/inventory/internal/...` prefix 로 이동하거나, InternalTokenFilter 의 path 매칭 로직이 기존 endpoint 도 커버하도록 분기

**slip-service 측**:
- `InternalTokenGuard` + `InternalAuthProperties` 작성 의무 (prod 부팅 시 dev default token 거부 — `inventory-service/InternalTokenGuard.java:37-43` 패턴)
- slip-service 의 RestClient 에 `X-Internal-Token` 헤더 자동 주입 (Bean 설정)

### 2.3 Eureka 등록
- `spring.application.name=slip-service` 자동 등록
- inventory-service / product-service 가 Eureka 에 정상 등록돼 있어 slip-service 의 `lb://inventory-service` / `lb://product-service` 호출 가능

---

## 3. CI 영향

### 3.1 GitHub Actions
- `.github/workflows/ci.yml:43-47` — `assemble` / `test` 가 전 모듈 와일드카드 (`./gradlew assemble`, `./gradlew test` — 모듈 지정 없음). settings.gradle 에 `:services:slip-service` 만 등록되면 자동 픽업 (BE 작성 의무).
- `chmod +x ./gradlew` (line 35-36) — Windows 커밋 방어 표준
- Testcontainers IT — `docker version / docker ps` (line 38-41) 로 가용성 확인 후 동작. slip-service IT 도 자동 실행됨.
- 빌드 시간 추가 추정: **+40초 ~ +1분** (assemble 약 +20초, test/IT 약 +20-40초). 본 슬라이스는 약 4 IT 정도라 무시할 수준.

### 3.2 테스트 리포트
- `actions/upload-artifact@v4` 가 `services/*/build/reports/tests/test/` 와일드카드로 잡으므로 slip-service 리포트도 자동 포함 (line 53-57)
- `mikepenz/action-junit-report@v4` 가 `**/build/test-results/test/TEST-*.xml` 와일드카드로 PR 코멘트 게시 (line 60-65)

---

## 4. 모니터링 / 운영

### 4.1 메트릭
- Spring Boot Actuator `/actuator/prometheus` 노출 권장 (Phase 1 표준 패턴, gateway `application.yml:94-101` 참조)
- 후속 비즈니스 메트릭:
  - `slip_count_by_status` (DRAFT / SENT / ACCEPTED / IN_PROGRESS / COMPLETED / REJECTED / CANCELLED ...)
  - `slip_acceptance_latency_seconds` (SENT → ACCEPTED 까지 경과)
  - `slip_completion_latency_seconds` (ACCEPTED → COMPLETED 까지 경과)

### 4.2 로깅
- slip-service → RabbitMQ → logging-service 이벤트 발행: **본 슬라이스 미도입, 후속 슬라이스에서 추가 권고**
- 단, 9단계 라이프사이클 전이는 audit 이력 필수 → BaseEntity 의 `modified_at` / `modified_by` 로 1차 충당
- HISTORY 복원 슬라이스 (Slip 2nd) 에서 별도 `slip_history` snapshot 테이블 + 수정 사유 + ApprovalLine 연계 도입 예정

### 4.3 데이터베이스 사이즈 추정
- 일 평균 50건 × 250 영업일 × 5년 ≈ **62500 row** + 라인 평균 3개 ≈ **187500 row (slip_line)** + 태그 평균 1개 ≈ **62500 row (delivery_tag)**
- 인덱스만 적절히 잡으면 단일 PG 인스턴스로 5년 이상 충분
- **partial unique index** 권고: `(slip_no) WHERE is_deleted = FALSE` — 같은 날 같은 번호 중복 방지 (Soft Delete 표준과 호환)

---

## 5. 후속 슬라이스 권고

### 5.1 즉시 후속 (Slip 2nd slice)
- **HISTORY 복원** — 수정 사유 입력 + 팀장 승인 + `slip_history` snapshot 테이블 + ApprovalLine 연계
- **출고일 변경** — UUID 추적 + 복원 옵션 A(기존 전표 수정) / B(신규 전표 발급 + 기존 CANCELLED)
- **긴급 수정 요청 워크플로우** — Slip 락 일시 해제 + 30분 타임아웃 + audit 로그

### 5.2 Phase 4 — Accounting Service 연계
- 입금/출금 전표 (현재 출고/입고만 — Q3=B)
- **출고 전표 COMPLETED 시 ar_receivable 자동 생성** (Plan §3.6 채권 관리 — partner-service 의 AR 모듈)
- **국세청 (홈택스) 전자세금계산서 API** 연계 — 매출 세금계산서 자동 발행
- **오픈뱅킹 API** — 입금 자동 매핑

### 5.3 Phase 5 — Notification + Dashboard
- WebSocket + SSE 실시간 동기화 (Plan §3.1)
- 새 전표 SENT 시 창고원에게 푸시 알림 (그룹웨어 messenger 모듈 활용)
- Dashboard 의 실시간 전표 카운트 위젯 (status 별 + 일별 트렌드)

### 5.4 운영 부채 (전 서비스 공통)
- **JWT_SECRET 가드** — `InternalTokenGuard` 와 동일한 prod 부팅 거부 패턴을 JWT 비밀키에도 적용 (현재 dev default 가 그대로면 prod 에서도 부팅됨)
- **권한 매트릭스 controller IT 자동 생성** — 각 endpoint × 7 role (MASTER / MANAGER / ACCOUNTING / SALES / WAREHOUSE / DRIVER / GUEST) 자동 매트릭스 검증
- **Storybook GitHub Pages 배포** — FE 디자인 시스템 (SlipStatusBadge / DeliveryTagSelector / SlipNumberDisplay 등) 시연 가시성

---

## 6. Plan 대비 의도적 변경 (Q&A 결정 사항)

| Q | 결정 | 사유 |
|---|---|---|
| Q1 | **A: STI** (Slip 1 테이블 + slip_type enum 으로 출고/입고 구분) | 첫 슬라이스 단순함 우선. CTI(class-table-inheritance) / TPC 는 도메인 분기 본격화 시 (Phase 4 Accounting) 재검토. |
| Q2 | **A: 수락→reserve / 처리완료→deduct / 수락 후 거부→release** | inventory-service 의 reserve/deduct 분리 패턴(Inventory 첫 슬라이스 에서 정의)을 자연 활용. 2-phase 보상 패턴. |
| Q3 | **B: 출고 + 입고만** (입금/출금/이동 후속) | Phase 1 범위 한정. 입금/출금/이동은 Phase 4 Accounting 에서 도입. |
| Q4 | **A: basic audit** (BaseEntity 7 audit fields 만) | HISTORY snapshot 은 Slip 2nd slice 에서 별도 테이블로. |
| Q5 | **B: 낙관적 락 (`@Version`) + 상태 전이 가드** | Redis 분산 락은 Phase 5 동시 다발 트래픽 시점에 도입. 현재는 1초당 < 1 건 수준이라 낙관적 락 충분. |

---

## 7. 결론

### 7.1 본 슬라이스 머지 가능 여부
- **인프라 변경 없음 — 머지 가능 (조건부)**
- 조건: **inventory-service 에 InternalTokenFilter 보강 필수** (서비스 간 직접 호출 보안 갭). 본 슬라이스 또는 즉시 후속 hotfix 슬라이스 중 택 1.

### 7.2 위험 요소
- slip-service ↔ inventory-service ↔ product-service 의 service-to-service 호출 체인 — **단일 트랜잭션 보상 패턴 검토 필요**
  - 현재는 실패 시 `BusinessException` 만 던지는 구조. reserve 성공 후 slip 저장 실패 시 reserve 가 고아로 남는 위험.
  - 1차 완화: `@Transactional` rollback hook 에서 release 호출. 2차 완화 (후속): Saga 패턴 + RabbitMQ 보상 메시지.
- 9단계 라이프사이클 전이 — 부정 전이 (e.g. CANCELLED → ACCEPTED) 가드를 enum-state-machine 으로 강제하는지 BE QA 시점 확인 필요.

### 7.3 다음 마일스톤
- Slip 2nd (HISTORY / 긴급수정) → Phase 4 Accounting (입금/출금 + AR + 홈택스) → Phase 5 Notification (WebSocket + Dashboard)
