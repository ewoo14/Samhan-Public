# #809 - 전표/견적 (거래처+품목) 최근 수동단가 자동채움

- **일자**: 2026-07-15
- **브랜치**: `feat/809-partner-product-price-memory`
- **연관**: 이슈 #809, spec `docs/specs/809-slip-estimate-recent-manual-price-spec.md`
- **범위**: 전표(출고/입고) + 견적. 주문(partner-order)은 제외.

## 핵심 결정

- 저장소는 `slip-service.partner_product_price_memory` 단일 테이블이다.
- 키는 `(partner_id, product_id)` 이며 soft delete revive 를 지원한다.
- 저장 basis 는 **VAT 포함 입력단가**다. `unitPriceWithVat` parity 가 권위값이고 `unitPrice` 는 파생값이다.
- 공급가 기준 저장은 전제 오류다. 전표/견적 입력 필드에 다시 채울 때 재사용마다 약 9.1% 단가 하락이 발생하므로 2026-07-15 개발책임자 승인으로 VAT 포함 기준으로 정정했다.
- BUNDLE 구성품은 기억하지 않는다. 세트 parent 품목만 사용자가 입력한 세트 단가를 `source='BUNDLE_SET'` 으로 기억한다.
- 수정/복사 경로도 포함한다. 전표 direct PUT 화면은 VAT 제외 단가 입력이므로 `unitPrice * 1.1` 로 저장소 basis 에 맞춘다.

## BE 구현

- `PartnerProductPriceMemory` 엔티티, repository, service, V58 Flyway migration 을 추가했다.
- `PartnerProductPriceMemoryService` 는 `Clock` 을 주입받고, 커밋 후 batch flush 에서 `REQUIRES_NEW` 트랜잭션을 1회 사용한다.
- 라인 루프에서는 가격기억 후보만 수집하고, 원 전표/견적 트랜잭션 `afterCommit` 에서 `(partnerId, productId)` dedupe 후 flush 한다.
- fail-soft 는 호출자/afterCommit flush 책임이다. `remember()` 는 예외를 삼키지 않고, batch flush catch 에서 warn 로그와 `slip_price_memory_upsert_failed_total` counter 를 남긴다.
- 전표 create/addLine, 출고 direct PUT, 입고 direct PUT, 전표 복사, 견적 create/update, 모바일 견적 create 를 배선했다.
- 조회 endpoint 는 `GET /slips/price-memory` 이며 `/internal` 이 아니다. 전표 생성/수정 또는 견적 생성/수정 권한 중 하나를 허용한다.

## FE 구현

- partner-service summary 의 `partnerId` 를 desktop `PartnerOption.id` 로 전달하되 화면에는 표시하지 않는다.
- 전표 create payload 에 `partnerId` 를 포함한다.
- 품목 선택 시 `(partnerId, productId)` 기억단가를 조회하고, hit 시 기억값, miss 시 catalog 정가를 채운다.
- `priceSource` 상태(`REMEMBERED`/`CATALOG`/`USER`)로 사용자 override 와 자동채움 값을 구분한다.
- 거래처 변경 시 자동채움 라인만 새 거래처 기준으로 재조회하고, 사용자 override 라인은 보존한다.
- 기억단가 hit 라인은 단가 셀에 `role="note"` 의 `최근가` 마커와 저장일 tooltip 을 표시한다.
- 견적 모델명 lookup 은 BE wire 계약(`id`, `name`)을 명시 변환해 `productId`, `productName` 으로 매핑한다.

## 검증 포인트

- 실 Postgres IT: VAT 포함 라운드트립 오차 0, ON CONFLICT 갱신, soft-delete revive, unique 제약, fail-soft 를 검증한다.
- BUNDLE IT: 구성품 미기억, 세트 parent `BUNDLE_SET` 기억, 원 트랜잭션 롤백 시 유령 단가 0건을 검증한다.
- 권한 IT: `GET /slips/price-memory` 403 enforcement 와 permission denied metric 을 검증한다.
- desktop typecheck/vitest 에서 견적 lookup 계약 정합, 기억단가 자동채움, 0원 기억단가, stale guard 를 검증한다.

## 검증 실측 (PM 독립 실행 · genuine `--rerun-tasks --no-build-cache` · XML 집계)

| 대상 | R1 fix 후 |
|---|---|
| slip-service | suites=180 · **tests=1241 · failures=0 · errors=0 · skipped=0** |
| partner-service | suites=31 · **tests=314 · failures=0 · errors=0 · skipped=0** |
| clients/desktop | typecheck exit 0 · vitest **103 files / 708 tests** 전부 통과 |

#809 신규 테스트(전부 skipped=0 실측): `it.PartnerProductPriceMemoryIT` **7**(실 Postgres — 라운드트립·ON CONFLICT·revive·UNIQUE 실제약·fail-soft·**BUNDLE 구성품 미기억/세트 parent 기억**·**롤백-오염 역방향**) · `web.SlipControllerPriceMemoryTest` 4 · `price.service.…ServiceTest` 3 · `estimate.service.EstimateServicePriceMemoryTest` 2 · `price.domain.…Test` 1 · **`SlipFormPage.test.tsx` 11 신설**(hit/miss/override/stale 3종/B-2 재조회/0원/최근가 마커/미선택/조회실패).

> ⚠️ 동어반복 `PartnerProductPriceMemoryRepositoryContractTest`(자기 소스 substring 검사)는 **삭제**하고 CI allowlist 도 정리했다(실 검증은 IT 가 전부 커버).

## PM 검증 게이트 (구현 인수 시 · 리뷰 진입 전)

Codex 자가보고 "targeted 테스트 통과"가 **전체 스위트 실패를 가린** 사례. PM 실측으로 **BLOCKING 4건** 포착 → 전부 fix 후 재검증 통과. ([[feedback_changed_module_full_test_before_push]] 재확증)

| # | 결함 | 실측 근거 | 조치 |
|---|---|---|---|
| 1 | `SlipPermissionControllerIT` **48 FAILED** | `NoSuchBeanDefinitionException: PartnerProductPriceMemoryService` — `@WebMvcTest`+`@MockBean` 나열식 IT 에 새 `SlipController` 의존성 미모킹 → 컨텍스트 로딩 실패 | `@MockBean` + price-memory 를 **권한 enforcement 테이블 등재** + 동형 sweep |
| 2 | **CI allowlist 미등재 → 신규 테스트 CI 영구 미실행** | Gradle `--tests` 는 하위패키지 미커버(파일 주석에 기존 교훈 명시) → `slip.price.*` 3개 전부 미실행 false-green | `price.domain/service/repository.*` 각각 명시 등재 ([[feedback_ci_test_filter_false_green]]) |
| 3 | **fail-soft 역전** | `remember()` 내부 catch + 호출자 무방비. REQUIRES_NEW 는 내부 catch 해도 tx 가 rollback-only 마킹 → 프록시 커밋에서 `UnexpectedRollbackException` 이 호출자로 전파 = **가격기억 실패 시 전표 저장이 깨짐**. #816 `DispatchNotificationRecorder` Javadoc 이 경고하던 **동일 함정의 재발** | 호출자 책임으로 재설계(#816 선례) |
| 4 | **실 DB 검증 0** | 신규 테스트 5개 전부 mock/문자열. `…RepositoryContractTest` 는 `Files.readString` 으로 **자기 소스 substring 을 확인하는 동어반복** → native SQL 오타에도 초록불(#816 ③-A "실경로 0 레코드=비기능" 동형) | `PartnerProductPriceMemoryIT extends AbstractPostgresIT` 신설 |

## 리뷰 이력

### R1 — Opus 5-agent (FE/BE/Design/DevOps/QA) · `8c95408bf` · [issuecomment-4976479928](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4976479928)
**BLOCKING 4 · HIGH 6 · MEDIUM 11 · LOW 8.** 5차원 전부 실행(Design N/A 없음).

**교차검증 신호**: DevOps·BE 가 **독립적으로 동일 결함**(REQUIRES_NEW 선커밋) 포착 · Design·FE 가 **독립적으로 동일 판정**(견적 bizno 폴백 = 회귀 아님) 도달 → 5-agent 다중 렌즈 가치 재실증.

주요 genuine:
- **B-1** BUNDLE 구성품에 카탈로그 **납품가**(`BundleExpander:107` `getDeliveryPrice()`) 각인 → 이후 단품 자동채움이 출고가를 납품가로 **영구 치환**(과소청구·카탈로그 인상돼도 memory 가 이겨 고착). 재배분은 `SINGLE_SET` 한정이라 상업멀티는 납품가 그대로. **BUNDLE 분기 진입 테스트가 0건**(`ProductSummary` 전부 `productType=null`)이라 리뷰까지 살아남음
- **B-2** 거래처 변경 시 이전 거래처 단가 잔류 → 저장 시 신규 거래처 기억 **오염(자기증식)**. 기존엔 정가(거래처 무관)라 함의 없던 경로를 본 PR 이 처음 만듦
- **B-3** **견적 DOA** — FE `ProductLookupResult`(`productId`/`productName`) ↔ BE `ProductSummary`(`id`/`name`) 계약 불일치(검증 없는 캐스팅 → tsc 통과·런타임 undefined). price-memory 400 + **라인 저장 자체 불가** → 견적 WRITE 훅 영구 미발화. 선재 결함이나 #809 가 신규 의존
- **B-4** spec ⑤ VAT basis **PM 무단 변경**(자기지적) → 개발책임자 승인 + spec 정정
- **H-1/H-3** REQUIRES_NEW **선커밋** → outer 롤백 시 **유령 단가 잔존** + 라인당 커넥션 2배가 정상 경로로 승격해 **D-SER-22 풀 사이징 근거 무효화**
- **H-2** 수정 경로(`SalesSlipUpdateService`·`SlipUpdateService`) 미배선 → 결정 ④ 이탈 · **M-2** 복사(`duplicateSlip`)가 `×1.1` legacy 로 `100,000 → 99,999.90` 리셋
- **H-4** mock `normalizeAdminPartner` partnerId 누락 → **mock 모드에서 기능 완전 死**
- **H-5** FE 테스트 전무 + **인접 테스트 구조적 false-green**(fixture 비-UUID 라 #809 경로 미진입) → "vitest 697 통과"가 #809 를 **전혀 보증하지 않음**
- **M-1** 견적 전용 권한(`estimates.list`)만 있는 사용자는 403 → `catch{}` 삼킴 → **무증상 사멸** · **M-3** wire 는 `number` 인데 FE `string` 선언 → **0원 기억단가가 falsy → miss 오판**(무상 품목에 정가) · **M-9** `aspectMetric` opt-out 이 권한거부 메트릭 회귀 가드에 구멍

**반증 실패(결함 없음)**: VAT 라운드트립(scale/rounding divergence 0 · 라이브 차이 0) · 견적 bizno 폴백 회귀(BE 가 `UUID` 타입이라 폴백은 **원래 400** — 본 PR 이 오히려 **데스크톱 견적 생성을 우발적으로 살림**) · V58 충돌(origin/main 최대 V57 · 열린 PR 1건) · 신규 IT 의 CI 커버(`slip.it` 직속 멤버) · 마이그 컨벤션 · 게이트웨이 라우팅/Security · UUID 비공개 가드(DOM 노출 0) · 용어 규약 · 결정 ① 주문 제외

### R1 fix — 개발책임자 결정 4건 반영 · [issuecomment-4976498183](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4976498183)
① spec ⑤ **VAT 포함 승인** + 정정 ② BUNDLE **구성품 제외 + 세트 자체 기억**(PM 권고 대비 상향) ③ **'최근가' 마커 도입** ④ **수정·복사 전부 배선**(PM 권고 대비 상향).

R1 findings 29건 전부 처리. Codex 가 `SlipFormPage.test.tsx` 작성 중 **구현 결함을 추가 발견해 보정**(자동채움 상태 라인이 새 품목 선택 시 재조회되지 않던 문제).

## R3 BE fix (CH-8/CH-9/CH-2/CM-6/CM-1/CM-7/CL-1/CM-8)

### bulk 조회 wire

| 항목 | 계약 |
|---|---|
| Method/Path | `POST /slips/price-memory/bulk` |
| Body | `{"partnerId":"UUID","productIds":["UUID", ...]}` |
| 검증 | `partnerId` 필수, `productIds` 1~100개, 원소 null 금지 |
| 성공 | `200 ApiResponse<List<...>>` |
| hit item | `{productId, unitPrice, source, updatedAt}` |
| 부분 hit | hit 만 최초 요청 순서로 반환. miss 와 중복 productId 는 생략 |
| 전체 miss | `204`가 아니라 `200` + `data: []` |
| 인가 | 단건과 동일한 `sales.slip.create:CREATE OR purchases.slip.edit:UPDATE OR estimates.list:CREATE/UPDATE`; 요청당 1회 결정 |

최대 100 UUID 를 GET query 로 보내면 UUID 본문만 약 3,600자이고 parameter/separator 를 포함하면
약 3.7KB 이상이다. Nginx/Netty 상한 안일 수는 있으나 보수적 2KB request-line 경계를 넘고 proxy마다
상한이 다르므로 조회용 POST body 를 채택했다. D-R3-1 에 따라 UUID query string 노출 자체는 정책상
허용이며, 선택 근거는 순수 URL 길이/운영 호환성이다. 단건 GET은 제거하지 않았다.

### 최신성·set-based 저장

- V58에 전용 `remembered_at TIMESTAMP NOT NULL`을 추가했다. command는 afterCommit 등록 전 원
  전표/견적 트랜잭션의 논리 저장 시각을 담는다.
- conflict 갱신은 `WHERE partner_product_price_memory.remembered_at <= EXCLUDED.remembered_at`일 때만
  수행한다. 동시 저장 A→B 뒤 afterCommit B→A 역전 테스트가 실 PostgreSQL 최종값 B를 단언한다.
- `modified_at`은 실제 DB flush 감사 시각으로 유지한다. 응답 `updatedAt`은 `remembered_at`으로
  전환했으므로 FE 최근가 tooltip은 이제 실제 원 문서 저장 시각을 받는다.
- N개 repository 호출을 동적 parameter의 단일 multi-row `INSERT ... ON CONFLICT` statement로
  바꿨다. 같은 저장 단위의 pair는 statement 전에 마지막 command로 dedupe한다.

### 라인/timeout/connection 판단

- 전표 생성·출고/입고 direct PUT·견적 생성/수정·모바일 견적 라인에 `@Size(max=100)`을 적용했다.
  실운영 문서가 보통 수십 라인이고 기존 설계 문서의 최대 예상은 20라인이며, 서비스 내부 bulk 표준도
  100건이다. 100은 정상 대량 문서를 막지 않으면서 500행 무제한 입력과 DB lock convoy를 차단한다.
- 가격기억 트랜잭션 전용 기본값은 `lock_timeout=1s`, `statement_timeout=3s`, transaction timeout=4s다.
  100행 단일 statement 정상 여유를 주되 보조 기능이 사용자 응답/Hikari를 장기 점유하지 않는 값이다.
- auth 동기 RestClient는 repo 표준과 같은 connect 2초/read 3초를 적용한다. 권한 4종 OR는 Java
  short-circuit로 바꿔 앞 권한이 true면 나머지 auth 호출을 하지 않는다.

CM-1은 TransactionTemplate timeout만 줄이는 안으로는 connection 획득 전 대기 시간을 완전히 제한할
수 없어 bounded async(core 2/max 4/queue 100)를 함께 채택했다. afterCommit callback은 enqueue 후
즉시 반환하므로 outer connection cleanup이 먼저 진행된다. 포화 시 caller-runs를 쓰지 않고 거부를
fail-soft 계측한다. 대안 durable outbox는 프로세스 crash 유실 방지에는 우수하지만 보조 단가 기능에
별도 테이블/worker/retention/backfill 운영을 도입하는 범위가 과해 이번에는 채택하지 않았다. bounded
async는 커밋 후 프로세스가 즉시 종료되면 작업이 유실될 수 있다는 trade-off가 있으며 runbook의 원
라인 기반 backfill로 복구한다. 또한 저장 직후 조회는 worker flush 전까지 짧게 이전 값/miss를 볼 수
있다. 정상 시 수 ms 수준이며 과부하 시 queue 대기만큼 늘 수 있다는 eventual read 계약을 수용했다.

### 계측·경보

Micrometer 실제 Prometheus registry `scrape()` 테스트로 아래 export 이름을 고정했다.

- `slip_price_memory_upsert_success_total`: 성공 statement의 command 수
- `slip_price_memory_upsert_failed_total`: DB 실패 또는 queue 거부 command 수
- `slip_price_memory_batch_size_{count,sum,max}`: batch 크기
- `slip_price_memory_upsert_duration_seconds_{count,sum,max}`: REQUIRES_NEW 지연

Prometheus `rule_files`에 `infrastructure/prometheus/rules/slip-price-memory.yml`을 배선하고
`sum(increase(slip_price_memory_upsert_failed_total{job="slip-service"}[5m])) > 0` 경보를 추가했다.
조치 절차는 `docs/runbooks/slip-price-memory-upsert-failure.md`다.

### 정책·마이그레이션·배포 문구

- D-R3-1: UUID는 화면 표시만 금지하며 API query/body는 유지한다.
- D-R3-3: soft-delete 거래처/품목이 연결된 기존 문서에서도 가격기억을 반환한다. 외부 생존 조회는 없다.
- CL-1: source schema를 `LINE_SAVE | BUNDLE_SET`으로 정정했다.
- CM-6: 아직 미머지인 V58의 `CREATE TABLE IF NOT EXISTS`를 제거하여 drift 배포가 실패하게 했다.
  **이미 구 V58을 적용한 로컬 dev DB는 checksum mismatch와 `remembered_at` 컬럼 부재가 함께 발생한다.
  PM은 DB 재생성을 우선하고, 데이터를 보존해야 하면 스키마를 최종 V58과 수동 정렬한 뒤에만
  `flyway repair`해야 한다. repair만 단독 실행하면 누락 컬럼이 생기지 않는다. 본 배치에서는 DB를
  직접 변경하지 않았다.**
- CM-8: 단일 `samhan-slip-service` compose 배포를 rolling이 아닌 recreate로 정정하고 readiness 대기,
  예상 중단 30~120초, 향후 다중 인스턴스의 write quiesce/outbox/backfill 주의를 추가했다.

### R3 BE 검증 실측

| 검증 | 결과 |
|---|---|
| `compileJava + compileTestJava --no-daemon` | **BUILD SUCCESSFUL** (20초) |
| 선별 단위/계약 5 suite | **19 tests, failure/error/skip 0** — bulk/4종 OR short-circuit/parity/100건/metric export/set-based 호출 |
| `PartnerProductPriceMemoryIT` | **9 tests, failure/error/skip 0** — fresh V58, bulk 실쿼리, afterCommit B→A 역전 guard 포함 |
| `SlipPermissionControllerIT` | **BUILD SUCCESSFUL** — 단건+bulk grant/deny enforcement 포함 |
| `docker compose ... config --quiet` | exit 0 |
| Prometheus v2.55.1 `promtool check config` | config valid, rule file 1개·rule 1개 SUCCESS |

PM의 최종 genuine `--rerun-tasks --no-build-cache` 전체 회귀는 별도 수행한다.
