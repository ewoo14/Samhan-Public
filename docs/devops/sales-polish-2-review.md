# Slice A — Sales Polish 2 — DevOps 검토 리포트

> 본 리포트는 Slice A (Sales Polish 2) 의 DevOps 관점 검토 산출물입니다.
> 본 Slice 는 인프라 자체 변경이 없으며, BE Flyway V2 한 건과 FE 토큰/컴포넌트 추가만 있습니다.
> 본격 인프라 추가 (Notification Service / 모바일 서명 / 별도 도메인 + KISA 보안) 는 Slice B/C 로 분리됩니다.

---

## 1. 인프라 변경 (작음)

| 영역 | 변경 |
| ---- | ---- |
| BE   | slip-service 의 SlipStatus enum + Slip 4 필드 + SlipLine 1 필드 + Flyway V2 마이그레이션 + 1 endpoint 추가 (POST /slips/{id}/inspect) |
| FE   | 디자인 토큰 추가 + 신규 ProgressBar 컴포넌트 + DispatchView 큰 디자인 정정 (1×5 결재란 / 14pt 본문 / 80×35mm 서명) |
| 인프라 | 자체 변경 0건 — 라우트 / docker-compose / CI / Gateway 모두 기존 그대로 |

본 Slice 는 application-level 변경에 한정. 인프라 신규 컴포넌트 0건.

---

## 2. Flyway V2 마이그레이션 검토

### 2.1 대상 파일
- `services/slip-service/src/main/resources/db/migration/V2__add_slip_signature_and_inspecting.sql`
- ALTER TABLE `slips` 4 필드 (dispatcher_user_id / dispatcher_signed_at / inspector_user_id / inspector_signed_at)
- ALTER TABLE `slip_lines` 1 필드 (specification varchar(50))
- 신규 인덱스 2개 (dispatcher_user_id / inspector_user_id) — user-service lookup 보조

### 2.2 회귀 위험 평가
- 모든 신규 컬럼 NULL 허용 → 기존 데이터 영향 0
- SlipStatus 의 INSPECTING 신규 enum value 추가 — 기존 enum 데이터 (DRAFT/PENDING/.../COMPLETED) 영향 0
- prod 적용 시 lock 시간 추정: < 1초 (현재 slip 테이블 row 수 작음 — 본격 운영 전이라 OK)

### 2.3 모듈 분리 확인
- inventory-service 에도 V2 마이그레이션이 있으나 별개 DB 모듈 (slip-service DB 와 분리)
- Flyway 는 service-per-DB 원칙으로 동작 — 충돌 0

### 2.4 적용 절차
1. PM 통합 단계에서 `./gradlew :services:slip-service:flywayInfo` 로 변경 확인
2. local docker-compose 에서 `docker compose up slip-db slip-service` 로 마이그레이션 실행 검증
3. PR 머지 후 staging 환경에서 V2 자동 적용 (Spring Boot startup 시)

---

## 3. SlipStatus 변경 영향

### 3.1 enum 신규 단계
- `INSPECTING("검수중")` 신규 추가 — PROCESSING → **INSPECTING** → COMPLETED
- 기존 9단계 → 10단계

### 3.2 client 영향
- FE Electron client (in-tree) 가 SlipStatus 응답을 받음
- BE 머지 → FE 머지 순서 문제 → PM 통합 단일 PR 이라 0 (동시 머지)
- 외부 API consumer 없음 (Slice A 시점) → breaking change 영향 0

### 3.3 기존 데이터 처리
- 기존 DB 의 COMPLETED 상태 Slip → 그대로 유지 (PROCESSING → INSPECTING → COMPLETED 흐름은 신규 전표에만 적용)
- ACCEPTED 상태 Slip → dispatcher_user_id NULL 그대로 (FE 가 빈 값 허용 — 결재란 출고인 셀 빈 값 표시)
- 백필 작업 불필요

---

## 4. 보안 후속 (Slice B/C 권고)

본 Slice A 에는 미포함. 출고인/검수인 자동 기입만 (BE 가 ACCEPTED/INSPECTING 트랜지션 시 user-service 에서 lookup 한 이름·시각 자동 set).

### 4.1 Slice B (모바일 서명) 보안 항목
- Notification Service (카톡 비즈메시지 + SMS) 신규 도입
- Redis 인프라 활용 (이미 docker-compose 에 있음 — 재사용)
- 카톡 비즈메시지 API 키 + SMS 게이트웨이 secret 관리 (`gradle.properties` 환경 변수 + Vault 도입 권고)
- 인프라 큰 추가: Notification Service + Redis pub/sub + 외부 API 통합

### 4.2 Slice C (모바일 서명 페이지 + e-Sign) 보안 항목
- 모바일 서명 페이지 (sign.samhan-air.com 신규 subdomain — domain strategy 준수)
- Canvas 서명 PNG + SHA-256 해시 + 타임스탬프
- 표준 전자서명 (KISA 가이드라인 준수)
- 보안 인증서 (HTTPS — Let's Encrypt 또는 사설 CA)
- 서명 deeplink token 만료 시간 설계 (24시간 권고)

본 Slice A 에는 e-Sign 관련 보안 항목 0건 — 단순 자동 채움만.

---

## 5. 후속 슬라이스 권고 (Slice B/C 우선)

1. **Slice B (즉시 후속, 1.5주)**: Notification Service 도입
   - Redis 인프라 활용 (이미 docker-compose 에 있음)
   - 카톡 비즈메시지 API 통합
   - SMS 게이트웨이
   - `services/notification-service` 신규 모듈 + Spring Boot 3 + WebFlux (비동기)
2. **Slice C (Slice B 후, 2주)**: 모바일 서명 페이지 + Canvas 서명
   - 표준 전자서명 + sign.samhan-air.com 도메인 설정
   - HTTPS 인증서 + KISA 가이드라인
   - PDF 첨부 (puppeteer 또는 wkhtmltopdf)
3. **Slice D (디자인 점진)**: 디자인 토큰 16 컴포넌트 점진 적용
4. **Slice E (Storybook 배포)**: GitHub Pages 배포 (디자인 시스템 visual reference)
5. **Slice F (Partner Service Q9)**: Phase 4 — Partner AR 관리 후속
6. **Slice G (Admin)**: UUID 화면 (관리자 권한)

---

## 6. 점검 결과

| 항목 | 상태 | 비고 |
| ---- | ---- | ---- |
| Gateway 변경 | 0건 | 신규 endpoint POST /slips/{id}/inspect 는 기존 /slips 라우팅에 자동 흡수 |
| docker-compose 변경 | 0건 | 신규 컨테이너 0개 |
| CI 변경 | 0건 | gradle 빌드 + Flyway info check 기존 그대로 |
| 환경 변수 추가 | 0건 | Slice A 시점 |
| secret 추가 | 0건 | Slice A 시점 (Slice B/C 에서 카톡 API 키 + KISA 인증서 secret 추가) |
| Flyway V2 회귀 위험 | 0 | NULL 허용 + 기존 데이터 영향 0 |
| BE/FE 호환 위험 | 0 | PM 통합 단일 PR 이라 머지 순서 문제 0 |

---

## 7. PM 통합 시 검증 권고

1. `./gradlew :services:slip-service:assemble` → BE 컴파일 확인 (한글 path JDK 트랩 — `gradle test` 대신 `assemble`)
2. `./gradlew :services:slip-service:flywayInfo` → V2 마이그레이션 인식 확인
3. local docker-compose 에서 `docker compose up slip-db slip-service` → V2 적용 + endpoint 동작 검증
4. FE Electron client 빌드 + DispatchView 인쇄 미리보기 (Chrome Ctrl+P) → A4 portrait 273mm budget 검산
5. QA IT (integration test) — BE 의도적 변경 후 IT drift 방어 (PM 통합 풀빌드 가드 준수)

---

## 8. 결론

- ✅ 본 Slice A 인프라 변경 작음 (자체 변경 0건, BE Flyway V2 한 건, FE 토큰/컴포넌트 추가)
- ✅ Flyway V2 회귀 위험 0 (NULL 허용 + 기존 데이터 영향 0)
- ✅ Gateway / docker-compose / CI 변경 없음
- 🔜 Slice B/C 가 본격 인프라 추가 (Notification Service + Redis pub/sub + 모바일 서명 페이지 + sign.samhan-air.com 도메인 + HTTPS 인증서 + KISA 보안)
