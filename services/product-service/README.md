# product-service

SamhanLogis Product 마스터 + Category 트리 서비스 (plan §3.5 first slice).

- 포트: **8084**
- DB: PostgreSQL `product_db` (service-per-DB), Flyway 자동 마이그레이션
- 인증: gateway 가 주입하는 `X-User-Id` / `X-User-Role` 헤더 신뢰 (HeaderAuthenticationFilter)
- 외부 서비스 호출 없음 (internal-token guard 미사용)

## 5대 핵심 결정 (개발책임자 결재 완료)

1. **Category 모델링**: 별도 엔티티 + 단일 부모 자기참조 트리. 깊이 무제한 (코드 강제 X)
2. **태그 저장**: PostgreSQL `jsonb` + Hibernate 6 native `@JdbcTypeCode(SqlTypes.JSON)` + GIN 인덱스
3. **가격 자료형**: `BigDecimal` + `NUMERIC(15,2)` + `currency CHAR(3) NOT NULL DEFAULT 'KRW'`
4. **단종 처리**: 별도 `ProductStatus` enum {`ACTIVE`, `DISCONTINUED`} (soft-delete 와 직교)
5. **unique 제약**: `(model_name)` 단독 unique partial: `is_deleted = false`

## REST endpoints (16)

| Method | Path | 권한 |
|---|---|---|
| GET | `/products` | 인증 |
| GET | `/products/{id}` | 인증 |
| POST | `/products/lookup` | 인증 |
| POST | `/products` | M/M/D |
| PATCH | `/products/{id}` | M/M/D |
| PATCH | `/products/{id}/price` | M/M/D/A |
| PUT | `/products/{id}/tags` | M/M/D |
| POST | `/products/{id}/discontinue` | M/M/D |
| POST | `/products/{id}/reactivate` | M/M/D |
| DELETE | `/products/{id}` | M/M/D (soft-delete) |
| GET | `/products/categories` | 인증 |
| POST | `/products/categories` | M/M/D |
| PATCH | `/products/categories/{id}` | M/M/D |
| DELETE | `/products/categories/{id}` | M/M/D (자식 존재 시 409) |

권한 약어: M=MASTER, M=MANAGER, D=DEVELOPER, A=ACCOUNTANT (price patch 한정)
