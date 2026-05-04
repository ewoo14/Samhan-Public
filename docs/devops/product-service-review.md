# Product Service — DevOps 검토 리포트

**대상 슬라이스**: Product Service first slice (4-team parallel)
**작성**: Team-DevOps (worktree `agent-ad58f3ef85aa7fc8a`)
**기준 base**: main `1a998ea`

---

## 1. 인프라 영향 분석

### 1.1 데이터베이스 (`product_db`)

- **상태**: 이미 존재. 추가 작업 불필요.
- **확인 위치**: `infrastructure/postgres/init/01-create-databases.sql:7`
  ```sql
  CREATE DATABASE product_db     OWNER samhan;
  ```
- 신규 docker volume 초기화 시 자동 생성되며, 기존 dev 환경에서도 이미 가용.
- 스키마(테이블/인덱스)는 product-service 내 Flyway/JPA `ddl-auto` 정책에 따라 부트시 적용 (BE 영역).

### 1.2 CI 파이프라인 (`.github/workflows/ci.yml`)

- **상태**: 변경 불필요. 신규 모듈 자동 픽업.
- **확인**: `assemble` 및 `test` 단계가 전 모듈 대상 (`./gradlew assemble --no-daemon`, `./gradlew test --no-daemon`).
- Test 리포트 아티팩트 패턴(`services/*/build/reports/tests/test/`) 도 와일드카드라 product-service 결과 자동 수집.
- 단, `settings.gradle` / 루트 `build.gradle` 에 모듈 등록이 PM 통합 시점에 반드시 선행되어야 CI 가 인식 (아래 §3 참고).

### 1.3 API Gateway 라우트

- **수정 파일**: `services/api-gateway/src/main/resources/application.yml`
- **추가 위치**: `slip-service` 라우트와 `inventory-service` 라우트 사이 (4번째 라우트 블록).
- **추가 내용**:
  ```yaml
  - id: product-service
    uri: lb://product-service
    predicates:
      - Path=/api/products/**
    filters:
      - StripPrefix=1
      - JwtAuthentication
  ```
- 형식은 기존 user/slip/inventory/accounting-service 와 동일 (단축 표기 `JwtAuthentication`). `logging-service` 처럼 role 제한이 필요 없으므로 `args.allowedRoles` 미사용.
- 빌드 검증: `:services:api-gateway:compileJava :services:api-gateway:processResources` PASS (8s, BUILD SUCCESSFUL).

### 1.4 Eureka 서비스 디스커버리

- 자동 등록. product-service 의 `pom`/`build.gradle` 에 `spring-cloud-starter-netflix-eureka-client` + `spring.application.name=product-service` 만 있으면 OK (BE 책임).
- Gateway 가 `lb://product-service` 로 참조하므로 application name 정합성이 핵심.

### 1.5 Prometheus / Grafana

- Spring Boot Actuator `/actuator/prometheus` 엔드포인트가 자동 노출되며, auth/user-service 와 동일 scrape config 패턴으로 인식.
- 별도 Grafana 대시보드 추가는 후속 슬라이스에서 일괄.

---

## 2. InternalTokenGuard 적용 여부

Product Service 는 **외부 서비스 호출 없음** (BE Plan §4 기준 — 이번 슬라이스에서 `AuthClient` 같은 inter-service 클라이언트 없음). 또한 본 슬라이스에서는 다른 서비스가 product 를 호출하는 시나리오가 정의되지 않음 (Inventory 의 `lookup` 호출은 다음 슬라이스).

| 서비스           | 외부 서비스 호출                       | InternalTokenGuard |
|------------------|----------------------------------------|--------------------|
| auth-service     | 없음 (자체 토큰 발급자)                | 적용 (피호출 측)   |
| user-service     | 있음 (auth-service `AuthClient`)       | 적용               |
| product-service  | **없음**                               | **불필요 (현재)**  |

> **다음 슬라이스 메모**: Inventory 가 `POST /api/products/lookup` 으로 호출하기 시작하면 product-service 도 InternalTokenGuard 적용 필수. 그 시점에 `INTERNAL_AUTH_TOKEN` env var 표준에 product-service 도 합류.

---

## 3. PM 통합 시점에 필요한 작업 (worktree 외부)

당신(DevOps) 의 worktree 에서는 건드리지 않았으나, PM 통합 시점에 반드시 처리되어야 할 항목:

- **`settings.gradle`**: `include 'services:user-service'` 다음에 다음 추가
  ```gradle
  include 'services:product-service'
  project(':services:product-service').projectDir = file('services/product-service')
  ```
- **루트 `build.gradle`** 의 `leafProjects` 리스트에 `':services:product-service'` 추가.

이 두 줄이 누락되면 CI 의 `./gradlew assemble` 이 product-service 를 인식하지 못함.

---

## 4. Inventory 슬라이스 대비 사전 권고

다음 슬라이스가 Inventory 임을 가정한 사전 권고:

1. **DB-level FK 사용 금지**
   `inventory.product_id UUID` 는 logical reference 로만 유지. 서비스-당-DB 분리 원칙(MSA database-per-service)에 따라 cross-DB FK 는 도입하지 않음. 무결성 검증은 application-side `POST /api/products/lookup` 배치 호출로 처리.

2. **이벤트 발행은 본 슬라이스에서 도입하지 않음**
   Product 의 `discontinue` / `markDeleted` 가 Inventory 캐시/표시에 영향을 주는 것은 사실이나, 본 슬라이스에서는 RabbitMQ 발행 미도입. Inventory 슬라이스에서 도입 시 `services/logging-service` 의 `RabbitConfig` + `AuditLogConsumer` 패턴을 모범 사례로 활용.

3. **Stale 표시명 위험**
   Product 의 `model_name` 변경 시 Inventory 의 캐시된 SKU 표시명도 stale. Inventory 가 `lookup` 을 hot path 마다 호출하면 성능 부담. 권고:
   - Inventory 가 read-replica 식 캐시 유지
   - Product 측 명칭 변경 이벤트 발행 (Phase 3 단계 검토)

---

## 5. 운영 배포 체크리스트 (Phase 7 사전 메모)

| 항목                       | Product Service                                                  |
|----------------------------|------------------------------------------------------------------|
| `INTERNAL_AUTH_TOKEN`      | **현재 슬라이스에서는 미사용**. 다음 슬라이스(Inventory 호출 수신)에서 합류. |
| `JWT_SECRET`               | Gateway 가 검증 → product-service 자체에서는 secret 보유 불필요. |
| DB 자격증명                | 표준 env var (`DB_HOST`, `DB_PORT`, `DB_NAME=product_db`, `DB_USER`, `DB_PASSWORD`). |
| Eureka URL                 | 표준 env var `EUREKA_URL` (기본값 `http://localhost:8761/eureka/`). |
| Prometheus scrape config   | 자동. `/actuator/prometheus` 엔드포인트 자동 노출.               |

---

## 6. 확인된 위험 / 후속 슬라이스 권고

1. **카테고리 자기참조 트리 cycle 위험**
   `product-service` 가 `categoryId` 자기참조 트리를 허용하는 경우, 운영자가 cycle (A→B→A) 을 만들면 트리 조립 알고리즘이 무한 루프. 본 슬라이스에서 application-side cycle 검증을 BE 가 처리했는지 PM 통합 시 확인 필요.

2. **외화 거래 정책 미정**
   가격 통화는 KRW 가정이지만 외화 거래 비즈니스 정책 미확정. 현재는 `currency CHAR(3)` 만, ISO 4217 enum 검증 없음. 외화 거래 정책 확정 후 BE 단에서 enum 검증 강화 권고.

3. **태그 키 표준화 미정**
   현재 자유 key-value. 향후 태그 검색 인덱싱 / UI 자동완성을 위해 등록된 키 목록을 별도 관리해야 할 가능성 있음 (Phase 3 추가 검토).

---

## 7. 검증 결과

- **Gateway 빌드**: PASS
  ```
  > Task :services:api-gateway:processResources
  > Task :shared:common:compileJava FROM-CACHE
  > Task :services:api-gateway:compileJava FROM-CACHE
  BUILD SUCCESSFUL in 8s
  ```
- **YAML 정합성**: `processResources` 가 PASS 하므로 application.yml syntax 정상.

---

## 8. 통합 시 충돌 가능성

- `application.yml` 충돌은 다른 worktree (BE/FE/QA) 가 해당 파일을 건드리지 않으므로 발생하지 않을 것으로 예상.
- 잠재적 충돌점은 PM 단계에서 `settings.gradle` / 루트 `build.gradle` 통합 시 모듈 추가 충돌 — 다른 슬라이스가 동시에 추가 모듈을 등록할 경우에 한해 주의.
