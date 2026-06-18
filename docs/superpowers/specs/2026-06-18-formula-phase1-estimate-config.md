# Phase 1 — 종합견적서 전역 가격 파라미터 설정 (estimate_configs)

> 2026-06-18 개발책임자 결정 후 착수. 수식 빌더 기획서 §4 Phase 1(옵션 A 파라미터 설정). [[project_formula_builder_epic]] [[feedback_post_devlead_decisions_to_pr]]

## 0. 개발책임자 결정 (확정)
- **Phase 1(파라미터 설정 메뉴) 착수** (F3/F4 자동매칭과 별개 선행).
- **카드수수료·선금할인 = 견적 총액에 반영** (카드 결제 시 수수료 % 가산=손실 방지 / 선금 시 % 할인. **기본값 0 = 현행 parity, 설정 시만 적용**. 적용 기준=VAT 포함 총액).
- (F3/F4 = B 경량 휴리스틱·isDefault 우선 — 후속 슬라이스.)

## 1. 목표
종합견적서(estimate-app)의 **전역 가격 파라미터**(거래처 무관)를 하드코딩/상수 → **DB 설정 + 메뉴 UI**로 전환. 신규 전역 저장소 `estimate_configs`(싱글톤) 신설. 🚨 **GAS/현행 parity 최우선 — 모든 기본값 = 현 하드코딩값, 설정 변경 전 견적 금액 완전 동일**.

## 2. 정찰 결론 (범위 확정)
- **거래처별 파라미터**(홈/상업멀티 DC·360/4way/1way/스탠드/디럭스/1등급 정액DC·단위반올림·유연호스)는 **이미 `DcConfig`(dc-config-service)에 있고 SalesPartnerDcConfigPage 로 편집 가능** → 본 슬라이스 대상 아님.
- **전역 저장소 없음** → 신규 `estimate_configs` 필요.
- estimate-app 전역 하드코딩(`clients/web/estimate-app/lib/code.js`):
  - `DISCOUNT_RATE_HOME`/`DISCOUNT_RATE_COMM` = 0.45 (변동DC 공통율, line ~137-138) — buildDefaultDcConfig_ 기본값.
  - 구형DC 0.5 (t.config `oldDiscount`, line ~1779, 상수화 미완).
  - VAT 1.1 (line ~2199-2205, 부가세 10% 분리).
  - 카드수수료 = 기존 "수수료 포함" 3% client-side 합산 동작 유지 + 요율 설정화. 선금할인 = 신규(기본0). 조합비 경고 = Phase 1 저장만, 실제 경고 트리거는 후속.
  - 안내문구(footer) = 견적서 하단 문구.

## 3. 데이터 모델 (신규)
**dc-config-service** 에 `EstimateConfig` 싱글톤 엔티티 (BaseEntity 7 audit + Soft Delete, [[project_build_conventions]]):
| 필드 | 타입 | 기본값(=현행 parity) | 의미 |
|---|---|---|---|
| commonHomeDiscountRate | NUMERIC(5,4) | 0.45 | 홈멀티 변동DC 공통율(거래처 미설정 시 fallback) |
| commonCommercialDiscountRate | NUMERIC(5,4) | 0.45 | 상업멀티 변동DC 공통율 |
| oldProductDiscountRate | NUMERIC(5,4) | 0.5 | 구형 제품 DC율 |
| vatRate | NUMERIC(5,4) | 0.1 | 부가세율(법정 10%) |
| cardFeeRate | NUMERIC(5,4) | 0.03 | 카드수수료율(기존 3% parity, client-side 합산) |
| advanceDiscountRate | NUMERIC(5,4) | 0 | 선금할인율(총액 할인) |
| comboWarnRate | NUMERIC(5,4) | 0 | 조합비 경고 임계(0=off). Phase 1은 저장/조회만 구현 |
| footerNotice | TEXT | (현 안내문구) | 견적서 하단 안내 |

- 싱글톤: 활성 1행 unique(soft-delete 제외). 미존재 시 service 가 기본값 1행 시드/반환.

## 4. 구현 (Codex)
### 4.1 BE — dc-config-service
- `EstimateConfig` 엔티티 + `EstimateConfigRepository` + `EstimateConfigService`(get-or-seed-default, update).
- Flyway `V_` 마이그레이션(테이블 + 기본값 1행 시드). 🚨 fresh Postgres probe 검증([[feedback_migration_fresh_postgres_probe]]).
- **Admin endpoint**: `GET /api/v1/estimate-config` + `PUT /api/v1/estimate-config` (page-code 권한, 아래 §4.3).
- **Internal endpoint**: `GET /products|dc/internal/.../estimate-config` (X-Internal-Token) — estimate-app 용. (dc-config-service 의 InternalDcConfigController 패턴 재사용.)
- 게이트웨이 라우트(admin은 JwtAuthentication, internal은 비노출).

### 4.2 FE — desktop 설정 메뉴
- 신규 페이지 `EstimatePricingConfigPage`(예: `/sales/estimate-config`). 단순 폼(8필드 입력 + 저장/리셋). SalesPartnerDcConfigPage 패턴(react-query·canAccess) 따름.
- route + 메뉴 등록(AppLayout/SalesSubNav). API client(sales.ts) getEstimateConfig/updateEstimateConfig.

### 4.3 권한 (개발책임자 page-code 승인 필요 시 보고)
- `sales.estimate-config` view/update (MASTER/MANAGER). seed 추가. — 신규 page-code면 [[feedback_pm_permission_autonomy]] 범위 내 PM 진행 + 보고.

### 4.4 estimate-app 통합 (🚨 parity 핵심)
- db-catalog 류 `estimateConfig()` fetch(DB모드) — internal endpoint read.
- `code.js`:
  - 변동DC 공통율 → `DISCOUNT_RATE_HOME/COMM` 대체(buildDefaultDcConfig_ 기본값을 config 값으로). 비-DB/실패 시 현 상수 fallback.
  - 구형DC → `oldDiscount` config 값(기본 0.5).
  - VAT → 1.1 하드코딩을 `(1 + vatRate)` 로(기본 0.1 → 1.1 동일). **VAT 분리 계산 전 지점 일관 적용**(line 2199 외 세금계산서/명세서 VAT 지점은 본 슬라이스 제외 — estimate-app 한정).
  - 카드수수료/선금할인 → "수수료 포함"·"선결제" 토글 ON 시 cardFeeRate/advanceDiscountRate 적용(총액 기준, 기본 0=무변경). 토글 현 동작 확인 후 요율만 주입.
  - footer → footerNotice 표시.
- 비-DB 모드/실패 = 현 하드코딩 fallback(graceful).

## 5. 검증 (Acceptance) — parity 최우선
1. **Golden parity**: estimate-app 동일 입력에 대해 config 기본값(0.45/0.5/0.1/카드0/선금0) 적용 결과 = 현 하드코딩 결과 **금액 byte 동일**(대표 거래처×품목 매트릭스). 카드수수료/선금 0 → 무변경.
2. **설정 반영**: cardFeeRate 설정 후 "수수료 포함" 시 총액 가산 정확. advanceDiscountRate·변동DC 공통율 변경 반영.
3. **Java IT**: EstimateConfig endpoint(get-or-seed/update/401) + Testcontainers.
4. **변경 모듈 전체 test 완주**([[feedback_changed_module_full_test_before_push]]). dc-config-service + estimate-app.
5. **Docker 실QA**: 설정 UI 저장 → estimate-app 견적 반영(변동DC 공통율 변경·카드수수료 가산) 라이브 캡처([[feedback_overnight_live_capture]] [[feedback_no_fake_data_ever]]).

## 6. 리뷰 워크플로우
G1 동일: Opus 5-agent → Codex fix → Codex 교차 → Opus 수렴(마지막 fix 덮기) → Docker 실QA 인라인 → CI green → 머지. 🚨 금액 계산 변경이므로 parity 회귀 + VAT 분리 정확성 최우선.

## 7. 리스크
- **VAT/DC 계산 변경 = 견적 금액 직결** → parity 미세 오차 = 오과금. golden 테스트 필수.
- 카드수수료 적용 기준(총액 vs 공급가) — 본 슬라이스 VAT 포함 총액 기준(개발책임자 A). 세부 상이 시 조정.
- 신규 page-code 권한 seed.
- 범위 경계: 세금계산서/거래명세서 VAT 중앙화는 본 슬라이스 제외(estimate-app 한정), F7/후속.
