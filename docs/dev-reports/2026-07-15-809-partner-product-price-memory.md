# #809 — 전표/견적 품목 추가 시 (거래처+품목) 최근 수동단가 자동채움

- **일자**: 2026-07-15
- **PR**: #820 (feat/809-partner-product-price-memory)
- **연관**: 이슈 #809·spec `docs/specs/809-slip-estimate-recent-manual-price-spec.md`
- **상태**: 구현 완료 → 캐논 리뷰 진행
- **민감도**: 🔴 가격 도메인 — 리뷰서 단가 로직 correctness 엄격 검증.

## 목표
전표(출고/입고)·견적 품목 추가 시 **(거래처+품목) 최근 사용 단가를 기억**해 자동채움(기존은 catalog 정가 `Product.sellingPrice`·거래처 무관).

## 개발책임자 결정 (2026-07-15 morning 확정·명확화 후 전부 PM 권고 수렴·PR #820 issuecomment-4975169714)
- ① 대상=**전표+견적**(주문 제외·주문은 DcConfig 규칙가 유지)
- ② 저장소=**slip-service 신규 테이블 `partner_product_price_memory`**(정찰 해소: 견적도 slip-service 영속 → cross-service 불필요)
- ③ 확정=라인 저장 시 upsert · ④ **effective(마지막 저장 라인단가) 기억**(source 태그·override 항상 우선)
- ⑤ VAT=라인과 동일 basis · ⑥ 키=**partnerId(UUID)**

## 구현

### 핵심 결정 3줄
- **저장 basis = VAT 포함 입력단가**. 전표/견적 화면 입력 필드가 곧 VAT 포함 단가(`priceVatInclusive=true`)이므로 그대로 저장·반환해 **라운드트립 무손실**. legacy 공급단가 경로(`priceVatInclusive` false/null)는 `×1.1` 정규화해 단일 basis 유지.
- **fail-soft = 호출자 책임**(#816 `DispatchNotificationRecorder` 선례 그대로). `PartnerProductPriceMemoryService.remember()` 는 `REQUIRES_NEW` 로 격리하되 **예외를 삼키지 않고 그대로 던지고**, 호출자(`SlipService.rememberPrice`·`EstimateService.rememberPrice`)가 try/catch(warn) 로 감싼다. → 가격기억 tx 만 롤백되고 전표/견적 저장은 커밋된다.
  - ⚠️ **초기 구현은 반대였다**(remember() 내부 catch·호출자 무방비). REQUIRES_NEW 는 예외를 내부에서 잡아도 **tx 가 rollback-only 로 마킹**되어 프록시 커밋 시 `UnexpectedRollbackException` 이 호출자로 전파된다 → 가격기억이 실패하는 바로 그 순간 전표 저장이 깨지는 **fail-soft 역전**. PM 검증 게이트에서 포착·재설계(#816 recorder Javadoc 이 경고하던 동일 함정의 재발).
- **READ 인가** = `GET /slips/price-memory`(사용자 대면·`/internal` 아님·브라우저 호출 가능). 전표 생성과 동등 — OUTBOUND 생성 **또는** INBOUND 작성 권한 중 하나.

### BE (slip-service)
- 신규 `price/domain/PartnerProductPriceMemory`(BaseEntity 7 audit + soft delete·`@SQLRestriction`·unique(partner_id, product_id))
- 신규 `price/repository/PartnerProductPriceMemoryRepository` — `findByPartnerIdAndProductId` + native `INSERT ... ON CONFLICT (partner_id, product_id) DO UPDATE`(단가/source 갱신 + **soft-delete 되살림**: deleted_at/deleted_by NULL·is_deleted FALSE)
- 신규 `price/service/PartnerProductPriceMemoryService`(find/remember)·`PartnerProductPriceMemoryResponse`
- 신규 `V58__create_partner_product_price_memory.sql`
- WRITE 배선: `SlipService.addSlipLinesExpanded`(단품·**BUNDLE 구성품 각 라인**)·`EstimateService.addEstimateLines`(단품·BUNDLE 구성품) → `rememberPrice(partnerId, productId, unitPrice, priceVatInclusive, actor)`. **partnerId/productId/unitPrice null 시 스킵**.
- READ: `SlipController.getPriceMemory` — hit=200/miss=**204**.
- **실 DB IT** `slip/it/PartnerProductPriceMemoryIT extends AbstractPostgresIT`(Testcontainers Postgres 16 + Flyway V58 실적용) — native SQL 은 컴파일 타임 검증이 없어 mock/문자열 테스트로는 기능 사멸을 못 잡는다: ① **라운드트립**(123456.78 저장→재조회 오차 0) ② ON CONFLICT 충돌갱신(**행 수 1 유지**·unit_price 갱신·created_by/modified_by audit) ③ soft-delete revive ④ **V58 UNIQUE 실제약**(중복 raw INSERT → DataAccessException) ⑤ **fail-soft 실증**(테스트 CHECK 제약으로 upsert 실패 유발 → 전표는 커밋·가격기억 row 0).
- CI: `.github/workflows/ci.yml` slip-units 잡에 `slip.price.domain.*`·`slip.price.service.*`·`slip.price.repository.*` **각각 명시 등재**(Gradle `--tests` 는 하위패키지 미커버 → 미등재 시 신규 테스트 CI 영구 미실행). IT 는 `slip.it.*`(slip-it-core) 기존 필터로 커버.

### FE (clients/desktop · design-system)
- partner-service `PartnerSummaryResponse.partnerId(UUID)` 노출 → desktop `partnerApi.ts`/`sales.ts` → design-system `PartnerOption.id?` 배선. **UUID 비공개 가드**: 화면 표시 금지·hidden state/API payload 전용(Javadoc/TSDoc 명시).
- 전표 create 페이로드에 `partnerId: selectedPartner?.id` 전송 → BE `slip.partnerId` 채워짐(**기존 null → 전표측 upsert 기능화**).
- `SlipFormPage.applyProductSelection` — 정가 자동채움 2경로(모바일 카드·데스크톱 테이블) 단일 함수로 통합 후 가격기억 조회 주입. hit=remembered/miss=`sellingPrice` 폴백. **override 보존**(기존 단가 있으면 미조회·미덮음) + **stale 가드 3중**(라인 id·productId 동일·`selectedPartnerIdRef` 일치·조회 중 사용자 수정 시 폐기).
- `EstimateFormPage.handleModelLookup` — 유효 partnerId UUID 시에만 조회(bizno 폴백값 조회 금지)·coedit provider mirror 반영.
- `api/slip.ts getPriceMemory()`(204→null)·`AddLineRequest.priceVatInclusive`.

### ⚠️ 의도된 동작 변화 (리뷰 검증 대상)
전표 create 에 partnerId 를 전송하면서, 기존 **dormant** 였던 slip-service 의 partnerId 기반 `businessNumber`/`partnerCode` snapshot resolve 가 **신규 전표에서 활성화**된다(fail-soft·기존 전표 무영향). 기존 denormalize 필드(`partnerName` 등)는 유지 — 회귀 금지.

## 검증

### 자동 테스트 (PM 독립 실행·genuine `--rerun-tasks --no-build-cache`·XML 집계 실측)
| 대상 | 결과 |
|---|---|
| slip-service | **suites=181 · tests=1240 · failures=0 · errors=0 · skipped=0** |
| partner-service | **suites=31 · tests=314 · failures=0 · errors=0 · skipped=0** |
| clients/desktop | `npm run typecheck` exit 0 · vitest **102 files / 697 tests** 전부 통과 |

#809 신규 테스트 (전부 skipped=0 — 실제 실행 실측):
`it.PartnerProductPriceMemoryIT` **5**(실 Postgres) · `price.service.…ServiceTest` 3 · `price.repository.…ContractTest` 2 · `price.domain.…Test` 1 · `web.SlipControllerPriceMemoryTest` 3 · `estimate.service.EstimateServicePriceMemoryTest` 2 · `SlipServiceTest` +2(VAT포함 입력값 정확 전달·partnerId null 스킵).

### 라이브 QA
_(리뷰 라운드에서 — Docker 실서버 실 GUI 스샷)_

## PM 검증 게이트 (커밋 전·구현 인수 검증)

Codex 구현 인수 시 PM 실측으로 **BLOCKING 4건** 포착 → 전부 Codex fix 후 재검증 통과. (Codex 자가보고 "targeted 테스트 통과"가 전체 스위트 실패를 가린 사례 — [[feedback_changed_module_full_test_before_push]] 재확증.)

| # | 결함 | 실측 근거 | 조치 |
|---|---|---|---|
| 1 | `SlipPermissionControllerIT` **48 FAILED** | `NoSuchBeanDefinitionException: PartnerProductPriceMemoryService` — `@WebMvcTest`+`@MockBean` 나열식 IT 에 새 `SlipController` 의존성 미모킹 → 컨텍스트 로딩 실패 | `@MockBean` 추가 + `GET /slips/price-memory` 를 **EndpointCase 권한 enforcement 테이블 등재** + 동형 sweep(`SlipServiceLockGuardTest`) |
| 2 | **CI allowlist 미등재 → 신규 테스트 CI 영구 미실행** | `ci.yml` slip-units 는 `--tests` allowlist 이고 Gradle `--tests` 는 하위패키지 미커버 → `slip.price.*` 3개 전부 CI 미실행 false-green | `price.domain.*`/`price.service.*`/`price.repository.*` **각각 명시 등재**([[feedback_ci_test_filter_false_green]]) |
| 3 | **fail-soft 역전**(가격기억 실패 시 전표 저장이 깨짐) | `remember()` 내부 try/catch + 호출자 무방비. REQUIRES_NEW 는 내부 catch 해도 tx 가 rollback-only 로 마킹돼 프록시 커밋에서 `UnexpectedRollbackException` 이 호출자로 전파 — **#816 `DispatchNotificationRecorder` Javadoc 이 경고하던 동일 함정의 재발** | `remember()` 는 예외를 그대로 던지고 **호출자**(`SlipService`/`EstimateService.rememberPrice`)가 try/catch — #816 선례 정렬 |
| 4 | **실 DB 검증 0** + 브리프 필수 라운드트립 미충족 | 신규 테스트 5개 전부 mock-only/문자열. `…RepositoryContractTest` 는 `Files.readString` 으로 **자기 소스의 substring 을 확인하는 동어반복** — native SQL 오타 시에도 초록불(#816 ③-A "실경로 0 레코드=비기능" 동형 리스크) | `PartnerProductPriceMemoryIT extends AbstractPostgresIT` 신설(실 Postgres+V58 실적용) — 라운드트립/ON CONFLICT/revive/UNIQUE 실제약/fail-soft 실증 |

### PM 교차확인 (genuine·비결함 판정)
- **VAT `×1.1` 정규화 parity**: `SlipLine` 도메인 규약이 **VAT 10% 고정**(`unitPriceWithVat = unitPrice*1.1`·공급가액 = 합계÷1.1·면세 구분 없음)이고 rounding 도 `setScale(2, HALF_UP)` 로 동일 → `rememberPrice` 정규화가 기존 규약과 일치. **결함 아님.**

## 리뷰 이력

_(캐논 듀얼 — Opus 5-agent ↔ Codex 적대 0수렴 진행)_
