# Inventory Service 첫 슬라이스 — DevOps 검토 리포트

> 슬라이스: inventory-first-slice
> base: eb611bf (Merge pull request #13 — product/feature/qa-tests-and-report)
> 작성일: 2026-05-04
> 작성: Team-DevOps (worktree `agent-a7348c6c9b2696df4`)

---

## 1. 인프라 변경 점검

### 1.1 신규 자원 (예정)
- **신규 마이크로서비스 모듈**: `:services:inventory-service` (port 8085, DB `inventory_db`)
- BE 가 별도 worktree 에서 아래를 작성 중:
  - `services/inventory-service/build.gradle` (Spring Boot + JPA + Flyway + RestClient + Testcontainers + springdoc)
  - `services/inventory-service/src/main/resources/application.yml`
  - `services/inventory-service/src/main/resources/db/migration/V1__init.sql`
  - 도메인/서비스/컨트롤러 레이어

### 1.2 점검 결과 — 이미 준비된 자원

| 항목 | 위치 | 상태 |
|---|---|---|
| `inventory_db` PostgreSQL DB | `infrastructure/postgres/init/01-create-databases.sql:8` | **존재** (추가 작업 불필요) |
| `uuid-ossp` / `pgcrypto` 확장 (inventory_db) | `infrastructure/postgres/init/02-extensions.sql:17-19` | **존재** |
| API Gateway 라우트 `/api/inventory/**` | `services/api-gateway/src/main/resources/application.yml:54-60` | **존재** (`lb://inventory-service` + StripPrefix=1 + JwtAuthentication) |
| `INTERNAL_AUTH_TOKEN` env var | `infrastructure/.env.example:31` | **존재** |
| `JWT_SECRET` env var | `infrastructure/.env.example:36` | **존재** |
| Eureka 클라이언트 자동 등록 | `application.yml` 의 `eureka.client.service-url.defaultZone` 표준 패턴 | BE 측에서 동일 패턴 채택 시 자동 |

```sql
-- 01-create-databases.sql:8 (이미 등재)
CREATE DATABASE inventory_db   OWNER samhan;

-- 02-extensions.sql:17-19 (이미 등재)
\c inventory_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

```yaml
# api-gateway/application.yml:54-60 (이미 등재)
- id: inventory-service
  uri: lb://inventory-service
  predicates:
    - Path=/api/inventory/**
  filters:
    - StripPrefix=1
    - JwtAuthentication
```

> **결론**: postgres init / gateway 라우트 / .env 표준 — DevOps 작업 모두 사전 완료. PM 통합 시 인프라 파일 수정 불필요.

### 1.3 PM 통합 시 반드시 처리해야 할 작업 (worktree 외부)

DevOps 권한 외 영역이지만 누락 시 CI 가 모듈을 인식 못 하므로 명시:

- **`settings.gradle`** (line 25 `':services:product-service'` 다음에 추가)
  ```gradle
  include 'services:inventory-service'
  project(':services:inventory-service').projectDir = file('services/inventory-service')
  ```
- **루트 `build.gradle`** 의 `leafProjects` 리스트 (line 37-45) 에 `':services:inventory-service'` 추가.

이 두 등록이 누락되면 `./gradlew assemble` 단계에서 inventory-service 가 빌드 대상에서 제외됨.

---

## 2. 보안 가드

### 2.1 InternalTokenGuard 적용 (필수 — 본 슬라이스부터)

본 슬라이스는 **이전 슬라이스와 결정적으로 다름**: inventory-service 가 product-service `/api/products/internal/lookup` 을 호출하므로 양측에 `InternalTokenGuard` 가 필요.

| 서비스 | 본 슬라이스에서 역할 | InternalTokenGuard |
|---|---|---|
| product-service | 피호출 측 (`/products/internal/lookup` 노출) | **신규 적용 필요** (직전 product 슬라이스에서는 미적용 — `docs/devops/product-service-review.md §2` 메모 참고) |
| inventory-service | 호출 측 (`X-Internal-Token` 헤더 전송) | **적용 필요** (헤더 주입은 클라이언트 인터셉터로) |

**점검 항목** (BE worktree 머지 후 PM 단계에서 확인):
- product-service 의 `/api/products/internal/**` 라우트 (gateway 우회 + `InternalTokenGuard` 필터)
- inventory-service 의 RestClient 인터셉터가 `X-Internal-Token: ${INTERNAL_AUTH_TOKEN}` 헤더 자동 주입
- 양측 application.yml 의 `app.security.internal.token: ${INTERNAL_AUTH_TOKEN}` 동일 키 참조
- prod 프로파일에서 dev 기본값 (`dev-internal-token-change-me`) 사용 시 `InternalTokenGuard` 가 부팅 거부 (auth/user-service 와 동일 패턴)

### 2.2 Q1=택B (gateway 우회 + LoadBalanced RestClient 직접 호출) — 의도적 분기

개발책임자 결재 사항. 본 슬라이스의 inventory→product 호출은 gateway 를 우회하고 Eureka 디스커버리 기반 LoadBalanced RestClient (`http://product-service/products/internal/lookup`) 로 직접 호출.

- **장점**: gateway hop 1회 절감 (latency ↓), gateway 의 JWT 검증 부하 회피
- **위험**: gateway 의 라우트 정책 (예: rate limit, CORS) 우회 → 내부 endpoint 는 외부 노출 금지 가드가 더 중요
- **방어**: `InternalTokenGuard` + `/internal/**` path prefix + gateway 라우트 미등록 (외부 접근 자체 불가)

### 2.3 Eureka 등록
- `spring.application.name=inventory-service` (게이트웨이 라우팅 + 서비스 디스커버리 키 — gateway 가 `lb://inventory-service` 로 참조)
- `eureka.client.service-url.defaultZone=${EUREKA_URL:http://localhost:8761/eureka/}` (product-service 와 동일 표준)

---

## 3. CI 영향

### 3.1 `.github/workflows/ci.yml`

- **자동 포함**: `./gradlew assemble`, `./gradlew test` 가 전 모듈 대상 (특정 모듈명 하드코딩 없음). `settings.gradle` 등록만으로 자동 빌드.
- **테스트 리포트 아티팩트**: `services/*/build/reports/tests/test/` 와일드카드 → inventory-service 결과 자동 수집.
- **Testcontainers IT**: CI runner `ubuntu-latest` 는 Docker 가용. `docker version` / `docker ps` 가드 step 존재 (line 38-41).
- **빌드 시간 영향 추정**: +40초 ~ +60초 (Testcontainers IT 의 PostgreSQL 컨테이너 기동 시간 포함).

### 3.2 gradlew 실행 권한

- 메모리 `feedback_gradlew_exec_bit.md` 가드 — 신규 모듈 추가는 root `gradlew` 만 영향, 그것은 이미 chmod +x. CI workflow 의 line 35-36 `chmod +x ./gradlew` 가 추가 안전망 역할.
- inventory-service 디렉토리 내부에는 자체 gradlew 없으므로 추가 chmod 작업 불필요.

### 3.3 한글 path 트랩

- 메모리 `feedback_korean_path_jdk.md` 가드 — 본 worktree 는 `C:\dev\SamhanLogis\.claude\worktrees\...` 영문 path 라 영향 없음. CI 도 ubuntu-latest checkout path (영문) 라 영향 없음.

---

## 4. 모니터링 / 운영

### 4.1 메트릭
- Spring Boot Actuator `/actuator/prometheus` 자동 노출 권장 (product-service `application.yml:33` 과 동일 `management.endpoints.web.exposure.include: health,info,prometheus` 패턴).
- 후속 권고: `inventory_balance` (gauge by warehouse × product), `stock_movement_total` (counter by direction) 메트릭은 본 슬라이스 범위 외 — Phase 2 마무리 시점에 추가.

### 4.2 로깅
- 본 슬라이스에서 RabbitMQ 로 logging-service 에 이벤트 발행은 도입하지 않는 것으로 가정 (BE 작업 범위 외). `services/logging-service` 의 `RabbitConfig` + `AuditLogConsumer` 패턴이 이미 정착되어 있으므로 후속 슬라이스 도입 비용 낮음.

### 4.3 데이터베이스
- 사이즈 추정: 4 창고 × 평균 200 제품 × 평균 5 lot ≈ 4,000 row (초기). 운영 1년 기준 50,000 ~ 100,000 row 예상.
- 단일 PostgreSQL 인스턴스로 충분. 핵심 인덱스 (`stock_balance(warehouse_id, product_id)`, `stock_movement(occurred_at desc)`) 만 잘 잡히면 부하 문제 없음.
- **동시성**: `StockBalance.@Version` 으로 optimistic lock. FIFO 출고 시 충돌 1회 재시도는 Service layer 에서 처리 (BE 책임).

---

## 5. 후속 슬라이스 권고

### 5.1 즉시 후속 (Phase 2 마무리)
- **Electron skeleton**: Inventory 의 WarehouseSelector + 출고 화면이 첫 활용처. desktop 패키징은 Phase 2 마무리에서 진행.
- **Storybook GitHub Pages 배포**: 디자인 시스템 누적 컴포넌트 (현재 13종) → 사내 디자인 검토 가속.

### 5.2 Slip Service 슬라이스 대비 권고 (Phase 3)

1. **재고 차감 트리거 패턴**
   Slip(출고) 발행 시 inventory-service 의 `/inventory/deduct` 동기 REST 호출 권장 (보상 트랜잭션 패턴 — Slip 실패 시 재고 복구 호출). 비동기 이벤트 기반은 일관성 윈도우가 길어져 출고 직후 잔량 조회 UX 가 깨짐.

2. **이벤트 발행 (read 측)**
   inventory-service 가 stock_movement 발생 시 RabbitMQ 이벤트 발행 → Slip 의 수정이력 / Dashboard 의 실시간 재고 위젯이 구독. 트리거(write)와 이벤트(read)의 방향이 다름에 유의.

3. **VIRTUAL 창고 활용**
   서비스 인보이스(공임만 청구, 실물 출고 없음)는 VIRTUAL 창고 출고로 처리 → Slip 에서 VIRTUAL 창고 선택 시 재고 차감 skip 분기. 본 슬라이스에서 VIRTUAL 창고 enum 도입했다면 Slip 슬라이스에서 그대로 활용 가능.

4. **창고 이동 + Slip 연계**
   `StockTransfer` RECEIVED 시점에 자동으로 입고 Slip 발행할지 (현재 가정: Slip 별도 발행) — Slip 슬라이스에서 의사결정 필요. 자동 연계 시 사용자 입력 부담 감소, 미연계 시 회계처리 유연성 증가.

### 5.3 운영 부채 (별도 작업)
- **JWT_SECRET 가드 추가**: 현재 `application.yml` dev 기본값 (`dev-secret-change-me-in-production-32bytes-min!`) 사용 시 prod 부팅 거부 가드 — `InternalTokenGuard` 와 동일 패턴으로 `JwtSecretGuard` 별도 슬라이스 권고.
- **권한 매트릭스 controller IT 확장**: 각 endpoint × 7 role 매트릭스 자동 생성 (현재 슬라이스 마다 수동 IT 작성 부담).

---

## 6. Plan 대비 의도적 변경

| 항목 | 결정 | 근거 |
|---|---|---|
| Q1 = 택B | gateway 우회 + LoadBalanced RestClient 로 product-service `/internal/lookup` 직접 호출 | 개발책임자 결재. latency ↓, gateway hop 1회 절감 |

(향후 PM 의 분기 결정 사항 추가 누적)

---

## 7. 결론

- ✅ **인프라 변경 사항 합리적, 본 슬라이스 머지 가능**
- ✅ **postgres init / gateway 라우트 / .env 표준 — DevOps 작업 사전 완료** (인프라 파일 수정 불필요)
- ⚠️ **PM 통합 시 `settings.gradle` + 루트 `build.gradle` `leafProjects` 에 `:services:inventory-service` 등록 필수**
- ⚠️ **product-service 에 `InternalTokenGuard` 신규 적용 필요** (본 슬라이스부터 inventory→product 내부 호출 시작)
- 🔜 **후속**: Slip Service 슬라이스 시점에 재고 차감 트리거 + 이벤트 발행 패턴 도입
- 🔜 **운영 부채**: `JwtSecretGuard` 별도 슬라이스, 권한 매트릭스 controller IT 자동 생성
