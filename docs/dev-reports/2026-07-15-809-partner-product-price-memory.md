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

## 범위 외로 남긴 것 (후속 후보 — 본 PR 미접촉, 정직 명시)

- **R4-Q2**: dev 시드 `TEST-BUNDLE-SET-01` 내부 불정합 — `bundle_component.component_product_code`
  에 product_code 대신 **model명**이 시드돼 구성품 resolve 실패 → `POST /slips` 404 → **이 세트는
  어떤 화면에서도 저장 불가**. dev seeder(product-service) 소관이라 #809 범위 밖. 본 라운드는
  `QA797-SET-01` 로 우회해 세트 경로를 실증했다.
- **`nightly-slip-it.yml` slip-it-public 그룹의 유령 패키지**: `--tests` 필터가 참조하는
  `slip.web.public_.*`/`slip.web.openapi.*` 는 현 트리에 부재한다(패키지 선언 grep 0건 실측).
  문서 배치 추가 실측: **최근 nightly run(2026-07-14)의 slip-it-public job 이 정확히 이 필터로
  `No tests found for given includes` 실패 중** — 무해한 dead-weight 가 아니라 nightly 를 붉게
  만들고 있는 pre-existing 결함이다. 본 PR 미접촉 파일이라 범위 외로 남기며 후속 정리가 필요하다.
- **`estimate-form-coedit-pending`**(`EstimateFormPage.tsx:1490`): R4-D9 와 동일한 조건부 마운트
  `role="status"` 패턴이나 **#809 무관 선재**(협업 에픽 산출 — `git show main` 으로 실증) →
  후속 정리 후보.
- **`role="alert"` 4곳**(SlipFormPage 2·EstimateFormPage 2)도 조건부 마운트이나 **#809 무관 선재**
  (main 동수 실증) + ARIA 명세상 alert 는 동적 삽입이 표준 발화 경로라 위험도 낮음.
