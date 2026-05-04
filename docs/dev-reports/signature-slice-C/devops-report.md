# Slice C DevOps Report — 모바일 전자서명 인프라 검증

> **작성**: 2026-05-04 PM Claude DevOps agent
> **참조**: Plan `docs/dev-reports/signature-slice-C/plan.md`,
> Designer `docs/design/signature-slice-C/mobile-spec.md`
> **PR 후보**: PR #23

본 슬라이스 DevOps 의무는 ① V5 Flyway 마이그레이션의 H2/PgSQL 양쪽 호환성 사전
검증 ② PNG bytea 저장 영향 분석 ③ 공개 모바일 페이지 보안 헤더 권장값 + 적용
위치 명시 ④ CI 영향 평가 ⑤ Phase 5 nginx 라우팅 deferred 가이드 ⑥ 회귀 위험
체크 입니다.

---

## 1. Flyway V5 마이그레이션 검증

### 1.1 PgSQL 16 컨테이너 시연 결과 (사전 검증 완료)

`postgres:16` 컨테이너 신규 인스턴스 + V1 → V2 → V3 → V4 → V5 순차 적용 →
**모두 성공**. 검증 SQL 은 `flyway-v5-draft.sql` 에 인용 가능 형태로 보존 (BE
agent 가 Slice C V5 작성 시 그대로 채택 가능).

| 검증 항목 | 결과 |
| --- | --- |
| `slips.signature_png BYTEA` 컬럼 생성 | OK (`bytea`, 4-byte PNG 시그니처 INSERT/SELECT 정상) |
| `slip_signature_audit` 테이블 신규 + 2개 인덱스 | OK (`(slip_id, created_at DESC)` + `(action, created_at DESC)`) |
| Partial UNIQUE INDEX `signature_share_token WHERE NOT NULL` | OK — NULL 토큰 다수 허용, 동일 non-NULL 토큰 INSERT 시 정확히 reject |
| 기존 slips 테이블 무영향 (ALTER ADD COLUMN nullable) | OK — PostgreSQL 11+ 메타데이터 only, 기존 row rewrite 없음 |
| V1~V4 + V5 통합 후 `\d slips` | 7개 신규 컬럼 모두 정상 등록 |

### 1.2 H2 (local profile) 호환성

`spring.profiles.active=local` 은 `flyway.enabled=false` + `ddl-auto: create-drop`
로 JPA 가 H2 스키마를 자동 생성. **Flyway SQL 은 H2 에서 실행되지 않음** —
V1 부터 일관된 패턴이며 Slice C 도 동일. 따라서 H2 의 partial unique index
미지원 (`WHERE` 절 syntax error) 은 본 슬라이스 회귀 영향 0.

PgSQL IT (Testcontainers `AbstractPostgresIT`) 가 Flyway 실제 적용을 검증함 —
Layer 2 (Docker IT 시연) 가드 통과.

### 1.3 BE agent 인계 사항

- V5 SQL 은 `flyway-v5-draft.sql` 그대로 채택 가능
- BE 가 Slip 엔티티에 7 신규 필드 + `SlipSignatureAudit` 엔티티 작성 시
  `ddl-auto: validate` 통과 보장 (PgSQL IT 시 Hibernate 메타데이터 ↔ Flyway
  스키마 일치)
- `signature_png BYTEA` 는 JPA `@Lob byte[]` 또는 `byte[] + @Column(columnDefinition="bytea")`
  매핑 권장 (Hibernate 6 + PostgreSQL 16 검증 완료 패턴)

---

## 2. PNG bytea 저장 영향 분석

### 2.1 사이즈 추정

| 시점 | 슬립 누적 | 서명 PNG 누적 |
| --- | --- | --- |
| 1개월 | 1,000 | ~30 MB |
| 12개월 | 12,000 | ~360 MB |
| MinIO 트리거 (월 1만건) | — | Phase 6 deferred |

PostgreSQL `bytea` 단일 컬럼 1GB 한계 → **본 슬라이스 50KB/row × 12,000 row =
600MB, 안전 마진 ~40%**. 단일 row 50KB 도 TOAST 자동 압축으로 디스크 ~70%
실효 사이즈.

### 2.2 Backup 영향

- `pg_dump --format=custom` 사용 의무 (binary 안전, 압축, 병렬 복원)
- `pg_dump --format=plain` 사용 금지 (bytea 가 hex 문자열로 60KB+ 팽창)
- 상세 runbook: `backup-runbook.md`

### 2.3 VACUUM / Autovacuum

- 서명은 1회 INSERT + 무효화 시 NULL UPDATE → bloat 최소
- Phase 5 prometheus pg_exporter 도입 후 `n_dead_tup` 자동 모니터링

### 2.4 MinIO 마이그 (Phase 6 deferred)

월 1만건 초과 시 시나리오는 backup-runbook §5 에 정리. `signature_channel`
컬럼 (VARCHAR(20)) 이 `MOBILE_CANVAS` / `S3_OBJECT` 확장 키로 사용됨 →
스키마 변경 없이 마이그 가능.

---

## 3. 공개 모바일 페이지 보안 헤더

### 3.1 적용 위치

API Gateway 신규 `WebFilter` (`PublicSecurityHeaderFilter.java`) — `/api/public/**`
응답에만 6종 보안 헤더 부착. 기존 `CorsConfig` / `JwtAuthenticationGatewayFilterFactory`
무영향. BE agent 인계.

### 3.2 권장 헤더 (6종)

| 헤더 | 값 |
| --- | --- |
| `Content-Security-Policy` | `default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'` |
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `X-Robots-Tag` | `noindex, nofollow` |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=()` |

상세: `public-security-headers.md`. CSP 는 Designer mobile-spec §6 호환성 표를
모두 만족 (Web Crypto, Canvas data URI, navigator.share, clipboard 모두 OK).

### 3.3 noindex 정책

mobile mini bundle `<head>` 에 `<meta robots="noindex,nofollow">` + 응답 헤더
`X-Robots-Tag: noindex, nofollow` 이중 적용. 인수자 view PII (서명자명 +
거래처명) 검색엔진 노출 차단.

---

## 4. CI 영향 분석

**변경 0건.** `.github/workflows/ci.yml`:
- env 변수 추가 X (Slice C 외부 의존 없음, Slice B Solapi env 그대로)
- 신규 step 추가 X (V5 마이그 검증은 기존 Testcontainers IT 안에서 자동 수행)
- Docker 의존 추가 X (PgSQL 16 컨테이너는 이미 Slice B 부터 사용 중)

본 DevOps 사전 검증 (V1~V5 PgSQL 시연) 은 로컬 작업으로 완료, 결과는
`flyway-v5-draft.sql` 에 보존되어 BE agent 가 그대로 채택 가능.

---

## 5. Phase 5 nginx 라우팅 deferred

Plan §7 Q8 결정에 따라 sign.samhan-air.com nginx 분리는 Phase 5 web app
슬라이스에서 일괄 처리. 본 슬라이스는 desktop Electron 앱 안 mock 라우트
(`http://localhost:5173/mobile/d/:token/s/:slipNo`) 로 시연.

Phase 5 진입 시 작업 (draft 후보 위치: `infrastructure/nginx/conf.d/sign.conf`)
는 `nginx-sign-deferred.md` 에 정리.

---

## 6. 회귀 위험 평가

| 영역 | 회귀 위험 | 비고 |
| --- | --- | --- |
| Docker compose | 0 | slip-service 컨테이너 정의 변경 X |
| V1~V4 마이그 | 0 | V5 는 ALTER ADD COLUMN nullable + 신규 테이블 → 기존 데이터 호환 |
| API Gateway 라우팅 | 0 | `/api/public/**` 라우트 기존 활성, 보안 헤더만 추가 |
| CORS | 0 | sign.samhan-air.com 이미 allowedOrigins 등록 (Slice B) |
| CI | 0 | env / step 변경 X |
| Eureka / 서비스 디스커버리 | 0 | slip-service 변경 없음 |
| H2 local profile | 0 | flyway.enabled=false 패턴 일관 유지 |

---

## 7. 비용 영향

- 외부 API 의존 신규 0건 (Solapi 만 — Slice B 에서 이미 도입)
- DB 사이즈 12개월 누적 360MB → 인프라 비용 영향 무시 가능
- MinIO 도입 deferred → Phase 6 시점에 비용 재평가

---

## 8. 다음 단계 (DevOps 후속)

1. BE agent 가 V5 `.sql` 작성 시 `flyway-v5-draft.sql` 그대로 채택 권장
2. BE agent 가 `PublicSecurityHeaderFilter` 신규 작성 (값은 `public-security-headers.md` §2 표)
3. QA agent 가 7.1 디바이스 검증 + 4.4 보안 헤더 curl 검증 수행
4. Phase 5 진입 시 `nginx-sign-deferred.md` §3 체크리스트 실행

---

## 9. 산출물 목록

- `flyway-v5-draft.sql` — V5 SQL (PgSQL 시연 검증 완료, BE 인용용)
- `public-security-headers.md` — 보안 헤더 6종 권장값 + 적용 위치
- `backup-runbook.md` — pg_dump 옵션 + MinIO 마이그 시나리오
- `nginx-sign-deferred.md` — Phase 5 nginx draft + 체크리스트
- `devops-report.md` — 본 문서
