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
- 품목 선택 시 `(partnerId, productId)` 기억단가를 조회하고, hit 시 기억값, miss 시 catalog
  판매가(`product.sellingPrice`)를 채운다(R4 D-R4-1 로 '정가' 표기 폐지).
- `priceSource` 상태(`REMEMBERED`/`CATALOG`/`USER`)로 사용자 override 와 자동채움 값을 구분한다.
- 거래처 변경 시 자동채움 라인만 새 거래처 기준으로 재조회하고, 사용자 override 라인은 보존한다.
- 기억단가 hit 라인은 단가 셀에 `role="note"`의 `거래처 최근단가` 마커와 원 문서 저장일 tooltip을
  표시한다. miss는 `판매가`로 명시하고(R4 D-R4-1 로 `정가`에서 개칭) input `aria-describedby`를
  연결한다. 라인 칩의 `aria-live` 는 R4-D2 로 제거 — 비동기 재적용 고지는 상시 마운트 배너
  (`role="status"`, 텍스트만 토글 — R4-D9) 1곳이 담당한다.
- 거래처 미선택 시 CATALOG 마커 설명은 거래처를 단정하지 않는 카피(`판매가를 적용했습니다`)로
  분기하고(R4-D4a), 거래처 해제 시 단가값·`priceSource` 는 유지하되 REMEMBERED 마커(저장일 포함)만
  해제한다(D-R4-4).
- 거래처 변경으로 실제 단가가 바뀐 행을 강조하고 `거래처 변경으로 최근단가 재적용 · 변경된 행을
  확인해 주세요.` 상태 배너를 표시한다.
- 거래처 변경 N개 라인은 `POST /slips/price-memory/bulk` 1회로 조회하고 hit-only 응답에서 생략된
  productId를 판매가 miss로 매핑한다. 고유 품목 101개 이상은 BE 계약 상한(100)에 맞춰 100개 단위
  chunk 순차 호출로 합산한다(R4-F5). 모델 blur/단일 품목 선택은 호환 단건 GET을 유지한다.
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
  100행 단일 statement 정상 여유를 주는 값이다. ⚠️ **R5-M1 정정**: 당초 이 절은 "보조 기능이 사용자
  응답/Hikari를 장기 점유하지 않는 값"이라 기술했으나, 이 4초 상한은 **커넥션 획득 대기를 포함하지
  않았다** — `TransactionTemplate` timeout 은 커넥션 획득 후에만 적용되므로 풀 고갈 시 worker 는
  Hikari 기본 30초 획득 대기에서 별도로 고착될 수 있었다(R4 의 1초 lock 실험도 "획득 이후"만 입증).
  R5 fix 가 Hikari `connection-timeout` 4초를 명시했고 이는 **가격기억 범위를 넘는 slip-service 전역
  동작 변경**이다 — blast radius 는 R6-M1 로 재지적돼 개발책임자 확인 대기다(아래 R5/R6 절).
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

- D-R3-1: UUID는 화면 표시만 금지하며 API query/body는 유지한다. DevTools Network는 사용자 화면이
  아니고 기존 `GET /slips/{id}`도 UUID URL을 사용한다. bulk POST는 UUID 은닉이 아니라 최대 100개
  UUID의 URL 길이 제약 때문에 선택했다.
- D-R3-3: soft-delete 거래처/품목이 연결된 기존 문서에서도 가격기억을 반환한다. UI 검색은 삭제
  엔티티를 신규 선택지에서 제외하지만 기존 문서 편집에는 단가 보존이 필요하며, 외부 생존 조회는
  CH-8 호출 증폭을 재발시키므로 추가하지 않는다.
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

## R3 FE 및 마지막 QA/테스트 보완

- PM 독립 FE 검증: desktop typecheck exit 0, desktop Vitest **104 files / 726 tests / 0 fail**,
  design-system **11 files / 41 tests / 0 fail** (`9ff6387f1`).
- 구매 PUT은 공급단가 `135000.00` 저장 후 가격기억 `148500.00`, non-legacy 복사는 VAT 포함
  `321000.00` 저장/조회 동일, 모바일 견적은 공급단가 `500000.00` 저장 후 가격기억 `550000.00`을
  실 PostgreSQL async flush 뒤 정확히 비교하는 IT를 추가했다.
- 같은 문서의 동일 pair는 마지막 라인 P2만 남고 set-based upsert가 1회 호출되는지 검증했다.
  `putIfAbsent()` mutation이면 P1이 남아 실패하고, dedupe 제거 mutation이면 실 PostgreSQL의
  `ON CONFLICT DO UPDATE cannot affect row a second time` 위험이 다시 노출된다.
- 주문 저장은 `PartnerProductPriceMemoryService` 무호출을 음성 테스트로 잠갔다. 외부 거래처/품목이
  soft-delete된 것으로 간주되는 orphan UUID도 활성 memory row를 반환하는 D-R3-3 의도 테스트를 추가했다.
- R2 라이브 QA는 견적 POST 500도 통과할 수 있는 CB-3 false-green이 확인되어 리포트 상단에
  **superseded** 정정 이력을 남겼다. 경화 스펙은 POST 2xx 응답 ID, 해당 ID의
  `unit_price_with_vat=P`, memory `unit_price=P`, 5초 유한 async 폴링을 사용한다.
- 경화한 라이브 스펙은 V58 로컬 DB 재생성 및 slip-service 재배포가 선행돼야 하므로 이번 배치에서
  실행하지 않았다. R4 라이브 QA 결과가 최종 증거다.

### 마지막 배치 선별 검증

| 검증 | 결과 |
|---|---|
| `:services:slip-service:compileTestJava --no-daemon` | **BUILD SUCCESSFUL** |
| `PartnerProductPriceMemoryServiceTest` + `MobilePartnerOrderServiceTest` | **BUILD SUCCESSFUL** — CM-9/CL-2 포함 |
| 신규 실 PostgreSQL IT 4건 | **4 passed** — 구매 PUT·복사·모바일 견적·soft-delete 편집 정책 |
| hardened Playwright spec 단일 파일 TypeScript 검사 | exit 0 |
| live Playwright | **미실행(의도)** — V58 DB 재생성·서비스 재배포 뒤 R4에서 수행 |

구매 PUT IT 첫 선별 실행은 create 응답 token 대신 DB 최신 `modifiedAt`이 필요한 기존 낙관적 잠금
fixture 계약 때문에 1회 실패했다. 기존 `SlipUpdateIT`와 동일하게 최신 token을 다시 읽도록 테스트를
교정한 뒤 해당 실 PostgreSQL IT가 통과했다. production 변경이나 production 결함은 아니었다.

## R4 — FABLE5 1차 적대검증 (캐논 4단계) · `e7a2ff0d6` · [issuecomment-4980286625](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4980286625)

**27건 (BLOCKING 0 · HIGH 4 · MEDIUM 9 · LOW 14).** 5차원(BE/FE/Design/DevOps/QA) 전부 실행,
R3(CODEX SOL 5.6) 30건 fix 자체를 적대 대상으로 재검했다. fix 는 캐논대로 라운드 모델(FABLE5)이
7배치로 수행했다 — 배치별 처리 건수: BE 2 · DevOps 4 · FE 6 · Design 6 · 통합정리 3 · sweep 2 ·
교차배치 1.

| 차원 | BLOCKING | HIGH | MEDIUM | LOW | 계 |
|---|---|---|---|---|---|
| BE | 0 | 0 | 0 | 5 | 5 |
| FE | 0 | **2** | 2 | 2 | 6 |
| Design | 0 | **1** | 3 | 5 | 9 |
| DevOps | 0 | 0 | 3 | 1 | 4 |
| QA | 0 | **1** | 1 | 1 | 3 |

### HIGH 4건과 fix

- **R4-F1 (HIGH·데이터오염)** — 견적에서 모델명으로 품목을 교체하면 이전 품목의 단가·`REMEMBERED`
  마커·저장일이 새 품목으로 그대로 승계됐다. 견적의 자동채움 판정이 R3 전표 fix semantics
  (`CATALOG`/`REMEMBERED` 라인도 재채움)를 누락한 전표/견적 비대칭이 원인 — 마커가 "이 거래처의
  신품목 최근단가"를 거짓 주장하고, 저장 시 `memory(거래처, 신품목)` 이 오염돼 이후 전 문서로
  전파된다. fix: 판정을 `clients/desktop/src/renderer/utils/priceSourceRules.ts` 공유 헬퍼
  (`shouldAutoFillPrice`/`isAutoPriceSource`)로 추출해 `SlipFormPage`/`EstimateFormPage` 가 동일
  함수를 쓰도록 비대칭 재발을 **구조적으로 차단**했다.
- **R4-F2 (HIGH·가격왜곡)** — legacy 견적(라인 `unitPriceWithVat` NULL) 편집-저장 시 공급단가가
  VAT 포함 필드로 hydrate 된 채 `priceVatInclusive:true` 로 재전송돼 **9.1% 하락이 영구화**되고,
  #809 가 그 하락값을 가격기억으로 upsert 해 거래처의 전 신규 문서로 전파했다. **R4 라이브 DB 실측:
  `estimate_lines` 1927/1927 = 100% legacy** — 전수 노출. R3 CH-1 이 동일 family 를 전표 복사
  (`duplicateSlip`)에서만 고치고 견적 편집을 놓친 **계열 sweep 누락**([결함 fix 계열 전수 sweep]
  위반, pre-existing 이나 오염 전파 경로는 #809 신설이라 범위 내). fix: hydrate 시 원 공급단가를
  `legacySupplyUnitPrice` 로 박제하고, 저장 시 사용자가 단가를 수정하지 않은 라인
  (`unitPrice === legacySupplyUnitPrice`)만 `priceVatInclusive:false` 로 전송한다.
- **R4-D1 (HIGH·a11y)** — 강조행 배경 `--surface-selected`(#EFF6FF) 위 `#6B7280` 라벨 =
  **4.44:1 로 AA(4.5:1) 미달**. 흰 배경에선 4.83:1 통과라 **#809 의 강조행이 새로 유발한 회귀**
  (R3 대비 확인 2쌍에 미포함 — 리뷰어가 R3 확정값 재현으로 산식 신뢰를 확보한 뒤 계산, CSS var
  fallback 함정(#776)도 반영해 토큰 실값 기준). fix: 강조행 한정 `--ink-secondary`(실값 #5C6773)
  상향 = **5.30:1 PASS** — `global.css` 모바일 카드 라벨 + `EstimateFormPage` 행번호 inline 의
  2지점(계열 sweep 결과 미달은 이 2지점뿐).
- **R4-Q1 (HIGH·QA인프라)** — real-qa 렌더러가 **stale design-system dist**(로컬 빌드 07-08,
  gitignore 산물)를 서빙해 #809 FE 변경분(마커·강조)이 화면에 통째로 부재했다. 마커 단언이 없던
  구 스펙이었다면 그대로 false-green. 라운드 내 해결: `vite.809-realqa.config.ts` 신설 —
  design-system 을 **브랜치 소스로 alias**(+ react dedupe)해 dist 재빌드(동시 리뷰 에이전트와의
  빌드 경합) 없이 현 브랜치 코드를 서빙. R2 라운드의 "dist 에 마커 문자열 부재 — 재구성 불가"
  미제도 PC 간 dist 차이로 설명·해소됐다.

### 🔴 개발책임자 결정 4건 — [issuecomment-4980376041](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4980376041)

4건 모두 PM 권고안 수렴. 결정 전문은 PR 기록과 `migration/decisions/DECISIONS.md` R4 절.

| ID | 결정 | 반영 |
|---|---|---|
| **D-R4-1** | 마커 용어 `정가` → **`판매가`** + spec 38-39 정정 | 자동채움 실체 = `product.sellingPrice` = 제품 등록 화면 라벨 "판매가". 기존 용어체계의 '정가'는 출고가 계열 별칭(estimate-app `lib/code.js` 동의어 매핑 실재)이라 오도. spec 자체가 기존 결정과 상충한 사례 → spec 정정 처리 |
| **D-R4-2** | 최신성 권위 = **`remembered_at` 캡처 시각 유지** | 코드변경 0. 잔존 창은 아래 정직 한계 ② 에 기록 |
| **D-R4-3** | 서브-원 드리프트 **수용 + 문서화** | 코드변경 0. 아래 정직 한계 ③ 에 기록 |
| **D-R4-4** | 거래처 해제 시 **단가 유지 + 마커만 해제** | 단가를 판매가로 되돌리지 않음. 도달성 한계는 아래 정직 한계 ⑦ |

### MEDIUM 9 · LOW 14 처리 요약

| ID | 급 | 요지 | 처리 |
|---|---|---|---|
| R4-F3 | M | 견적 lookup 결과가 과잉 staleness 게이트로 통째 폐기 — 단가 mid-flight 입력/거래처 변경 시 productId 소실·저장 차단(사유 무표시) | fix — 품목 필드는 uid+modelName+requestId 로만 적용, 가격 3필드(`unitPrice`/`priceSource`/`priceMemoryUpdatedAt`)만 게이트 |
| R4-F4 | M | 거래처 변경 bulk 재조회 in-flight 중 저장 가능 → 구 거래처 단가가 신 거래처 memory 로 교차 오염 | fix — `lookupLoading` 기반 busy 를 `canSubmit` 게이트에 포함(전표/견적) |
| R4-D2 | M | 마커 `aria-live` 가 라인마다 부착 → N행 flip 시 낭독 큐 적체(spec 은 배너 1곳만 요구한 spec 초과 구현) | fix — 칩 `aria-live` 전부 제거, 전역 고지=배너 1곳 + `aria-describedby` 유지 |
| R4-D3 | M | `정가` 용어가 기존 체계와 불일치 | → **D-R4-1** |
| R4-D4 | M | (a) 거래처 미선택인데 "이 거래처에…" 단정 카피 (b) 거래처 해제 시 마커·기억단가 잔존 | (a) fix — 미선택 시 `판매가를 적용했습니다` 분기 (b) → **D-R4-4** 마커만 해제 |
| R4-O1 | M | R1 fix 커밋에 `ci.yml` 전면 영문화 + 제도 기억 주석 삭제가 미신고 혼입 | fix — 한국어·주석 복원(기능 diff 0 실증), allowlist 변경만 잔류 |
| R4-O2 | M | 신규 운영 env 9종(`SAMHAN_PRICE_MEMORY_*` 7 + `SAMHAN_AUTH_*_TIMEOUT_MS` 2)이 env-template 미동기화 — 런북이 안내하는 튜닝 노브가 소스 밖 어디에도 없음 | fix — `infrastructure/env-templates/slip-service.env` 등재 + 런북 상호참조 |
| R4-O3 | M | upsert 실패 경보가 dev(Prometheus rule) 전용 — prod 는 CloudWatch 일원화(Phase 11 기결정)인데 등가물·이식 계획 부재 → prod 에서 fail-soft+health UP 특성상 무한정 미감지 가능 | fix — 런북에 "dev 로컬 스택 전용" 실효 경계 명시 + `infrastructure/terraform/CUTOVER.md` **M-19**(awslogs 배선 + Logs metric filter 2건 + alarm) 이식 절차 신설 |
| R4-Q2 | M | dev 시드 `TEST-BUNDLE-SET-01` 내부 불정합으로 이 세트는 저장 자체 불가 | 🔶 범위 외(아래 절) — `QA797-SET-01` 로 우회 실증 |
| R4-B1 | L | `priceMemoryExecutor` 가 최초 `Executor` 빈이 되며 Boot `applicationTaskExecutor` 자동구성 back-off — 향후 `@Async` 도입 시 무관 작업이 가격기억 전용 4스레드 AbortPolicy 풀을 조용히 점유하는 트랩 | fix — `applicationTaskExecutor`/`taskExecutor` 를 auto-config 동형(`@Lazy` + `ThreadPoolTaskExecutorBuilder`)으로 명시 복원 + `PartnerProductPriceMemoryAsyncConfigTest` 신설 |
| R4-B2 | L | 다중행 upsert 가 문서 라인 순서로 행 잠금 — 교차 순서 동시 flush 시 PostgreSQL deadlock → fail-soft 배치 통째 유실 | fix — dedupe 후 `(partnerId, productId)` 전역 정렬(`PAIR_LOCK_ORDER`)로 구조적 소멸 + 단위테스트 2건 |
| R4-B3 | L | `remembered_at` 캡처 시각 vs 실제 커밋 순서 역전 창 | → **D-R4-2**(코드 0) |
| R4-B4 | L | 서버 시계 역행 시 recency guard 가 조용히 skip | 수용+기록 — 정직 한계 ④ |
| R4-B5 | L | create(VAT포함)→직접 PUT(공급단가) 교차 경로 서브-원 드리프트 | → **D-R4-3**(코드 0) |
| R4-D5 | L | 기억 저장일이 hover `title` 전용 | 수용+기록 — 정직 한계 ⑤ |
| R4-D6 | L | 단가 input 22px 축소가 칩 없는 행까지 일괄 적용 — 동행 28px input 과 상시 높이 불일치 | fix — `:has(.priceMemoryNote)` 조건부화 |
| R4-D7 | L | 마커 칩 font-size 10px/11px 하드코딩(DS 스케일 이탈) | fix — `--badge-channel-font-size-sm` 토큰 인용(LineRow 칩 + desktop 강조 칩) |
| R4-D8 | L | 강조행 시각이 기존 선택행과 유사 | 수용+기록 — 정직 한계 ⑥ |
| R4-D9 | L | 배너 live region 이 내용과 함께 조건부 마운트 — 일부 SR 미낭독 | fix — 빈 `role="status"` 상시 렌더 + 텍스트만 토글(전표/견적) |
| R4-F5 | L | 고유 품목 101개 이상이면 FE bulk 가 throw → catch 에서 전 라인이 조용히 판매가(CATALOG) 강등 | fix — `getPriceMemories` 100개 단위 chunk 순차 합산 |
| R4-F6 | L | 자동채움 provider write 의 doc-sync 반영이 pending 분류를 USER 로 덮어 마커 소멸 에지 | fix — `CollaborativeSlipInput` 에 `onDocSyncValueChange` 분리(실입력 부수효과와 격리) |
| R4-O4 | L | `slip.config.*` 가 어느 CI allowlist 에도 없어 `HeaderAuthenticationFilterTest` 영구 미실행(레포 concrete 182 vs CI 실행 181 전수 대조로 특정한 pre-existing) | fix — `ci.yml` slip-units 필터 등재(+ R4-B1 신규 테스트용 `slip.price.config.*` 도 등재) |
| R4-Q3 | L | 견적 쪽 거래처 변경 bulk/배너·miss 마커가 라이브 스펙 미커버(전표로만 실증) | fix — 라이브 스펙 시나리오 08~10 신설(아래 라이브 QA) |

적대 반증(무결 확인)도 다수 — `localAutoPriceWritesRef` "같은 값 재입력 USER 승격 누락" 성립 불가,
`set_config` 동일 커넥션(psql `FOR UPDATE` 12초 잠금 반증 실험), dedup 우회 진입로 0, recency guard
`<=` 방향 정합, Micrometer `_total_total` 사실무근 등. 전문은 R4 리뷰 게시분.

### R4 fix 검증 실측

| 대상 | 결과 |
|---|---|
| slip-service | suites=183 · **tests=1269 · failures=0 · errors=0 · skipped=0** |
| partner-service | suites=31 · **tests=314 · failures=0 · errors=0 · skipped=0** |
| design-system | **45 tests · 0 fail** |
| clients/desktop | typecheck exit 0 · vitest **740 tests · 0 fail** |

PM 독립 genuine 실행(`--rerun-tasks --no-build-cache`). 문서 배치가 slip/partner 수치를
`build/test-results` XML 재집계로 교차확인했다(2026-07-15 22:13/22:17 산출물, 상기 수치와 일치).
desktop/design-system 카운트는 vitest 결과가 파일로 남지 않아 PM 실측 보고 수치를 기록한다.

### R4 라이브 QA (Docker 실서버 · mock OFF · `:8080` · 실 GUI)

- 이 PC 의 slip-service 컨테이너가 07-11 빌드(V58 미적용·테이블 부재) stale 이어서 브랜치 코드로
  재빌드·재배포 후 실측했다(V58 checksum `1743979716`, `remembered_at NOT NULL`, unique 제약 확인).
- **pre-fix(적대검증이 검증한 대상): 스펙 7 시나리오 7/7 PASS · 실캡처 14장
  `docs/qa/809-partner-product-price-memory/r4/`** (`e7a2ff0d6` 에 커밋). 스펙은 R3 fix 신규 UI
  미단언 3항목을 메워 **단언 +17 강화 · 약화 0**(R3 가 적발한 false-green 패턴 부활 0).
- **post-fix: 시나리오 08(R4-F4 busy/저장차단 + R4-D9 상시 마운트)·09(R4-F1 품목 교체 무승계)·
  10(R4-D4a 미선택 카피 + R4-D2) 3건 신설 → 10/10 PASS · 실캡처 23장 `r4-postfix/`**
  (Playwright `.last-run.json` = passed 실측).

## R5 — CODEX SOL 5.6 2차 적대검증 (캐논 5단계) · `810a5efd1` · [issuecomment-4982532949](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4982532949)

**22건 (BLOCKING 1 · HIGH 8 · MEDIUM 9 · LOW 4, 중복 병합 후).** 5차원(BE/FE/Design/DevOps/QA)
전부 실행, R4 fix 자체를 최우선 적대 대상으로 재검했다. fix 는 캐논대로 라운드 모델(CODEX SOL
5.6)이 4배치로 수행했고 전건 disposition 됐다 —
[fix 완료 게시 issuecomment-4983603804](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4983603804).

| 차원 | BLOCKING | HIGH | MEDIUM | LOW |
|---|---|---|---|---|
| BE | 0 | 1 | 1 | 1 |
| FE | 0 | **2** | 2 | 0 |
| Design | 0 | **1** | 2 | 2 |
| DevOps | **1** | 1 | 4 | 1 |
| QA | 0 | **3** | 0 | 0 |

| fix 배치 | 커밋 | 범위 |
|---|---|---|
| BE·DevOps | `ffc8e49ab` | H1·M1·L1 / H5·M6·M7·M8·M9·L4 |
| FE·Design | `5e46b1ce4` | **B1**·H2·H3·H4·M2·M3·M4·M5·L2 |
| QA 커버리지 | `710979e26` | H6·H7·H8 시나리오 신설 — 최초 13 PASS/1 FAIL suite RED 정직 보고 |
| R5-H6 재설계 | `e178c12cb` | legacy 견적 가격 basis 실증 재설계 → 스위트 14/14 GREEN |

### BLOCKING — R5-B1: PM 의 "검증 green" 보고가 틀렸음 (정정)

PR HEAD `810a5efd1` 의 CI 가 실제로 실패 중이었다 — `EstimateFormPage.coedit.test.tsx:708` 이 저장
버튼 활성화만 대기해 비동기 hydration 완료를 보장하지 않아, 느린 CI 에서 초기값 `'0'` 을 읽는
**flaky**(run 29423568108 실측: 1 failed / 739 passed). 🔴 **PM 정직 정정: R4 fix 완료 게시의
"desktop vitest 740/740" 은 로컬 통과였고, 로컬 통과가 CI green 을 보장하지 않았다 — 그 시점 머지
게이트 ⑩ 은 미충족이었다.** 이 테스트는 R4-F2 의 회귀 테스트인데 R5-H2 가 밝혔듯 진짜 버그 경로를
우회하면서 동시에 flaky 였다. fix 후 진짜 게이트에서 확인 — **CI(exact SHA `e178c12cb`/`710979e26`)
36/36 SUCCESS**.

### HIGH 8건과 fix

- **R5-H1 (HIGH·데이터오염·결정 ② 정면 위배·라이브 CONFIRMED)** — BUNDLE 세트를 **무수정 편집
  저장**만 해도 update DTO 에서 `setHead`/`parentSetModel` 이 소실돼 BE 가 구성품을 일반 단품으로
  재생성 → 구성품 배분가가 `LINE_SAVE` 로 기억 오염(전표 `2026/07/15-11`·견적 `2026/07/16-1`
  라이브 실측 — DB 오염행 4건을 PM 이 psql 로 재확인). R1~R4 전 라운드가 생성(POST) 경로만 검증한
  **계열 sweep 누락**. fix: **`BundleLineageResolver` 신설 — 설계 (b) 서버측 계보 resolve** 채택.
  (a)안(FE 가 계보 전송)은 타 클라이언트(모바일·API 직접)에서 동일 오염이 재발하므로 기각 — FE
  계약 변경 0. 무수정 PUT 후 lineage snapshot 완전 동일·구성품 기억행 0 을 라이브 실증.
- **R5-H2 (HIGH·가격왜곡)** — R4-F2 의 "미수정 판정"이 **타입 불일치로 양방향 오판**: BE JSON
  number vs FE string DTO 선언이라 hydrate 값은 runtime number 인데 coedit seed 는 `unitPrice` 만
  문자열 정규화 → `"10000" !== 10000` 으로 미수정을 수정으로 판정(9.1% 하락 재현), 반대로 값을
  되돌린 실제 편집은 미수정으로 오판. R4 회귀테스트는 string fixture + coedit 강제 실패로 진짜
  경로를 우회하고 있었다. fix: 응답 경계 금액 canonical 정규화 + 값 비교 대신 **명시적
  provenance(`legacyPriceUntouched`)** 보존. 라이브 실증: 무수정 PUT 후 `unit_price=1920000.00`
  불변·버그가 만들 오판값 `1745455.00` 미발생·기억 `2112000.00`(×1.1 basis) 정확·역방향 되돌림은
  실제 편집으로 판정.
- **R5-H3 (HIGH·데이터오염)** — R4-F3 분할 게이트가 lookup **중간 상태 저장**을 허용: 모델 lookup
  과 가격조회 사이에 거래처가 바뀌면 품목 게이트만 통과해 `productId=Y·unitPrice=0·loading=false`
  로 저장 가능 → 0원 라인 + 가격기억 0 오염. 기존 테스트가 이 중간 상태를 정상으로 단언해 결함을
  고정하고 있었다. fix: 품목 바인딩 후 가격이 stale 이면 현재 거래처+새 productId 로 **재resolve**
  하고 완료까지 busy(`최근단가 확인 중…`) 유지·저장 차단. 라이브 실증: hold 중 강제 click 에도
  POST 0건, 응답 후 저장 1건·BE 공식 `round(888000/1.1)` 과 DB exact 일치.
- **R5-H4 (HIGH·a11y)** — R4-D1 의 sweep 이 **전표 `LineRow` 의 `--ink-tertiary` 계열을 누락** —
  `#8A95A4 on #EFF6FF` = **2.79:1**(행번호·드래그핸들·placeholder·삭제 아이콘). R4 는 이 쌍을
  "`.selected` 와 동일쌍 = 선재 parity" 로 느슨 분류했으나 #809 강조행이 그 배경을 새로 적용하므로
  새 인스턴스가 맞다. fix: 5.30:1 상향 + 전수 감사에서 미달 3건 추가 교정(info inset border
  1.31 · 행 구분선 1.16 · input hover border 1.42) + `LineRow.contrast.test.tsx` 회귀 가드 신설.
- **R5-H5 (HIGH·false-green)** — 레포 전체 CI union 대조에서 **shared 3모듈 concrete 테스트
  7클래스가 어느 workflow 에서도 영구 미실행**(approval-core 3 · ecount-io 2 ·
  notification-publisher 2) — R4-O4 의 sweep 이 slip-service 에 국한됐음이 드러남. fix: test task
  등재. 실측 approval-core **16** · ecount-io **13** · notification-publisher **4** 전부 0 fail —
  "오래 미실행이라 깨져 있을 우려"는 기우로 확인.
- **R5-H6~H8 (HIGH·QA커버리지)** — R4 QA 10/10 PASS 가 위 HIGH 들을 구조적으로 못 잡음이 확정:
  스펙 전체에 legacy 견적 편집 `PUT /estimates/{id}` 단언 0건(H6 → R4-F2 false-green) · BUNDLE
  편집 PUT 미검증(H7 → R5-H1 false-green) · 모델 lookup↔가격조회 사이 중간 창 미검사(H8 → R5-H3
  false-green). fix: 시나리오 11(legacy 무수정/되돌림 가격 basis)·12(BUNDLE 무수정 PUT)·13(lookup
  중간 상태 저장 차단) 신설 — 최초 배치 13/1 RED 를 정직 보고한 뒤 H6 를 재설계해 14/14 GREEN.

### MEDIUM 9 · LOW 4 처리 요약

| ID | 급 | 요지 | 처리 |
|---|---|---|---|
| R5-M1 | M | 4초 tx 상한이 **커넥션 획득 대기 미포함** — `TransactionTemplate` timeout 은 획득 후 적용, 풀 고갈 시 worker 가 Hikari 기본 30초 대기서 고착 → 큐 100 포화 → 연속 거부·유실. R4 의 1초 lock 실험은 "획득 이후"만 입증 | fix — Hikari `connection-timeout` 4초 명시. ⚠️ **slip-service 전역 동작 변경**(가격기억 범위 초과) → R6-M1 재지적·개발책임자 확인 대기 |
| R5-M2 | M | R4-F5 chunk 후반 실패가 앞 chunk 정상 hit 까지 폐기 → 기존 기억값을 판매가로 덮음 | fix — chunk 별 성공/실패 분리 반환 |
| R5-M3 | M | mock 가격기억 조회가 `partnerId` 를 안 읽음 → 거래처 격리 회귀를 mock hard gate 가 미포착 | fix — (partnerId, productId) 복합키 + 계약 검증 (→ R6-M3 이 이 fix 의 RFC-4122 강제와 mock 거래처 id 불일치를 재적발) |
| R5-M4 | M | 칩 `aria-live` 제거(R4-D2) 후 **최초 lookup 결과의 단가 출처가 SR 에 자동 고지되지 않음** | fix — 페이지 단일 status region(sr-only) 1곳에서 lookup 결과 1회 고지 |
| R5-M5 | M | 변경행 식별이 색상에만 의존 + SR 행별 변경 상태 없음 — R4-D8 수용 근거가 약했음(4px 보더도 색·칩 텍스트는 '변경'을 말하지 않음) | fix — `단가 변경` 아이콘+텍스트 인디케이터 + 행 `aria-describedby` 연결 |
| R5-M6 | M | R4-O2 env 템플릿 9종이 compose/user_data 실행 경로에 미연결 — 운영자가 바꿔도 기본값 동작 | fix — compose 매핑(라이브 컨테이너 env 9종 주입 실측) |
| R5-M7 | M | R4-O3 이 만든 M-19 의 전제 오류(`user_data.sh` 는 이미 CloudWatch Agent 로 수집 중) + 실행 불가 절차 | fix — 기존 Agent 경로 기준 재작성 + Terraform metric filter 2·alarm 2 (→ R6-H5 가 전달 신뢰성 전제를 재반박, 재fix 대상) |
| R5-M8 | M | nightly 실패 Issue 자동생성이 존재하지 않는 label(`ci` 등)로 항상 실패 | fix — 실재 label + 무라벨 선생성 fallback |
| R5-M9 | M | #821 fix 가 머지 전 한 번도 미실행 + **본 dev-report 의 nightly 문단이 현 diff 와 모순** | **부분 이행** — PR ref `workflow_dispatch` run 29431175485 success 실증. dev-report 정정은 당시 미이행(R6-M7/M10 재지적) → 본 R6 문서 배치가 정정 수행 |
| R5-L1 | L | R4-B1 명시 빈이 Boot virtual-thread 분기 무력화(현 JDK17+설정 없음이라 inert) | fix — threading 조건 분기 복원 (→ R6-L3 잔여 편차 2건 재지적·현재 inert) |
| R5-L2 | L | spec 32행이 견적 수정 UI 의 VAT 기준을 실코드와 반대로 기술 — 후속 구현자가 따르면 10% 과대 기록 | fix — spec 정정(`5e46b1ce4`) |
| R5-L3 | L | 데스크톱 빌드 산출물에 Pretendard woff/woff2 0건 — 미설치 환경은 fallback 폰트 렌더 | #809 무관 선재 → 범위 외(아래 절) |
| R5-L4 | L | `--tests package.*` 는 text wildcard — "서브패키지 자동 미포함" 레포 주석이 부정확 · `vendor.*` dead token | fix — 주석 정정 + zero-match 토큰 정리 |

### 🔴 PM 정직 정정 3건 (R4·R5 게시에서 잘못 보고했던 것)

1. **"CI green"** — 로컬 740/740 통과 ≠ CI green(R5-B1). R4 fix 완료 게시 시점의 머지 게이트 ⑩ 은
   미충족이었다. 검증 권위는 exact SHA 의 CI 다.
2. **R4-F2 도달성 과장** — "estimate_lines 1927/1927 = 100% legacy 라 전부 오염 대상" 게시는
   과장이었다. **실 legacy 1,926건 전부 `partner_id NULL`** → 가격기억 upsert 가 null-skip →
   **현 실데이터로는 legacy 오염 경로에 도달 불가**. fix 자체는 유효하다(거래처를 재선택하는 순간
   경로가 열린다).
3. **"#809 회귀" 가설 반증** — legacy 견적 저장이 `거래처 정보를 다시 불러올 수 없습니다` 로 막히는
   것은 **main 도 동일**하다(main hydrate 가 `setPartner` 미호출 → `!partnerIdSnapshot && !partner`
   로 동일 차단 — 소스 대조 완료). #809 은 차단을 도입한 게 아니라 **오류 메시지만 더 정확하게**
   바꿨다. 따라서 앱 결함이 아니라 R5-H6 최초 스펙의 기대가 틀렸던 것(main 에서도 불가능한 일을
   단언) → 거래처 재선택 전제로 재설계해 14/14 GREEN.

### R4 판단 중 R5 가 뒤집은 것

R4-D8 수용 근거(→ R5-M5 fix) · R4-D1 "선재 parity" 분류(→ R5-H4 fix) · R4-O3 "CloudWatch 배선
부재" 전제(→ R5-M7 재작성) · R4-O4 sweep 범위(slip-service 국한 → R5-H5) · R4 의 nightly "무해한
dead-weight" 판단(실제 실패 중이었음 — R4 fix 게시에서 기정정).

### R5 가 반증한 것 (R4 fix 무결 확인)

R4-B2 정렬(dedupe 후 정렬·마지막 라인 승리 유지·역순 잠금 반례 없음) · R4-B1(builder 빈은
back-off 비대상·unqualified `Executor` 주입/`@Async`/`@EnableAsync` 전부 0건) · R4-O1 복원(기능
diff 는 test-task 1줄뿐 — 단 PM 의 "주석 3줄"은 물리적으로 4줄이었던 경미한 부정확) · #821 필터
정렬(public 0→11 증가·커버리지 감소 0) · R4-F1(자동채움 판정 지점 통일·잔존 0) · R4-F4(영구 busy
경로 미발견).

### R5 fix 검증 실측 (PM 독립 genuine `--rerun-tasks --no-build-cache`)

| 대상 | 결과 |
|---|---|
| slip-service | **tests=1273 · 0 fail/error/skip** |
| partner-service | **tests=314 · 0 fail/error/skip** |
| shared 3모듈 (R5-H5 신규 등재) | approval-core **16** · ecount-io **13** · notification-publisher **4** — 전부 0 fail/error/skip |
| clients/desktop | typecheck exit 0 · vitest **745 · 0 fail** |
| design-system | **46 tests · 0 fail** |
| CI (exact SHA `e178c12cb`) | **36/36 SUCCESS** |

### R5 라이브 QA (Docker 실서버 · mock OFF · 실 GUI)

**14 PASS / 0 FAIL / 0 SKIP** — 단언 약화 0·강화만(`expect(` 104 → 171 · 삭제 0 · 독립 test
10 → 14). 실캡처 **36장** `docs/qa/809-partner-product-price-memory/r5-postfix/`(기존 `r2/`·`r4/`·
`r4-postfix/`·`r5/` 무손상). R5-H3 의 지연 재현은 `route.fetch()` 로 실 upstream 응답을 받아
무변조 hold/전달(합성 0). 테이블 전체 DELETE 0건 — 정리는 정확한 `(partner_id, product_id)`
교집합만.

R5 시점 정직 미커버: **정상 coedit 연결 상태에선 거래처 autocomplete 가 잠겨 legacy 견적 재선택
불가** → R5-H6 검증은 coedit 실패 → 평문 폼 fallback 으로 진입(가격·PUT·DB 는 전부 실서버). 정상
협업 모드의 거래처 재선택 UX 는 잔존 과제로 기록했고 **R6-H6 이 이를 데드락으로 확정**했다.
legacy `QUOTE_SENT` 실표본 0(**R5 시점** 1,926행 전부 `QUOTE_DRAFT` — ⚠️ 이 수치는 재시드로 **stale**,
현재 legacy 0건. §R7 실측치 정정 참조) · BUNDLE 구성품 가격을 사용자가 직접
수정하는 경로와 전표 autocomplete 선택의 별도 중간 창은 범위 밖.

## R6 — FABLE5 재수렴 (캐논 6단계) · `e178c12cb` · [issuecomment-4984027656](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4984027656) — 🔴 **0수렴 실패**

**22건 (HIGH 6 · MEDIUM 10 · LOW 6, 중복 병합 후 — 원시 29건).** 5차원 전부 실행, R5 fix 자체를
최우선 적대 대상으로 재검했다. **머지 불가 판정.**

### 🔴 구조적 패턴 — 6라운드 연속 "fix 가 새 결함을 낳음"

```
R1 M-2 fix  → R3 CH-1(9.1% 과소) 유발
R3 CH-1 fix → 계열 sweep 누락 → R4 F2 로 귀환
R4 F2/F3/D1/D2/D8/O3/O4 fix → R5 가 전부 새 결함/오판으로 적발
R5 H1/H2/H4/M1/M3/M7 fix    → R6 가 또 전부 새 결함으로 적발
```

속도보다 충실도 — 각 라운드 fix 가 자기 계열의 전수 sweep 과 "fix 를 되돌리면 fail 하는" 회귀
가드를 갖추지 못한 것이 근본 원인이다.

### HIGH 6건

- **R6-H1 (라이브 CONFIRMED ×2 변형)** — `BundleLineageResolver` 의 greedy fallback 이 계보를
  **오귀속**: exact 매칭 실패 시 "첫 미소비 동일 productId" 를 잡아, 신규 단품 라인이 세트 head
  계보를 탈취하고 진짜 구성품이 평면화된다 → R5-H1 이 막았다던 배분가 오염이 재생산(BE 차원 전표
  `2026/07/16-23` + QA 차원 견적 변형 — **두 차원 독립 라이브 실측**). 평면화된 구성품은 이후
  저장마다 재기억되는 **자기강화 루프**. spec invariant("구성품 라인은 기억하지 않는다") 정면 위배.
- **R6-H2** — **전표 복사**가 동일 오염을 1클릭마다 재생산: `duplicateSlip` 이 전개된 구성품을
  평면 POST 로 재생성(계보 필드 없음) → 서버 일반 단품 분기가 배분가를 수집. 본 문서 BE 구현 절이
  "전표 복사" 를 배선 경로로 명시했음에도 R5 sweep 이 놓친 **계열 sweep 누락**.
- **R6-H3** — 버전이력/collab **복원 4경로**의 스냅샷 `Line` record 에 `setHead`/`parentSetModel`
  필드가 아예 없어 복원 시 계보 전량 소실 → 이후 무수정 저장 1회면 오염 재유입. 계보-in-스냅샷
  부재 자체는 선재(#318 계열)이나 memory 오염 결과는 #809 신규.
- **R6-H4** — R5-H4 의 "전수 감사" 가 실제로는 LineRow 1곳만 교정 — 동일 위반쌍이 **#809 가
  추가한 다른 3표면에 잔존**(`#BFDBFE on #EFF6FF` ≈ 1.31:1 — R5 가 위반으로 지목해 교정한 정확히
  그 쌍 · 강조행 구분선 ≈ 1.01:1 은 R5 교정값보다 낮음). Design·FE 두 차원 독립 확인. (정직:
  중복 단서가 있어 실사용 영향은 제한적이나, R5 스스로 위반으로 규정한 기준이 4표면에 동일 적용돼야
  한다는 일관성 문제.)
- **R6-H5** — R5-M7 의 prod 로그 전달 전제가 **AWS 공식 문서와 상충**: collect_list 와일드카드
  단일 entry 는 "최신 파일만 push" + `{container_id}` 는 지원 변수가 아니라 리터럴 렌더 → 17개
  컨테이너 혼류에서 slip-service 라인이 유실될 수 있고, `treat_missing_data=notBreaching` 이라
  alarm 은 영원히 OK = **M-19 가 막으려던 "무한정 미감지" 그대로 재현**. M-19 완료 조건에
  end-to-end 도달 검사 부재.
- **R6-H6 (라이브 CONFIRMED · 🔵 개발책임자 확인 대기)** — **정상 coedit 모드에서 legacy 견적
  1,926건 전부 저장 불가 데드락**: 오류 문구는 "거래처를 다시 선택해 주세요" 인데
  `PartnerAutocomplete disabled={coeditActive}` 라 지시를 이행할 수 없다. main 도 동일(회귀 아님 —
  소스 대조)이나, #809 가 non-legacy 는 snapshot hydrate 로 고쳐놓고 legacy 만 남겼고 R5-H6 의
  보호는 coedit 실패 fallback 에서만 도달 가능하다.

### MEDIUM 10 · LOW 요지

| ID | 요지 | disposition |
|---|---|---|
| R6-M1 | **R5-M1 Hikari 4s 전역화의 blast radius 불비례** — `connection-timeout` 명시는 fleet 26모듈 중 slip-service 유일 · slip IT(pool max=3·최중량 582 IT)는 4000 상속인데 auth/accounting IT 는 20000 상향 · 진짜 용량 구멍(**무 timeout in-tx RestClient**)은 미해결. R5 진단은 옳았으나 처방이 범위 초과 | 🔵 개발책임자 확인 |
| R6-M2 | `DB_CONNECTION_TIMEOUT_MS` 노브가 실행 경로 어디에도 미배선 — 같은 커밋의 R5-M6("compose 명시 매핑 필수") 자기 표준 위반 + 테스트가 resolved 4000 을 단언해 노브/테스트 상호배타 | FIX |
| R6-M3 | R5-M3 의 mock fix 가 RFC-4122 를 강제하는데 mock 거래처 id 전원이 version-less → mock 모드에서 전 거래처 400 → 조용히 CATALOG 폴백 = **mock 기능 도달성 회귀** | FIX |
| R6-M4 | **R5-H2 의 coedit 회귀 테스트가 가짜** — 주석과 달리 fixture 가 여전히 문자열이라 fix 이전 코드로도 PASS(PM 실물 대조 확인) | FIX |
| R6-M5 | `SlipFormPage` 만 재조회 시 `priceLookupAnnouncement` 미클리어(견적엔 있음) → stale 문구 SR 재낭독 — **또 slip/estimate 비대칭**(R4-F1 이 "구조적 차단" 을 주장한 계열) | FIX |
| R6-M6 | `LineRow.contrast.test.tsx` 가드가 hex 하드코딩이라 토큰 실값 변경 시 영구 green + 단언 쌍이 실제 렌더 쌍과 상이 | FIX |
| **R6-M7** | **본 dev-report 에 R5 라운드 전체 부재**(`810a5efd1..HEAD` dev-reports 커밋 0건) + nightly 문단이 현 diff 와 모순 + spec 상태줄 "R4 대기" 정지 + 화면 계약에 R5 신설 UI 3요소 미기재. **4개 차원 독립 지적 — PM 오케스트레이션 누락(R4 는 문서 배치를 돌렸으나 R5 는 누락)** | FIX — **본 개정이 그 fix** |
| R6-M8 | **BUNDLE_SET parent 기억이 생성 시점 1회뿐** — 수정 경로에서 세트 가격을 바꿔도 기억 미갱신(전개 후 문서에 BUNDLE productId 라인이 없어 갱신 경로 부재) → 재선택 시 구값 자동채움 + '거래처 최근단가' 마커. 라이브 실증(1,100,000 → head 1,300,000 PUT → 기억 1,100,000 유지) · **spec 미기재였음**(본 개정으로 각주 명시) | 🔵 개발책임자 확인 |
| R6-M9 | 라이브 QA 스펙의 전역 카운트 단언이 **공유 스택 동시 사용에 false-RED** — 12a FAIL 원인은 타 차원 에이전트의 동시 PUT → 격리 재실행 PASS 로 교차 오염 확정. **PM 오케스트레이션 결함(라이브 프로브 병렬 실행)** | FIX + PM 규율 개정 |
| R6-M10 | R5-M9 disposition 중 dispatch 1건만 이행 — scheduled nightly 는 main 의 구 필터로 **6연속 실패(07-09~07-14) 중**인데 기록 0 · "머지 전까지 nightly 붉음" 한계 미고지 | FIX |
| LOW | `failedProductIds` 두 호출자 미소비·주석 불일치 / 견적 신규 `role="row"` orphan(axe serious) / R5-L1 잔여 편차 2건(바이트코드 실측·현재 inert) / INBOUND direct PUT resolver 회귀 IT 부재 / 변경행 드래그 시 elevation 소실 / 시나리오 11 의 `1926` 고정 단언·헤더 4필드 미원복 / PR body `연관 Issue: #821` 미기재 | FIX |

### R6 가 반증한 것 (R5 fix 무결 확인 — 요약)

R5-H4 교정의 전역 오염 우려 → 무결(`.lineRow.priceRefreshed` 모듈 스코프 한정·전역 토큰 원값
불변·타 화면 영향 0) · R5-H3 무한 재resolve/레이스/영구 busy → 전부 불성립 · R5-H2 provenance
흐름 전 경로 clean · R5-M2 분리 반환 정확 · R5-H5 견고(CI 실 실행 로그 대조 — JUnit 632 = 599 +
33 정확 일치) · slip CI 3그룹 합 1273 = 로컬 1273·skip 0("잡 green ≠ 실행" 우려 수치 기각) ·
R5-L4(`slip.vendor` 실재 0 → 제거로 커버리지 감소 0) · R5-M6(라이브 컨테이너 env 9종 주입 실측) ·
R5-M7 리소스 자체(filter 문자열이 실코드 log.warn 리터럴과 정확 일치·terraform validate 통과) ·
R5-M8(label 실재·무라벨 선생성 건전) · #821(run 29431175485 success 실조회) · **R5 신설 3시나리오는
fix 를 되돌리면 fail(회귀-살상력 확인 — 단 12 는 R6-H1 범위 밖)** · legacy 무수정 저장 라이브
무결(오판값 1745455 미발생) · VAT basis·V58·BaseEntity/인가·게이트웨이·Micrometer 라이브·다크모드
도달 불가 재확인·`판매가` 잔존 0·UUID 화면 노출 0 전부 clean.

### R6 라이브 증거·잔여물

실캡처 **43장** `docs/qa/809-partner-product-price-memory/r6/`(스위트 34 + 적대 프로브 9) — 기존
캡처 디렉토리 전부 불가침 유지. 스위트 13/14 PASS + 12a 는 교차 오염 false-RED → 격리 재실행
PASS(실질 14/14, FAIL 원인 = R6-M9). BE 차원 실측이 남긴 라이브 잔여물은 리뷰 게시에 PM 조치
대상으로 명시됐다 — 오염 기억행은 QA 차원 12a 격리 재실행의 스펙 내장 reset 이 삭제했고, 전표
`2026/07/16-23`(계보 오귀속 상태)은 잔존한다.

### R6 fix — ✅ 완료 · 게시 [issuecomment-4985345956](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4985345956)

> 이 절은 원래 *"🚧 진행 중 · PM 이 fix 완료 후 이 절을 갱신한다"* 로 남아 있었다. **R8 문서 배치가 그
> 약속을 이행해 갱신한다**(2026-07-16).

- fix = FABLE5 배치(BE·DevOps·QA·문서) 완료 후 게시. 라이브 QA **19/19**(`90a2c6ed9` 기준 · 스샷 41장
  `r6-postfix/`).
- **R6-H6·R6-M1·R6-M8 은 "개발책임자 확인" 으로 이월**됐고 2026-07-16 에 각각 **D-R8-1·D-R8-2·D-R8-3**
  으로 확정됐다. 그중 **D-R8-3 은 같은 날 D-R8-5 로 번복**됐다(아래 R8 절).
- 후속 R7 이 **R6 fix 의 핵심인 2-패스 resolver 를 BLOCKING 으로 붕괴**시켰다 — 아래 R7 절.

## R7 — CODEX SOL 5.6 재수렴 (캐논 5단계) · [issuecomment-4985570977](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4985570977) — 🛑 **2/5 차원에서 의도적 중단**

**BE·FE 2차원 완료 시점에 BLOCKING 이 "이 접근으로는 수렴 불가" 를 증명**해, 개발책임자 판단을 받고
**Design·DevOps·QA 3차원을 의도적으로 미실행**했다. 곧 폐기될 코드를 리뷰하는 비용을 피하기 위함이다.

> ⚠️ **이 중단은 캐논의 "5-agent 단축금지" 예외가 아니다** — 리뷰 대상 코드 자체를 폐기하기로 개발책임자가
> 결정했기 때문이며, 그래서 **R8 이 5차원 full 라운드로 재시작**했다([[feedback_expanded_scope_reinstate_review]]).

### 🔴 R7 이 밝힌 것 — 휴리스틱 접근의 구조적 붕괴

- **R7-BE-1 [BLOCKING]** — `BundleLineageResolver:198` 이 head 를 2패스에서 무조건 제외 → 세트 head
  구성품의 **수량만 수정**해도 fingerprint exact 불일치 → 계보 없는 일반 라인으로 저장 →
  `collectPriceMemory` 가 **배분가를 `LINE_SAVE` 로 각인**. 🔴 **R6 의 "계보 보수적 소실 = 오귀속보다
  안전" 판단이 틀렸다 — 소실 = 각인이라 안전하지 않다.** 게다가 `BundleLineageResolverTest:102-114` 가
  **그 동작을 정상으로 고정**하고 있었다(테스트가 결함을 잠금).
- **R7-BE-2 [HIGH]** — 전역 그리디 tie-break 가 **단품과 구성품을 서로 뒤집는다**. 읽기 전용 probe 로
  재현: 세 후보의 수량거리가 모두 1 → "빈 계보 우선" 탓에 첫 요청 라인이 단품 엔트리를 선점 → 실제
  단품이 거리 3인 세트 엔트리를 승계. **요청 순서를 바꾸면 결과가 달라져 R6 의 "요청 순서 비의존" 주장도
  붕괴.** `Slip.lines` 에 `@OrderBy`/`@OrderColumn` 부재로 `entryOrder` 가 DB 반환 순서 의존.
  → **R7 판정: "거리 정렬 그리디로는 해결할 수 없다."**
- **R7-BE-3 [HIGH·CONFIRMED]** — `SlipSnapshot` 이 `driver`·`unloadDate` 누락 → **R8-BE-5 로 이월·미fix**.
  ⚠️ **게시 수치는 stale — 아래 R8 §실측치 정정 참조.**
- **R7-FE-1 [HIGH]** — FE 가 계보 소실·오염을 **경고 없이 확정**: 편집 state/request 가 `id`·`setHead`·
  `parentSetModel` 을 전부 버리고 전량 PUT. 견적은 **응답 DTO 부터** 두 필드가 없어 회피 불가.
- **R7-FE MEDIUM 3 · LOW 2** — duplicate mock 권한 미검사 · duplicate mock/test 계보 미검증 · 거래처
  해제 시 stale 안내 재낭독 · **R6-M4 "양방향 실증" 이 fixture 계층 차이였음**(PM 자기 정정) · mock UUID
  regex 가 GET 실 wire 보다 엄격(`1-1-1-1-1` → 실 API **204** / mock **400**). → **전부 R8 로 이월**.

### ✅ R7 이 반증한 것 (clean)

comparator 가 `lineIndex+entryOrder` 까지 포함해 **Java sort 의 stable/unstable 은 결과 불변** · 라인 상한
100 이라 후보 10,000 = 성능 결함 근거 없음 · 전표 값 덮어쓰기/견적 팩토리 복원 모두 캡처값 결정적 재현 ·
구 JSONB nullable + `FAIL_ON_UNKNOWN_PROPERTIES=false` 하위호환 · **복사 endpoint 의 권한·404 우선·cutoff·
`sourceOrderLineId` 미승계·계보 복사·비구성품만 기억 전부 정합** · 채번 `PESSIMISTIC_WRITE` 직렬화 ·
afterCommit/executor · V58 BaseEntity 7 audit · FE 403/404/409 문구가 BE 계약과 일치 · `aria-describedby`
가 실 `<input>` 도달 · `판매가` 정렬 · UUID 화면 노출 0.

### 📌 R7 의 귀결 — lineId 왕복 계약 도입 (개발책임자 결정)

PM 이 4개 선택지(세트 범위 제외 / 임시 안전장치 / **lineId 계약** / 현행 머지)를 제시했고 **lineId 왕복
계약**으로 확정. 근본원인 = *"update 계약이 라인 안정 ID 없이 전 라인을 통째 교체 → 서버가 신규/수정
라인을 구분 불가"*. 구현 = CODEX LUNA 5.6 · 커밋 `34f978ec9`. **범위 점증 → 리뷰 재가동.**

R7 반례가 테스트 이름에 잠겼다: `modifiedSetHead_quantityOnly_stillPreservesLineageByLineId` ·
`sameProductComponentAndPlainLine_keepTheirOwnLineageRegardlessOfRequestOrder` ·
`swappingRequestOrder_doesNotChangeLineageAssignment` · IT
`lineIdFromAnotherSlip_isRejectedAsBadRequestBeforeReplacement`.

## R8 — OPUS 4.8 1차 적대검증 (캐논 4단계) · `6ae5ccde9` · [issuecomment-4987613082](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4987613082) — 🔴 **0수렴 실패**

> ⚙️ **2026-07-16 개발책임자 워크플로우 변경**: 1차 적대검증 모델이 **FABLE5 → OPUS 4.8** 로 교체됐다
> (FABLE5 토큰 소모량 극심 → 폐지 · 커밋 `178e3a113`). 2차 적대검증 = CODEX SOL 5.6 은 유지. 구 기록의
> "FABLE5 라운드" 는 현 1차 적대검증 라운드로 읽는다.

**고유 28건 (BLOCKING 2 · HIGH 9 · MEDIUM 8 · LOW 9 — 중복 병합 후).** 5차원 전부 실행(Design N/A 없음)
+ 라이브 QA. **머지 불가 판정.**

### 🔑 R8 의 핵심 결론 — **"lineId 계약이 근본원인을 제거했다"는 주장은 반증됐다**

직전 세션은 근본원인이 끊겼다고 판단하고 **"예상 2라운드"** 를 전망했다. R8 은 그 주장을 1순위 적대
대상으로 삼았고 **BE·FE·QA 3개 차원이 독립적으로 반증**했다.

**공정하게 성립하는 부분**: `BundleLineageResolver` 의 **"휴리스틱 잔존 0" 주장은 참이다.** BE 차원이
`Comparator`/`distance`/`fingerprint`/`tie`/`Fallback` 계열을 전수 grep 해 반증에 **실패**했다. 계보
결정은 `Map<lineId, lineage>` 직접 조회뿐이다.

🔴 **그러나 정확성 부담이 사라진 게 아니라 서버 → FE 로 이전됐을 뿐이다.**

```
구 설계: 계보를 버림 → 휴리스틱으로 되찾으려다 반례에 붕괴 (R5·R6·R7)
신 설계: 계보를 lineId 로 왕복 → 서버는 lineId 를 무조건 신뢰
         → FE 가 lineId 를 흘리거나(R8-QA-2) 미전송하거나(R8-QA-1)
           엉뚱한 품목에 붙이면(R8-QA-6) 서버가 그대로 각인
         → 방어 0 = 더 조용히 깨짐
```

`BundleLineageResolver` 는 입력을 전면 신뢰하는 **순수 함수**이고, **그 전제를 강제하는 주체가 아무도
없다.** 폴백을 의도적으로 없앤 탓에 클라이언트 결함이 휴리스틱 오작동이 아니라 **조용한 데이터 파괴**로
귀결된다.

### 🔴 BLOCKING 2

| ID | 요약 | 실패 시나리오(라이브 실측) |
|---|---|---|
| **R8-QA-1** 🆕 | **lineId 미전송 PUT 이 세트 계보를 전량 파괴** + 구성품 배분가 각인. **QA 가 독자 포착 — 정적 4차원 전부 놓침** | 세트 전표 생성 → **아무것도 수정하지 않고** 왕복 PUT(lineId 없음) → **200** → `GZN:t:GZS｜DCX:f:GZS` → **`GZN:f:-｜DCX:f:-`**. 기억행에 구성품 `501600`·`752400` 이 `LINE_SAVE` 로 각인 |
| **R8-FE-1** = **R8-QA-2** | coedit 원격 라인삭제 → lineId **위치 밀림** → 계보 오귀속 + 사용자 단가 증발. **전표엔 견적의 `lineStructureLocked` seed-lock 이 없음** | 2창 coedit → A 가 1행(head) 삭제 → B 저장 **200** → 단품 `AC032CN1DBC1` 이 `parent=AF17B6474GZS` 로 각인, `DCX` 가 `set_head=true` 탈취, 사용자 입력 **299000 증발**. PUT body 실증: `lineId f427db74`(head) ↔ `productId c9c200ad`(DCX). **2/2 결정적 재현** |

**서버 방어 2종이 모두 미발화함을 QA 가 확인**: `validateLineIds` 는 밀린 lineId 도 "소유+중복없음" 이라
통과 · `verifyVersion` 낙관적잠금은 원격삭제가 **Y.Doc 전용(서버 미호출)** 이라 `modifiedAt` 불변 →
409 없음.

### 🟠 HIGH 9

| ID | 요약 | 확신도 |
|---|---|---|
| **R8-BE-1**=**R8-QA-6** | **lineId 승계에 productId 동일성 검증 없음** → 품목 교체 행이 남의 계보를 상속. 라이브: head 라인을 무관 단품 `ACD-2558G` 로 교체 + 단가 150000 → **200** → `ACD-2558G:t:AF17B6474GZS`, 기억행 **NONE** | CONFIRMED |
| **R8-BE-2** | 🔴 **D-R8-3 의 전제가 거짓** — `parentSetModel`=modelCode 인데 `price_memory.product_id`=NOT NULL UUID · `ProductClient` 에 modelCode→productId 역조회 없음 · `expand` 6:4 재배분으로 **일관된 "세트 단가" 부재** → **D-R8-5 번복의 근거** | CONFIRMED |
| **R8-BE-3**=**R8-QA-3** | **전표 수정 시 거래처 변경이 partnerId 에 미반영** → 기억이 **원 거래처**에 귀속·오염. **견적은 정상 = 또 slip/estimate 비대칭** | CONFIRMED |
| **R8-BE-4** | **D-R8-2 설계 함정 3종** — DataSource 만 분리하면 ①`set_config(is_local)` 무력화 ②autoconfig back-off 로 기동 실패 ③stale 주석 3곳. 현 테스트는 `verify(applyTransactionTimeouts)` mock 확인이라 **false-green 유지** | CONFIRMED |
| **R8-DEVOPS-2** | **D-R8-2 진단 확인** — `connection-timeout: 4000` 이 slip-service **단일 전역** DataSource 에 적용 → pool 포화 시 21번째 요청이 `SQLTransientConnectionException` → `handleUnknown` → **HTTP 500**. **"fleet 26모듈 중 유일" 실측 확인**. 격리 사이징 안전: PG `max_connections=300`, 현재 141, 전용 4 추가 시 ≈154 | CONFIRMED |
| **R8-BE-5** | **R7-BE-3 재확인** — `SlipSnapshot` 이 `driver`·`unloadDate` 누락. `SlipService:403` 이 *"toSnapshot 필드"* 라 **명시**하는데 record 에 없음 | CONFIRMED (⚠️ 수치 정정 — 아래) |
| **R8-FE-2** | **lineId 왕복 계약의 FE 테스트 0건** — PR 간판 계약이 FE 에서 완전 무검증. `34f978ec9` 의 FE diff = **18줄, 테스트 0** → R8-FE-1 이 749 vitest 를 그대로 통과 | CONFIRMED |
| **R8-DESIGN-1** | 견적 폼에 **거래처 입력 경로가 2개**인데 권위 있는 쪽만 비활성 — `PartnerAutocomplete disabled={coeditActive}`(`:1133`) vs `거래처명` 자유입력 **미잠금**(`:1153`) → 마커가 거짓말. `SlipFormPage:956-957` 이 동일 결함을 **"P0 D-AC3-01"** 로 이미 제거한 선례 존재 | CONFIRMED(정적) |
| **R8-QA-7** | 🔴 **라이브 QA 스위트 전면 붕괴** — 직전 "19/19" 재현 불가. **0 passed / 10 failed / 9 미실행**. 원인 = 시드 품목 소멸(`products.product_code` **1116행 전부 NULL**). **코드 회귀 아님** | CONFIRMED |

### 🟡 MEDIUM 8 · 🔵 LOW 9 (요약)

**MEDIUM** — `R8-BE-6` recency guard 로 전량 skip 돼도 `success_total` 증가(다중 인스턴스 clock skew 시
조용한 유실이 100% 성공으로 보임) · `R8-BE-7`(=`R8-DEVOPS-3`) executor drain ↔ HikariDataSource close
순서 무제약 · `R8-FE-3` 안내가 guard 밖 발화 → aria-live 거짓 고지(**견적엔 guard 있음 = 또 비대칭**) ·
`R8-FE-4`(R7-FE-1) duplicate mock 권한 미검사 — 근거 주석이 **범주 오류**(403 은 시간 비의존인데 409 와
묶어 "시간 의존" 이라 기재) · `R8-FE-5`(R7-FE-2) duplicate mock 계보 미검증 + `__mockStatus:201` 미반영 ·
`R8-FE-6`(=`R8-DESIGN-2`·R7-FE-3) 거래처 해제 시 stale 안내 재낭독 · **`R8-DEVOPS-1` prometheus rule
미마운트** → `GET :9090/api/v1/rules` = `{"groups":[]}` = 경보 **런타임 부재**(R1~R7 전 라운드 미검증) ·
`R8-QA-8` **BE 테스트가 데이터 손실을 '정상'으로 못박음**

**LOW** — `R8-FE-7`(R7-FE-4) mock UUID regex GET/POST 분리 필요 · `R8-FE-8` `setHead`/`parentSetModel`
노출했으나 **소비자 0**(비대칭 해소가 DTO 표면에만) · `R8-FE-9` `seedEstimateCoeditProvider` 가 lineId
누락 = **fix 지뢰** · `R8-FE-10` D-R8-1 "1,926건" 재현 불가 · `R8-DESIGN-3` LineRow JSDoc 이 폐기된
`'최근가'` 문서화(레포 **유일 잔존 1건**) · `R8-DESIGN-4` 대비 가드가 **렌더되지 않는 쌍** 단언 ·
`R8-DESIGN-5` 저장일이 `title`/`aria-label` 에만(터치·키보드 미도달) · `R8-DESIGN-6` R6-L2 fix 주석의
a11y 거짓 주장 · `R8-DESIGN-7` `role=row` orphan(**main 선재 = 범위 외**)

### 📌 개발책임자 결정 D-R8-5~8 — [issuecomment-4987642891](https://github.com/ewoo14/Samhan-Public/pull/820#issuecomment-4987642891)

R8 리뷰가 낸 **확인요청 4건 전부 확정 · 4건 모두 PM 권고안 수렴.**

| ID | 쟁점 | 확정 |
|---|---|---|
| **D-R8-5** | D-R8-3 **재결정** | ✅ **설계 귀결로 재분류 — 결함 아님.** spec 명시 후 close |
| **D-R8-6** | R8-QA-1 [BLOCKING] lineId 미전송 PUT | ✅ **400 거부** (판정 기준은 **D-R8-9** 가 이전) |
| **D-R8-7** | R8-QA-3 [HIGH] 전표 거래처 자유입력 | ✅ **자유입력 봉쇄 + `PartnerAutocomplete` 통일** (+ 계약에 `partnerId` 추가) |
| **D-R8-8** | 차원 간 권고 충돌 | ✅ **"세트 구성품의 품목을 교체하면 그 세트의 구성품이 아니다"** → **BE productId 검증 + FE Y.Doc 직독 둘 다** |
| **D-R8-9** | D-R8-6 구현 중 BE 배치가 고지한 **오탐 1종** | ✅ **요청 레벨 계약 마커(`lineIdContract`) 도입** — 판정 = "lineId 개수" → **"마커 유무"**. 마커 부재 → 400 / 마커 존재 → **lineId 0개(전 라인 교체)도 허용**. 근거: **구버전 desktop 사실상 없음(전원 최신본)** → 즉시 필수화 |

🔵 **D-R8-9 는 D-R8-6 을 대체하지 않는다** — 거부(400) 자체는 유지되고 **판정 기준만** 옮겨 오탐을
제거한다. 파괴 경로(R8-QA-1)는 여전히 차단된다: 구 클라이언트는 마커를 보내지 않으므로 라인을 한 줄도
건드리기 전에 거부된다.

> 📌 **이 결정도 리뷰가 제 역할을 한 사례다.** D-R8-6 을 구현하던 BE 배치가 *"계보 보유 전표에서 전
> 라인을 새 라인으로 교체하는 정상 저장이 400 이 된다"* 는 오탐을 **스스로 정직 고지**하고 PM 판단을
> 요청했다. 그리고 *"구버전 desktop 이 실제로 서버를 치는가"* 라는 확인 한 번이 **"구 클라이언트
> 호환" 이라는 요구사항 자체가 가상이었음**을 드러내 설계를 바꿨다.

🔴 **D-R8-5 는 같은 날 오전 확정(D-R8-3)의 번복이다** — 이력 보존을 위해 명시한다.

| | 오전 (D-R8-3) | 현재 (**D-R8-5**) |
|---|---|---|
| 판정 | **결함으로 처리 · 이 PR 에서 fix** | **설계 귀결로 재분류 · 결함 아님** |
| 근거 | *"lineId 계약 도입으로 수정 경로 갱신이 기술적으로 가능해짐"* | 위 전제가 **R8-BE-2 로 코드 반증됨** |

> 📌 **이 번복은 리뷰가 제 역할을 한 사례로 기록한다.** 오전 확정은 직전 세션이 보고한 전제를 신뢰한
> 판단이었고, R8 BE 차원이 그 전제를 코드로 반증했다. 캐논의 *"물음은 '결과가 맞나'가 아니라 '모든
> 단계를 밟았나'"* 가 작동했다.

계약 상세는 spec `docs/specs/809-slip-estimate-recent-manual-price-spec.md` **§R8 확정 계약**(D-R8-6/7/8/9)
및 **§BUNDLE 정책 → `BUNDLE_SET` 기억의 정의**(D-R8-5) 에 명시했다.

### 🔴 lineId 계약의 실제 상태 — **근본원인이 제거된 게 아니라 FE 로 이전됐다**

정직하게 기록한다. 직전 세션의 보고와 실제 상태의 차이는 다음과 같다.

| 직전 세션의 보고 | R8 이 확인한 실제 |
|---|---|
| "휴리스틱 잔존 0" | ✅ **참** — BE 차원이 전수 grep 으로 반증 실패 |
| "근본원인 제거" | 🔴 **거짓** — 부담이 **서버 → FE 로 이전**. FE 는 그 부담을 지지 못함(R8-FE-1/2) |
| "폴백 없음 = 같은 결함 재발 방지" | 🔴 **역효과** — 서버 방어 0 → 클라 결함이 **조용한 파괴**로 귀결(R8-QA-1) |
| "예상 2라운드" | 🔴 **철회** — R8 이 BLOCKING 2건을 새로 냄(1건은 정적 4차원이 전부 놓치고 라이브만 포착) |

**D-R8-6(400 거부) + D-R8-8(BE productId 검증)이 서버 방어를 복원**하고, **D-R8-8 FE(Y.Doc 직독)가
근본 fix** 를 담당한다. 즉 계약 자체는 유지하되 **"입력을 전면 신뢰" 전제를 서버가 강제**하도록 바꾼다.
**D-R8-9(계약 마커)가 그 강제의 판정 기준**이다 — 서버는 라인을 세는 대신 클라이언트의 <b>자기
선언</b>을 요구하며, 선언 없는 쓰기는 계보 유무와 무관하게 전부 거부한다.

### 🔴 R7 실측치 정정 (stale 수치 — 은폐 금지)

**DB 가 재시드돼 R7 이 게시한 실측치가 stale 하다.** R7 수치를 그대로 인용하면 **허위 실측**이 되므로
정정한다. **구조적 결함 판정은 CONFIRMED 유지** — 바뀐 것은 라이브 영향 규모뿐이다.

| 항목 | R7 게시(stale) | **현재 실측** | 판정 |
|---|---|---|---|
| 활성 revision 중 `driver`·`unloadDate` 키 보유 | **2,028건** 전부 0건 | **56건** 전부 **0건** (R8 리뷰 시점 21건 → QA 쓰기 23건 반영) | 🔴 **구조 결함 CONFIRMED 유지** — 키 부재는 불변 |
| `unload_date` 보유 활성 전표 | **7건** | **0건** (전체 44건 중 0) | ⚠️ **라이브 영향 = 잠재**(현 데이터로 도달 불가) |
| legacy 견적(`partner_id NULL`) | **1,926건** | **0건** (`estimates` 총 **3건** 전부 partner_id 보유) | ⚠️ D-R8-1 을 "legacy 전용" 으로 좁히면 **QA 검증 불가** |
| `estimate_lines` | **1,927건** | **3건** | — |

- 🔬 **본 정정의 실측 근거**(R8 문서 배치가 독립 재확인 · 2026-07-16 · 읽기 전용 SELECT):
  `SELECT count(*), count(*) FILTER (WHERE snapshot ? 'driver'), count(*) FILTER (WHERE snapshot ?
  'unloadDate') FROM slip_revisions WHERE deleted_at IS NULL;` → **`56|0|0`**.
  snapshot 최상위 키 전수 = `customerRepresentative, customerAddress, partnerCode, lines, partnerName,
  slipNo, customerTel, businessNumber, slipDate, partnerId` — **`driver`·`unloadDate` 부재 확인.**
- 🔴 **과장이 있었다는 사실 자체를 이력으로 보존한다** — 위 R4-F2("1927/1927 = 100% legacy") 과장 정정과
  **같은 계열의 반복**이다. 라이브 수치는 재시드로 휘발되므로, **수치를 결함 근거의 본체로 삼지 말고
  구조(코드/스키마)를 근거로 삼아야 한다**는 것이 이 반복이 주는 교훈이다.
- ⚠️ **revision 건수는 휘발성**이다 — QA 라운드가 전표를 쓰면 증가한다(R8 리뷰 21건 → 본 정정 시점
  56건). **고정 수치를 단언하는 테스트/문서를 만들지 말 것**(R6 LOW 의 `1926` 고정 단언과 동일 함정).

### 🔴 R8 정직 고지 (리뷰가 스스로 밝힌 한계)

1. **직전 "라이브 QA 19/19" 는 R8 에 재현되지 않았다** — 0 passed / 10 failed / 9 미실행. 코드 회귀가
   아니라 **시드 품목 소멸**(R8-QA-7)이다. 다만 그 결과 **기존 9건의 계약은 R8 에 라이브 검증되지 않았다.**
2. **D-R8-1 의 "legacy 1,926건" 재현 불가** → **R8-DESIGN-1 경로(정상 견적 + coedit)로 fix·QA 를 잡아야
   한다**(현 픽스처 3건으로 재현 가능). FE·Design 차원이 독립적으로 같은 결론에 도달.
3. **견적 GUI 라이브 재현 미시도** — R8-BE-1·R8-DESIGN-1 은 전표 경로 실증 + 코드 확인으로 갈음
   (`EstimateService:502` → 동일 `restoreEstimateLines`→`assign` 호출 확인).
4. **D-R4-4(거래처 해제) 라이브 미커버** — 해제 어포던스 부재(기존과 동일 사유).
5. ✅ **CI allowlist false-green 의혹은 완전 반증됐다** — 레포 concrete 테스트 **185 전부 실행 확인**
   (exact SHA `178e3a113` 실 CI 로그). **이 PR 에 false-green 없음.** 과거 라운드의 우려는 **해소로 종결**.
6. **Micrometer `_total_total` 중복 미발생**(라이브 export 명 실측) · **terraform 3파일도 범위 외 혼입
   아님**(prod 엔 Prometheus 컨테이너가 없어 CloudWatch 등가물).
7. **다크모드 대비 = 도달 불가로 종결** — 미확인 대비 1건은 다크모드 전용 파탄(`1.002:1`)이었으나
   desktop 에 `data-theme`·`prefers-color-scheme` **0매치** = 활성화 경로 없음 → **#809 결함 아님**
   (main 선재 토큰 갭). Codex 의 AA 2건(**7.149 / 15.083**)은 실토큰 파싱 재계산으로 정확함 재확인.
8. **QA 가 라이브에 쓴 것**: 전표 23건 생성(OUTBOUND DRAFT), 기억행 4건. 원 픽스처 보존(COMPLETED 2 + 원
   DRAFT 19 + 견적 3 무변경) · `docker compose down`·DB 재생성 **안 함** · 고아 vite 0 · 브랜치/SHA 무변경.

### R8 라이브 증거

실캡처 **19장** `docs/qa/809-partner-product-price-memory/r8/` — 신규 스펙
`price-memory-r8-adversarial-real-qa.spec.ts` **2 passed / 4 failed**(4 failed = **결함 4건 재현 = 의도된
RED 가드**). 🚫 **기존 스펙은 한 줄도 수정하지 않았다**(단언 약화 0 · R3 가 잡은 false-green 부활 금지).

✅ **R3 fix UI 실물 건재 재확인**(매 라운드 항목 · 반증 실패 = clean): hit 마커 `거래처 최근단가` 913000 ·
miss 마커 `판매가` · 거래처 변경 배너 + `단가 변경` 강조 **정확히 1행** · `POST /slips/price-memory/bulk`
**정확히 1건**(단건 GET 0건 = D-R3-4 준수) · 구 `정가` **0건**(D-R4-1 준수).

### R8-DEVOPS-1 fix — prometheus rule 미마운트 복구 ✅ (문서/DevOps 배치 완료분)

🔴 **경보가 런타임에 존재하지 않았다** — `SlipPriceMemoryUpsertFailure` 는 가격기억 fail-soft 유실의
**유일한 dev 측 탐지기**인데 이 스택에서 **실제로 부재**했다. runbook 의 전제가 거짓이었던 셈이다.

**원인 = "마운트가 조용히 없는" 상태**:

| 사실 | 실측 |
|---|---|
| prometheus 컨테이너 생성 시각 | `2026-07-02T03:28:20Z` |
| `./prometheus/rules` 마운트 추가 커밋 | `77ea69c77` @ **2026-07-15** (**13일 후행**) |
| `docker inspect` Mounts | `prometheus.yml` + `prometheus_data` **뿐** — rules 바인드 **부재** |
| `docker exec ls /etc/prometheus/rules/` | `No such file or directory` |
| `curl :9090/api/v1/rules` | **`{"groups":[]}`** |

`prometheus.yml` 의 `rule_files` 는 **bind-mount 된 파일**이라 restart 만으로 반영되지만,
`docker-compose.yml:166` 의 `./prometheus/rules:/etc/prometheus/rules:ro` 는 **컨테이너 생성 시점**
마운트다. 🔴 **결정적으로 — Prometheus 는 `rule_files` glob 이 0개 매치해도 오류를 내지 않는다.**
그래서 로그·헬스체크·기동 어디에도 신호가 없고, **rule 파일은 git 에 멀쩡히 존재하며 promtool 도 통과**해
정적 리뷰로는 절대 잡히지 않는다. **R1~R7 전 라운드가 이걸 검증하지 않은 이유**다.

**복구** (restart 로는 안 고쳐진다 — `--force-recreate` 필수):

```
docker compose -p infrastructure --project-directory <repo>/infrastructure \
  -f docker-compose.yml -f docker-compose.local-all.yml \
  up -d --force-recreate --no-deps prometheus
```

**복구 실증** — `curl -s http://localhost:9090/api/v1/rules`:

| | 출력 |
|---|---|
| **before** | `{"status":"success","data":{"groups":[]}}` |
| **after** | `groups[0].name=slip-price-memory` · `rules[0].name=`**`SlipPriceMemoryUpsertFailure`** · `state=inactive` · **`health=ok`** · `file=/etc/prometheus/rules/slip-price-memory.yml` |

end-to-end 배선까지 확인했다(rule 로드만으로는 불충분 — selector 가 실 job 에 붙어야 한다):

- `promtool check rules` → **`SUCCESS: 1 rules found`**
- scrape target `slip-service` = **`up`**
- `slip_price_memory_upsert_failed_total{job="slip-service"}` **TSDB 실재**(현재값 `0` = 실패 0건 = 정상)
- 즉 rule 의 `job="slip-service"` selector 가 **실 job label 과 일치** → 경보가 실제로 발화 가능

**🔴 재발 방지 가드 3종** (다음 라운드가 같은 걸 또 놓치지 않도록):

1. **`infrastructure/scripts/verify-prometheus-rules.ps1`** 신설 — **git 의 rule 파일 목록 ↔ 런타임
   `/api/v1/rules` 로드 목록을 대조**해 drift 를 exit code 로 실패시킨다. #809 rule 만이 아니라 **앞으로
   추가되는 모든 rule 파일**에 자동 적용된다(이 결함의 일반형을 막는다). rule health 와 promtool 문법도 함께 검사.
2. **`docs/runbooks/slip-price-memory-upsert-failure.md` §0차 확인** 신설 — 이 runbook 의 **전제(경보가
   존재한다) 자체를 먼저 검증**하도록 했다. *"경보가 안 울렸다 = 정상"* 이 아니라 *"경보가 아예 없었다"* 를
   1차 확인 전에 배제한다.
3. **`infrastructure/README.md` §Alerting rules** 신설 — 트랩(생성 시점 마운트 · 빈 glob 무오류)과
   `--force-recreate` 요구를 문서화. `rules/` 를 File layout 에도 반영.

> 📌 **교훈**: 이 결함은 **코드·설정·git 어디에도 결함이 없는데 런타임에만 존재**했다. 정적 리뷰
> 4차원이 전부 통과시킨 이유이며, R8-QA-1(정적 4차원이 놓치고 라이브만 포착)과 **같은 계열**이다.
> [[feedback_qa_docker_real_test]] 의 *"실서버 테스트, code read PASS 금지"* 가 인프라 자산(경보·대시보드·
> 마운트)에도 적용된다는 뜻이다.

### R8 fix — ✅ **완주** (R8-QA 신규 4건 + 라이브 잔여 3건 수렴 · 2026-07-16 PM 갱신)

- fix = **OPUS 4.8**(캐논: fix = 그 라운드 진행 모델). 배치: **BE**(계약 변경 선행) → **DevOps/문서**
  (병렬) → **FE**(BE 계약 수신 후) → **QA**. 본 dev-report/spec 개정이 그중 **문서 배치**다.
- **본 배치가 완료한 것**: D-R8-5 spec 명시·close · D-R8-6/7/8 계약 spec 명시 · lineId 왕복 계약 spec
  신설(미기재였음) · R7·R8 절 신설 · R7 stale 수치 정정 · **R8-DEVOPS-1 복구 + 재발 방지 가드 3종**.
- **✅ R8 fix 1차 완주**(회사PC · 28건 전건 disposition · `e8f558cd4`) → **라이브 QA 가 신규 4건
  (R8-QA-9/11/12/13) 포착**. **R8 fix 2차 완주**(집PC 2026-07-16 · 아래 상세) — 신규 4건 + 그 fix 가
  낳은 라이브 잔여 3건(VAT 드리프트·miss 각인·세트 구성품 재가격)까지 **국소 수렴**. 이후 **R9 =
  CODEX SOL 5.6 5차원**(`gpt-5.6-sol` · 종합검증 1회) → 양측 0수렴 → PM 종합 10-게이트 → CI green → 머지.

### R8-QA-13 / D-R8-13 fix — 마커 자기신고를 라인 내용과 대조 (OPUS 4.8 BE, R8 fix 2차)

- **결함(라이브 실증)**: 요청 레벨 `lineIdContract` 마커는 클라이언트 **자기신고**라 서버가 내용과
  대조하지 않았다. 스테일/악성 클라이언트가 마커 `true` 를 실으면서 **계보 보유(BUNDLE_SET)** 전표/
  견적에서 lineId 를 한 개도 안 실으면, 서버가 전 라인 교체를 수행 → **200** → 세트 계보 전량 파괴
  (`parent→NULL`·`set_head→false`). D-R8-9 가 이 경로를 "오탐 제거" 명목으로 허용해 R8-QA-1 을
  **마커라는 다른 문**으로 재개방했다. D-R8-13 이 그 부분만 반전한다.
- **fix**: 마커를 내용과 대조한다. **계보 보유 문서 ∧ 요청 non-null lineId 0개** 일 때만 400
  거부(`INVALID_INPUT`). 오탐 방지 — 계보 없는 평면 문서 + lineId 0개 = 허용, 계보 보유 + lineId ≥1개
  = 허용. 오직 (계보 ∧ lineId 전무) 만 거부. 거부 메시지는 마커 거부(`REJECTION_MESSAGE`, "앱을
  업데이트")와 **다른** 조치("세트 구성품" + **화면 새로고침**)를 안내한다 —
  거부되는 주체가 구버전 앱이 아니라 화면이 스테일한 최신 앱이기 때문이다.
- **변경 파일**(services/slip-service 만):
  - `service/BundleLineageResolver.java` — `hasBundleLineage()` 추가(캡처 계보 중 하나라도 세트
    구성품이면 true).
  - `service/LineIdContractGate.java` — `LINEAGE_REJECTION_MESSAGE` + `requireLineIdsForLineage(
    boolean documentHasLineage, int requestedLineIdCount)` 추가.
  - `service/SlipUpdateService.java`(매입)·`service/SalesSlipUpdateService.java`(매출)·
    `estimate/service/EstimateService.java`(견적) — 3 미러 모두 **기존 라인 제거/교체 이전에**
    `requireLineIdsForLineage(bundleLineage.hasBundleLineage(), 요청 non-null lineId 개수)` 배선.
- **검증(genuine, 이 환경 실측)**:
  - `LineIdContractGateTest` 12/12 · `SlipUpdateLineIdContractTest` 12/12 ·
    `EstimateUpdateLineIdContractTest` 6/6 — 0 failures/errors/skipped
    (`--rerun-tasks --no-build-cache`).
  - `PartnerProductPriceMemoryIT` **29/29** 통과(실 PostgreSQL Testcontainers) — 개정된
    `bundleSlipFullLineReplacementWithMarker_butNoLineIds_isRejectedToPreventLineageDestruction`
    포함, 인접 마커 게이트 IT(round-trip 거부·견적 마커 거부) 무회귀 확인.
  - 회귀 sweep: 전표/견적 update 를 부르는 타 테스트(`SlipUpdateIT`·`SlipSalesUpdateIT`·
    `EstimateControllerIT` bundle CREATE·`RevisionRestoreVatAuthorityIT`·`EstimateServicePriceMemoryTest`)
    는 평면 문서이거나 CREATE 경로라 D-R8-13(계보∧lineId전무) 조건에 도달하지 않음 — 무영향.

### R8-QA-9/12 + mock 회귀 게이트 fix (OPUS 4.8 FE, R8 fix 2차)

- **R8-QA-9 [HIGH]** 전표 수정 진입 시 거래처 빈칸 — `AsyncAutocomplete` 가 focus 로 열린 뒤 `disabled`
  로 플립되면 React 가 disabled 요소에 onBlur 를 안 쏴 `open` 고착 → 선택값 표시 소실. **fix**:
  `disabled` 전이 감지 `useEffect`(blur 타이머 정리 + `setOpen(false)`). 단위테스트 신설.
- **R8-QA-12 [MEDIUM]** coedit 중 행삭제 잠금(`slipCoeditActive`)이 (1)수정 모달 행삭제를 영구 불가로
  (2)R8-QA-2 근본 fix 라이브 검증을 봉쇄 → **D-R8-11: 잠금 제거 + Y.Doc lineId 직독으로만 방어**.
  서버측 삭제 경합은 저장 400 → **충돌 배너("최신 내용 불러오기")** 로 처리(막다른 "입력값 확인" 대체).
- **mock 회귀 hard gate 2건** — R8 fix 1차의 D-R8-7(거래처→`PartnerAutocomplete`·CRDT 헤더 편입)이
  coedit 인라인 폼을 회귀시킴: `slip-coedit-field-header-partnerName` testid 소실 + 원격 memo 미전파.
  **근본 fix**(테스트 약화 아님): `PartnerAutocomplete` 에 `inputTestId` prop + 재시드 게이트를
  `isEmpty`(full-seed) / `stale`(lineId 셀만 in-place 복구, 헤더·원격편집 보존)로 분리. spec 은 D-R8-7 이
  의도 제거한 자유입력 hold 단언만 coedit-bound 표시값 단언으로 이동(코어 커버리지 보존).

### R8-QA-11 fix — 거래처 변경 재조회 공용 이식 (D-R8-10) + 라이브 잔여 3건 국소 수렴 (OPUS 4.8 FE)

- **원 결함(R8-QA-11 HIGH)**: 수정 모달에서 거래처만 바꿔 저장 → 옛 거래처 협상단가가 새 거래처에
  각인(모달에 재조회 부재). **D-R8-10 fix**: `SlipFormPage` 의 재조회·배너·강조를 공용 훅
  `usePartnerPriceRefresh` 로 추출해 수정 모달에 이식(복붙 0).
- **라이브 QA 가 그 fix 의 잔여 3건을 순차 포착 → 전부 국소 fix**(전부 모달 재조회 지점 마감 미스 =
  확산 아님):
  1. **VAT 드리프트 [MED]** — 모달 필드는 VAT제외인데 재조회가 VAT포함 기억값을 직기입 → 저장 시 BE
     ×1.1 → 매 변경 ~10% 팽창. **fix**: `vatPrice.ts`(÷1.1 원단위 HALF_UP = BE `createFromVatInclusive`
     미러)로 필드 도메인 변환 → 수렴 고정점(500,000→필드454,545→기억499,999.50).
  2. **miss 각인 [HIGH · 원 결함의 miss 케이스]** — 새 거래처 기억 없으면 모달이 옛 값 유지 → 각인.
     **fix**: 기존 BE `POST /products/lookup` 으로 카탈로그 판매가 조회 → miss fallback(폼 패리티) →
     miss 시 카탈로그가로 전환·각인 차단.
  3. **세트 구성품 재가격 [MED]** — 재조회가 세트 구성품 라인까지 카탈로그 fallback 적용 → 배분가
     −9.09% 변형. **fix**: `bundleComponentLineIds`(BE `isBundleComponent` 미러)로 구성품을 후보에서
     제외. 부수로 in-flight 편집 race 가드도 처리.

### R8 fix 2차 검증 실측 (PM 독립)

- **BE genuine** — slip-service **1,350 passed / 0 fail / 0 error / 0 skip**(189 클래스 ·
  `--rerun-tasks --no-build-cache` foreground · 실 PG IT 포함).
- **FE** — desktop `typecheck` exit 0 · vitest 전 green(design-system 12파일 · desktop 108파일 · 신규
  `vatPrice`/`usePartnerPriceRefresh` 포함) · mock 회귀 게이트 대상 2 + 회귀 sweep green.
- **배포 실증** — slip-service fresh jar(`LineIdContractGate.class` 포함) `--force-recreate` 재배포 ·
  healthy · actuator UP.
- **라이브 QA(Docker 실서버 · mock OFF · `dev_manager`)** — `price-memory-r8-adversarial-real-qa`
  **13/13**(R8-QA-1·2a·2b·3·4·5·6·9·10·11-HIT·11-MISS·13 + 신설 14 구성품 가드) ·
  `price-memory-r2-live-real-qa` **19/19** · 실캡처 `r8-postfix2/` 82장. 핵심 실측: 11-HIT 필드
  454,545·B기억 499,999.50·A 1,004,300 불변 / 11-MISS 카탈로그 1,440,000→필드 1,309,091·B←카탈로그
  (옛 A 854,700 미각인) / 신설 14 구성품 88,000·55,000 불변·순세트 bulk 0.

### 📌 개발책임자 결정 (2026-07-16 · R8 페이싱 바운드)

R8 한 슬라이스를 하루 종일 iterate(리뷰→fix→QA→잔여→fix…)한 데 대해 개발책임자가 **"이런 건 PM 이
조절해야 한다"** 지적. 결정 = **"구성품 fix 마치고 R9 1회 종합 후 오늘 머지"** — R9 findings 를 한
코너씩 재QA 하지 말고 **일괄 disposition + 재수렴 1회**로 바운드. PM 조절 규율
(`.claude/memory/feedback_pm_regulate_slice_effort.md`) 박제.

## 정직 한계 (R4 확정 — 알려진 경계와 수용 근거)

1. **의도된 trade-off(기존, R3 채택)**: 가격기억 flush 는 **bounded async** — 저장 직후 짧은
   창에서 이전값/miss 를 볼 수 있고(정상 시 수 ms, 과부하 시 queue 대기만큼), 커밋 직후 프로세스
   종료 시 해당 작업은 유실될 수 있다(runbook 의 원 라인 기반 backfill 로 복구). 원 전표/견적
   저장의 fail-soft 가 우선이다. FE 는 서버 결과를 그대로 표시하며 자동재시도는 없다.
2. **D-R4-2 잔존 창**: `remembered_at` 은 원 트랜잭션 커밋 **前** 애플리케이션 캡처 시각이라 실제
   커밋 순서와 **ms~수백ms 창에서 역전될 수 있다**. flush 실행 순서 역전은 실 PostgreSQL IT
   `afterCommitExecutionInversion_keepsLaterLogicalSave` 가 방어를 실증했고, 캡처-커밋 역전이
   나더라도 두 값 모두 사용자가 실제 입력한 단가라(자동채움은 제안값) 실질 피해는 확인된 바
   없다. **커밋순서 권위(`clock_timestamp()`/시퀀스)는 미채택** — 동시저장 빈도가 문제되면 재검토.
3. **D-R4-3 서브-원 드리프트**: VAT포함 100,000 생성 → 라인 공급 90,909 파생 → 공급단가 UI
   무변경 저장 → 기억값 **99,999.90** 이동(-0.10). **1회 수렴·비복리**(R3 CH-1 의 9.1% 복리와
   다름)이고 11의 배수 단가에선 발생하지 않는다. 두 저장 basis(VAT포함/공급) 병존의 내재적 반올림
   한계로 **수용**.
4. **R4-B4 시계 역행**: 서버 시계가 뒤로 가면 recency guard 가 역행 폭만큼 갱신을 **조용히 skip**
   한다(statement 자체는 성공이라 success 카운터만 증가·알람 침묵). 수용 근거: 단일 컨테이너 ·
   `Clock.system(Asia/Seoul)`(`TimeConfig.java`) · KST 는 DST 없음 · 정상 NTP 는 step-back 이 아닌
   slew 보정.
5. **R4-D5**: 기억 저장일이 hover `title` 전용이라 키보드/터치의 시각 사용자는 저장일을 확인할 수
   없다. 수용 근거: 핵심 의미('거래처 전용 과거가')는 마커 라벨 텍스트가 전달하고, 스크린리더는
   `aria-describedby` 로 청취 가능하다.
6. **R4-D8**: 거래처 변경 강조행이 기존 선택행과 시각적으로 유사하다(둘 다 `--surface-selected`).
   수용 근거: 색상 단독 의존이 아님 — 배너 + 4px 좌측 보더 + 마커 칩 텍스트가 병행 전달한다.
   (⚠️ R5-M5 가 이 수용 근거를 뒤집었다 — 4px 보더도 색이고 칩 텍스트는 '변경'을 말하지 않는다.
   R5 fix 로 변경행에 `단가 변경` 아이콘+텍스트 인디케이터와 행 `aria-describedby` 연결을 추가했다.)
7. 🔴 **D-R4-4 는 현재 라이브 GUI 로 도달 불가**: `PartnerAutocomplete`(AsyncAutocomplete)에
   **거래처 해제 어포던스가 없다** — clear 버튼 부재, 빈 입력 blur 는 onChange 미호출("더미
   onChange 금지" 게이트), free-text 불일치는 기존 선택 유지. 따라서 전표
   `handlePartnerAutocompleteChange(null)`/견적 `handlePartnerOptionChange(null)` 분기는 현재
   UI 로는 도달 불가한 **방어 코드**다. FE 단위테스트 2건이 커버한다(`SlipFormPage.test.tsx` 해제
   시 단가 유지·마커 해제 / `LineRow.test.tsx` `partnerSelected=false` 시 REMEMBERED 마커 미렌더).
   **거래처 해제 UI 도입 여부는 별도 결정 사항**이다.
8. **R4-F4 라이브 QA 검증 방법론**: 재조회 in-flight 창이 로컬에선 수십 ms 라 실측이 불가능해,
   견적 시나리오 1건에 한해 `page.route` 로 **실서버 실응답을 그대로 전달하면서 2.5s 지연만
   주입**했다(응답 내용 변조/합성 없음 — 네트워크 지연 재현). 스펙 주석에 명시돼 있다.
9. **실 브라우저 스크린리더 수동 QA 미수행** — a11y 항목(R4-D1/D2/D9)의 검증은 자동테스트 +
   코드/토큰 실값 대비 계산 수준까지다.
10. **R2 라이브 QA "7/7 PASS" 는 superseded** — R3 QA 가 당시 스펙 자체의 false-green(견적 저장
    500 이어도 통과 가능·임의 견적 조회·존재만 단언)을 적발해 `r2/qa-report.md` 상단에 정정
    이력을 남겼다. `r2/` 는 실행 이력으로 보존한다. R4 경화 스펙 재실행이 최종 증거다.
11. **`r4/`(pre-fix 14장)와 `r4-postfix/`(post-fix 23장)의 구분**: `r4/` 는 R4 적대검증이
    **검증한 대상(fix 이전 `e7a2ff0d6`)** 의 증거라 보존한다 — 캡처에 마커가 `정가` 로 보이는
    것이 정상이다(D-R4-1 fix 이전 캡처).

## 정직 한계 — R5/R6 신규 확정 (2026-07-16)

1. **로컬 통과 ≠ CI green** — R4 fix 완료 게시의 "desktop vitest 740/740 green" 보고는
   틀렸다(R5-B1: hydration 미대기 flaky 가 느린 CI 에서만 발현). 검증 권위는 exact SHA 의 CI 다.
   PM 정정 기록.
2. **R4-F2 도달성 과장 정정** — "estimate_lines 1927/1927 = 100% legacy 라 전부 오염 대상"은
   과장이었다. **당시 실 legacy 1,926건 전부 `partner_id NULL`** → 가격기억 upsert 가 null-skip →
   **현 실데이터로는 legacy 오염 경로에 도달 불가**. fix 는 유효하다(거래처 재선택 시 경로가 열림).
   ⚠️ **2026-07-16 재정정**: 위 1,926/1,927 은 **재시드로 stale** 하다 — 현재 `estimates` **3건**
   (legacy **0건**) · `estimate_lines` **3건**. **과장이 있었다는 사실은 이력으로 보존**하되 수치는
   R8 절 §R7 실측치 정정 을 권위로 삼는다.
3. **"#809 회귀" 가설 반증** — legacy 견적 저장 차단은 **main 도 동일**하다(main hydrate 가
   `setPartner` 미호출 → `!partnerIdSnapshot && !partner` 로 동일 차단 — 소스 대조). #809 은 오류
   메시지만 더 정확하게 바꿨을 뿐 차단을 도입하지 않았다.
4. **정상 coedit 모드에서 legacy 견적 저장 데드락**(R6-H6·라이브 CONFIRMED) —
   `PartnerAutocomplete disabled={coeditActive}` 라 오류 문구의 지시("거래처를 다시 선택해
   주세요")를 이행할 수 없다. main 동등(회귀 아님)이나 #809 이 non-legacy 는 고쳐놓고 legacy 만
   남겼고, R5-H6 의 provenance 보호는 coedit 실패 fallback 에서만 도달 가능하다.
   ✅ **해소 — D-R8-1 로 "이 PR 에서 fix" 확정**(문구 정정에 그치지 말고 실제 저장 경로를 열 것).
   ⚠️ 원 기술의 "**1,926건**" 은 **stale**(현 legacy **0건**) → **R8-DESIGN-1 경로(정상 견적 + coedit)로
   fix·QA 를 잡아야 한다**(R8 정직 고지 2 · 현 픽스처 3건으로 재현 가능).
5. **BUNDLE_SET parent 기억은 생성 시점 1회뿐**(R6-M8·라이브 CONFIRMED) — 수정 경로에서 세트
   가격을 바꿔도 기억이 갱신되지 않아 재선택 시 구값이 자동채움된다.
   ✅ **종결 — D-R8-5 로 "설계 귀결·결함 아님" 확정**(오전 D-R8-3 "결함 처리" 의 **번복** — 전제가
   R8-BE-2 로 코드 반증됨). spec §BUNDLE 정책 에 **정의로 명시하고 close** 했다. **더 이상 확인 대기
   아님이며, 다음 라운드가 결함으로 재제기해서는 안 된다.**
6. **Hikari 4s 전역화의 blast radius**(R6-M1) — `connection-timeout` 명시는 fleet 26모듈 중
   slip-service 유일이며, 진짜 용량 구멍(무 timeout in-tx RestClient)은 미해결이다.
   ✅ **해소 — D-R8-2 로 "가격기억 전용 DataSource 격리 후 전역 30s 복원" 확정**. R8-DEVOPS-2 가 진단을
   실측 확인(pool 포화 시 21번째 요청 → `SQLTransientConnectionException` → **HTTP 500**)했고,
   R8-BE-4 가 **격리 설계 함정 3종**을 냈다(①`set_config(is_local)` 무력화 ②autoconfig back-off 기동
   실패 ③stale 주석). ⚠️ **범위 점증 → 정식 리뷰 대상.**
7. **라이브 QA 스펙이 공유 스택 동시 사용에 false-RED**(R6-M9) — 시나리오 12a 가 타 차원
   에이전트의 동시 PUT 으로 FAIL → 격리 재실행 PASS 로 교차 오염 확정. **PM 오케스트레이션
   결함**(라이브 프로브를 쓰는 차원의 병렬 실행) — 스펙 격리 강화 + PM 규율 개정 대상.
8. **기존 승계** — 위 R4 절의 ① bounded async trade-off · ② D-R4-2 캡처~커밋 창 · ③ D-R4-3
   서브-원 드리프트 · ④ 시계 역행 skip 침묵 · ⑤ 저장일 hover title 전용 · ⑦ D-R4-4 GUI 도달
   불가 · ⑨ 실 브라우저/스크린리더 수동 QA 미수행은 그대로 유효하다(⑥ R4-D8 은 R5-M5 로 수용이
   철회되고 fix 됐다 — 해당 항목의 주석 참조). 추가로 **legacy `QUOTE_SENT` 실표본 0**(당시 1,926행
   전부 `QUOTE_DRAFT` — R5 정직 보고·R6 재확인)이라 SENT 상태 legacy 경로는 실데이터 검증이
   불가능했다. ⚠️ **2026-07-16**: 재시드로 **legacy 자체가 0행**이 되어 이 경로는 여전히 실데이터
   검증 불가다(사유만 "전부 DRAFT" → "표본 부재" 로 바뀜).

## 범위 외로 남긴 것 (후속 후보 — 정직 명시)

- **R4-Q2**: dev 시드 `TEST-BUNDLE-SET-01` 내부 불정합 — `bundle_component.component_product_code`
  에 product_code 대신 **model명**이 시드돼 구성품 resolve 실패 → `POST /slips` 404 → **이 세트는
  어떤 화면에서도 저장 불가**. dev seeder(product-service) 소관이라 #809 범위 밖. 본 라운드는
  `QA797-SET-01` 로 우회해 세트 경로를 실증했다.
- **`nightly-slip-it.yml` slip-it-public 유령 패키지 — ⚠️ 정정: 더 이상 범위 외 아님(#821 을 본
  PR 에서 fix)**: `--tests` 필터가 참조하던 `slip.web.public_.*`/`slip.web.openapi.*` 는 현 트리에
  부재했고, 최근 nightly run(2026-07-14)의 slip-it-public job 이 정확히 이 필터로 `No tests found
  for given includes` 실패 중이었다. 당초 이 항목을 "본 PR 미접촉 파일이라 범위 외" 로 기록했으나
  이 문장은 **개발책임자 "같이 fix" 결정(#821) 이전에 쓰인 것**으로 현 diff 와 모순됐다(R5-M9·
  R6-M7 지적). 실제로는 **범위 점증분으로 R4 fix 배치 `d08b1c281` 이 필터를 현 트리 기준으로
  정렬**했고(연관 Issue #821), R5 fix 배치 `ffc8e49ab` 가 Issue 생성 label fallback(R5-M8)과 실행
  검증(R5-M9 — PR ref `workflow_dispatch` run 29431175485 success)을 보완했다. 단 **scheduled
  nightly 는 main 의 구 필터로 계속 실패 중이며 본 PR 머지 전까지 붉은 상태가 유지**된다(R6-M10 —
  07-09~07-14 6연속 실패 실측).
- **`estimate-form-coedit-pending`**(`EstimateFormPage.tsx:1490`): R4-D9 와 동일한 조건부 마운트
  `role="status"` 패턴이나 **#809 무관 선재**(협업 에픽 산출 — `git show main` 으로 실증) →
  후속 정리 후보.
- **`role="alert"` 4곳**(SlipFormPage 2·EstimateFormPage 2)도 조건부 마운트이나 **#809 무관 선재**
  (main 동수 실증) + ARIA 명세상 alert 는 동적 삽입이 표준 발화 경로라 위험도 낮음.
- **Pretendard 폰트 파일 부재**(R5-L3 · R5 Design 차원 발견): 데스크톱 렌더러에 `@font-face` 선언은
  있으나 빌드 산출물(`public`/`out/renderer`/`dist/web`)에 woff/woff2 가 0건 — Pretendard 미설치
  환경에선 Segoe UI/맑은 고딕 fallback 으로 렌더된다. **#809 무관 선재** → 범위 외 이슈 등록 후보.
- **slip 합계셀 6자리 이상 금액의 2줄 랩 클리핑**(R6 Design 차원 관찰 — 본 문서 배치는 별도 재검증
  하지 않음): 합계(VAT포함) 셀에서 긴 금액이 줄바꿈되며 잘려 보인다는 관찰. #809 무관 선재 레이아웃
  이슈로 분류 → 후속 확인 후보.
- **단가 입력 표기 불일치**(R6 Design 차원 관찰): 전표 LineRow 는 단가를 콤마 포맷으로 표시
  (`LineRow.tsx:218` `toLocaleString`)하고 견적 폼은 raw 문자열 입력을 그대로 표시한다 — #809
  이전부터의 화면 간 불일치로 후속 통일 후보.
