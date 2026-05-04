# Slice B (notification-slice-B) — DevOps 검토 리포트

> 작성: 2026-05-04 DevOps Agent
> 입력: `docs/dev-reports/notification-slice-B/plan.md` (§5 DB, §6 Solapi)
> PR 후보: PR #22

본 리포트는 Slice B (배송기사 배치 링크 + 자동 SMS) 의 인프라 / 환경변수 / Flyway / CI / Docker / RabbitMQ 영향 검토 산출물입니다.

---

## 1. 신규 환경변수 (4개)

### 1.1 변수 명세 — Plan §6 인용

| 변수 | 용도 | local (H2) | dev/staging/prod | 시크릿 등급 |
| --- | --- | --- | --- | --- |
| `SOLAPI_API_KEY` | Solapi 인증 키 | 미사용 (Mock) | 필수 | High |
| `SOLAPI_API_SECRET` | Solapi HMAC 시크릿 | 미사용 (Mock) | 필수 | **Critical** |
| `SOLAPI_SENDER_PHONE` | 사전 발신번호 등록된 010 번호 | 미사용 (Mock) | 필수 | Low |
| `SOLAPI_BASE_URL` | Solapi API 엔드포인트 | 미사용 (Mock) | `https://api.solapi.com` 기본값 | Low |

`SOLAPI_API_SECRET` 은 HMAC 서명에 직접 사용되므로 노출 시 SMS 무단 발송 위험 → 시크릿 관리 시스템 (staging/prod) 우선순위 1.

### 1.2 산출 파일

- `infrastructure/.env.example` — 기존 인프라 env 템플릿에 Solapi 섹션 4개 변수 추가 (DEV-ONLY 기본값).
- `infrastructure/env-templates/slip-service.env` (**신규**) — slip-service 전용 per-service env 템플릿. DataSource / Eureka / InternalAuth / Solapi 통합. 사용법: `cp infrastructure/env-templates/slip-service.env services/slip-service/.env` (`.gitignore` 의 `.env` 패턴으로 자동 무시).
- `README.md` — "환경변수 (per-service)" 섹션 신설, slip-service Slice B 신규 env 표 명시.

### 1.3 .gitignore 검증

`.gitignore` 64–65 라인에 `.env` / `.env.local` / `.env.*.local` 이미 ignored. PR #21 회고 직후 정리됨. 신규 작업 불필요.

`git check-ignore` 결과 — `services/slip-service/.env` 패턴 매치 확인.

---

## 2. Flyway V3 / V4 호환성 검증

### 2.1 검증 방법

PostgreSQL 16-alpine 컨테이너 (port 55432) 에 V1 + V2 + V3 (Plan §5.1) + V4 (Plan §5.2) 를 순차 적용 — `psql -v ON_ERROR_STOP=1`.

```
docker run -d --name slipdb-flyway-test postgres:16-alpine ...
docker exec slipdb-flyway-test psql -f /tmp/V1.sql   # OK (CREATE TABLE x3, INDEX x6)
docker exec slipdb-flyway-test psql -f /tmp/V2.sql   # OK (ALTER x5, INDEX x2)
docker exec slipdb-flyway-test psql -f /tmp/V34.sql  # OK (V3 ALTER x3 + INDEX x2 / V4 CREATE TABLE + INDEX + FK)
```

검증용 V3/V4 드래프트는 `docs/dev-reports/notification-slice-B/flyway-v3-v4-draft.sql` 보관 (BE 가 그대로 마이그레이션 파일로 옮길 수 있음).

### 2.2 검증 결과 — PgSQL 프로파일

- **V3 — `slips` 컬럼 추가 + 부분 인덱스 2개**: `driver_name` / `driver_phone` / `delivery_batch_id` 컬럼 정상 추가, `WHERE delivery_batch_id IS NOT NULL` 및 `WHERE driver_phone IS NOT NULL` partial index 2개 생성 확인. `\d slips` 출력으로 검증.
- **V4 — `delivery_batches` 신규 + FK**: 테이블 생성, `uk_batch_token` UNIQUE constraint, `uk_delivery_batches_driver_date` partial UNIQUE INDEX (`WHERE is_deleted = FALSE`), `slips → delivery_batches` FK 정상. `\d delivery_batches` 출력으로 검증.
- **마이그레이션 순서**: V3 가 `delivery_batch_id UUID` 만 추가 (FK 미설정), V4 가 `delivery_batches` 테이블 생성 후 FK 추가 — 순서 의존성 깨짐 0.
- **회귀 위험 0**: 신규 컬럼 모두 nullable → 기존 슬립 데이터 호환. 신규 테이블 → 기존 도메인 무영향.

### 2.3 H2 local 프로파일

- `application.yml` local profile: `spring.flyway.enabled=false` + `spring.jpa.hibernate.ddl-auto=create-drop` → V3/V4 SQL 미실행. Hibernate 가 `Slip` 엔티티의 신규 필드 (driverName/driverPhone/deliveryBatchId) + 신규 `DeliveryBatch` 엔티티에서 H2 in-memory 스키마 자동 생성.
- **DevOps 관점 액션 0**: SQL 파일의 H2 호환성 평가 불필요 (실행 안됨). 단, BE 가 엔티티 ↔ SQL 컬럼명/타입 매칭을 보장해야 PgSQL 프로파일 (ddl-auto: validate) 부팅 시 검증 통과 — Layer 1+2 PM 통합 가드에서 컴파일 + Hibernate validate 확인 의무.

### 2.4 적용 절차 (BE/QA 인용용)

1. PM 통합 단계: `./gradlew :services:slip-service:flywayInfo` 로 V3/V4 pending 확인.
2. local docker-compose 부팅 후 slip-service `bootRun` 으로 V1+V2+V3+V4 자동 실행 검증.
3. PR 머지 → staging 환경 자동 마이그레이션 (Spring Boot startup 시점).

---

## 3. CI 영향 분석

### 3.1 결론 — CI workflow 변경 0

`.github/workflows/ci.yml` 검토:

- `gradle assemble --no-daemon` (45 라인) — 컴파일만, env 무관.
- `gradle test --no-daemon` (47 라인) — Testcontainers IT 포함. **테스트 프로파일은 MockSmsGateway 자동 활성** (Plan §6 — H2 local 또는 test profile → Mock) → `SOLAPI_*` env 미주입 시에도 정상.
- `setup-java` (24 라인) / `setup-gradle` (30 라인) / `chmod +x gradlew` (35 라인) — 기존 그대로.

### 3.2 SOLAPI env 가 CI 에 필요한가?

**No**. 근거:

- BE 는 `@Profile("!local & !test")` 로 `SolapiSmsGateway` bean 활성, 그 외는 `MockSmsGateway` bean (logging only, in-memory result).
- IT 는 `@MockBean SmsGateway` 로 격리 가능 (Plan §9 명시) — 외부 client `@MockBean` 의무 (`feedback_it_mockbean_external_clients.md`) 준수.
- 따라서 GitHub Actions secrets 에 `SOLAPI_*` 등록 **불필요**, `ci.yml` env 블록 추가 **불필요**.

### 3.3 향후 (참고)

Phase 5 Notification Service 분리 + 통합 SMS 발송 모니터링 시점에 staging 환경 별도 secrets 등록 예정 (현 슬라이스 범위 외).

---

## 4. Docker compose 영향

### 4.1 결론 — `docker-compose.yml` 변경 0

- `infrastructure/docker-compose.yml` 에 slip-service 컨테이너 정의 자체가 없음 (현 단계는 인프라 데이터/모니터링 스택만 컨테이너화, application 서비스는 `gradlew bootRun` 직접 실행 패턴).
- notification-service 신설 X (Plan §6 명시 — Slice B 는 slip-service 내부에 SMS 발송 로직 통합, Notification Service 분리는 Phase 5).

### 4.2 향후 (참고)

slip-service 컨테이너화 시점 (Phase 5 또는 Phase 6 외부 web 노출 단계) 에 `services/slip-service` 블록 추가하며 `env_file: ../infrastructure/env-templates/slip-service.env` 패턴 적용 예정. 현 슬라이스에서는 템플릿 파일만 미리 정비.

---

## 5. RabbitMQ 영향

### 5.1 결론 — RabbitMQ 토폴로지 변경 0

- Plan §1 / §6: SMS 발송 트리거는 **관리자 수동 클릭** (결정 N1) — 이벤트 기반 비동기 처리 미사용.
- POST `/delivery-batches/{id}/send-sms` → SolapiSmsGateway 동기 호출 → smsSentAt 기록. 큐/exchange/binding 0 신설.
- 기존 logging-service RabbitMQ consumer 패턴 영향 0.

---

## 6. 비용 모니터링 (deferred)

### 6.1 현 슬라이스 처리

- Solapi 비용 추정: 기사 5~10명/일 × 배치 1~3건 = 월 150~450 SMS = **월 1.5~4.5천원** (Plan §2 N3).
- SMS 발송 결과는 `application log` 로만 기록 (slip-service stdout) — `feedback_function_documentation.md` Layer 3 dev-report 패턴 인용.
- `DeliveryBatch.smsSentAt` / `smsLastError` 컬럼으로 DB 측에도 발송 이력 추적 가능 (재시도 판별).

### 6.2 deferred 항목

- Grafana 대시보드 — Solapi 호출 건수/비용 가시화 → **Phase 5 Notification Service 분리 시점**.
- Prometheus 커스텀 메트릭 (`solapi_sms_sent_total`, `solapi_sms_failed_total`) → 동일 시점.
- 일일/월별 비용 알림 (월 임계치 초과 시) → 동일 시점.

본 슬라이스에서 Grafana provisioning 변경 없음 → infra `provisioning/dashboards/` 무영향.

---

## 7. 회귀 위험 평가 — 종합

| 영역 | 위험 | 근거 |
| --- | --- | --- |
| Docker compose | **0** | slip-service 컨테이너 정의 자체 없음, infra 스택 무변경 |
| CI workflow | **0** | MockSmsGateway 활성으로 SOLAPI env 불필요 |
| RabbitMQ | **0** | 동기 호출만, 큐/exchange 신설 0 |
| Flyway 회귀 (기존 V1/V2) | **0** | V3/V4 는 ALTER ADD COLUMN nullable + 신규 테이블, 기존 데이터 호환 |
| Flyway 신규 (V3/V4) | **0** | PgSQL 16 컨테이너 사전 적용 검증 완료, partial index/FK 정상 |
| H2 local 부팅 | **낮음** | BE 가 엔티티 ↔ SQL 매칭 보장 시 0 — PM 통합 Layer 2 가드 책임 |
| 시크릿 노출 | **0** | `.env` 이미 `.gitignore`, dev 기본값 모두 `dev-*-change-me` 명시 |
| .env.example UTF 인코딩 | **0** | Write tool (UTF-8 no BOM) 사용, `feedback_powershell_utf8_writes.md` 준수 |

---

## 8. 회고 가드 준수 점검

| 가드 | 준수 여부 | 비고 |
| --- | --- | --- |
| `feedback_pm_integration_build_check.md` Layer 2 (Docker IT) | **충족** | PgSQL 16 컨테이너 즉석 부팅 + V1+V2+V3+V4 순차 적용 시연 |
| `feedback_korean_commits.md` | **충족** | 모든 신규 문서/커밋 한국어 |
| `feedback_powershell_utf8_writes.md` | **충족** | `.env.example` / `.env` 템플릿 모두 Write tool (UTF-8 no BOM) |
| `feedback_uuid_no_user_visibility.md` | **N/A** | DevOps 산출물에 UI 노출 없음 |
| `feedback_role_naming_full.md` | **충족** | MANAGER / MASTER 풀네임 사용 |
| `feedback_it_mockbean_external_clients.md` | **참조** | BE 가 `@MockBean SmsGateway` 적용 의무 안내 (§3.2 / §7) |

---

## 9. 다음 단계 (BE/QA 인용용)

1. BE — `services/slip-service/src/main/resources/db/migration/V3__add_slip_driver_contact.sql` 및 `V4__create_delivery_batches.sql` 작성 (본 리포트 §2.1 검증 SQL 그대로 사용 가능).
2. BE — `application.yml` 에 `app.sms.*` 섹션 추가 (Plan §6 인용), `SmsGateway` 인터페이스 + `SolapiSmsGateway` / `MockSmsGateway` 구현, profile 기반 bean 활성.
3. QA — IT 시나리오에 `@MockBean SmsGateway` 격리 + Solapi 호출 검증 (성공/실패/재발송 3 케이스).
4. PM 통합 — Layer 1+2+3+4 사전 검증 후 PR #22 발행.
