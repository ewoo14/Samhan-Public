# product-service: 구글 스프레드 시트 → DB cron 동기화 (옵션 C-2)

> Phase 6 backend 정정 슬라이스. PR #38 (M1a 시드) 머지 후 정책 변경.
> Branch: `feature/migration-be-product-google-sheets-sync`. Base: `origin/main`.

## 1. 배경

개발책임자 결정 (2026-05-05):
> "견적서와 주문서의 경우에만 기존 구글 스크립트처럼 구글 스프레드 시트에서 그대로 가져오는 것으로 하자"

기존 product-service M1a (PR #38 머지) 는 27 sheet → 8 entity DB 시드 구조. 본 작업은 시트가 source-of-truth 인 운영 모델로 정정하기 위해 **구글 시트 → DB cron 1시간 동기화** 추가.

legacy 시트 ID `1RJqO3jT-yJTi3NDBhL60o_cZWlVETGTU7UlvIKXuVNQ` 그대로 사용. 27 tab → 8 entity 매핑 그대로 (PR #38 보존).

## 2. 옵션 비교 + 채택

| 옵션 | 설명 | 장점 | 단점 | 결정 |
|---|---|---|---|---|
| C-1 | 실시간 시트 read (호출마다) | DB 동기화 불필요 | 시트 API per-minute quota (60 read) 한계 + 응답 지연 | 기각 |
| **C-2** | **cron 1시간 주기 시트 → DB sync** | **DB read 빠름 + 시트 변경 1시간 내 반영 + quota 부담 분산** | **시트 변경 즉시 반영 안 됨 → C-3 결합** | **채택** |
| C-3 | admin endpoint 수동 trigger | 즉시 반영 가능 | 자동 sync 부재 | C-2 와 결합 (보조) |

본 PR 은 **C-2 + C-3 결합** — cron 자동 + admin 수동 trigger 동시 제공.

## 3. 변경 매트릭스

### 신규 파일 (4)

| 파일 | 책임 |
|---|---|
| `services/product-service/src/main/java/.../client/GoogleSheetsClient.java` | Google Sheets v4 SDK 래퍼. Service Account JWT 인증. Caffeine 5분 TTL 캐시. |
| `services/product-service/src/main/java/.../service/ProductSheetSyncService.java` | tab 별 별도 트랜잭션 sync. row hash (SHA-256) 기반 변경 감지. soft-delete 자동. |
| `services/product-service/src/main/java/.../scheduler/ProductSheetSyncScheduler.java` | `@Scheduled` cron + `ApplicationReadyEvent` 부팅 1회. |
| `services/product-service/src/main/java/.../web/ProductAdminController.java` | `POST /api/v1/products/admin/sync` 수동 trigger (옵션 C-3). |

### 정정 파일 (3)

| 파일 | 변경 |
|---|---|
| `services/product-service/build.gradle` | `google-api-client` 2.4.0 + `google-api-services-sheets` v4-rev20240514 + `google-auth-library-oauth2-http` 1.23.0 + `caffeine` 3.1.8 + `wiremock-standalone` 3.9.1 (test) 추가 |
| `services/product-service/src/main/resources/application.yml` | `google.sheets.*` 4 키 + `app.scheduling.*` 2 키 추가. local profile 에서 `app.scheduling.enabled=false` override. |
| `services/product-service/src/main/java/.../ProductServiceApplication.java` | `@EnableScheduling` 활성 |

### IT (1)

| 파일 | 시나리오 |
|---|---|
| `services/product-service/src/test/java/.../it/ProductSheetSyncServiceIT.java` | (1) 첫 sync insert only (2) 동일 시트 재 sync — rowHash 일치, update 없음 (3) 가격 변경 시 update 발생 (4) 시트에서 사라진 row soft-delete |

### dev-report (1)

`docs/dev-reports/migration-be-product-google-sheets-sync.md` (본 문서)

### legacy 보존

- `ProductSeedRunner` (Phase 6 M1a dry-run runner) — **변경 없음**. cron 가용 전 fallback 으로 유지.
- V1~V4 Flyway migration — 변경 없음. 시트 sync 는 기존 schema 위에서 동작.
- 27 tab → 8 entity 매핑 — `ProductSheetSyncService.TAB_MAPPINGS` 가 PR #38 의 6 카테고리 매핑 (홈멀티/싱글 세트/싱글 구성품/상업멀티/상업멀티 구성/구형) 그대로 보존.

## 4. 동기화 룰 상세

### TAB → 도메인 매핑 (PR #38 보존)

| 시트 tab | productCategory | usageScope | estimateCategory |
|---|---|---|---|
| 홈멀티 | HOME_MULTI | BOTH | HOME_MULTI |
| 싱글 세트 | SINGLE_SET | BOTH | SINGLE_SET |
| 싱글 구성품 | SINGLE_PART | NONE | (null) |
| 상업멀티 | COMMERCIAL_MULTI | BOTH | COMMERCIAL_MULTI |
| 상업멀티 구성 | COMMERCIAL_PART | NONE | (null) |
| 구형 | OLD | BOTH | LEGACY |

### upsert 매트릭스

| 상태 | 행동 |
|---|---|
| DB 에 없음 + 시트에 있음 | INSERT (`Product.seedFromSheet`) |
| DB 에 있음 + 시트에 있음 + rowHash 일치 | unchanged (skip) |
| DB 에 있음 + 시트에 있음 + rowHash 불일치 | UPDATE (가격 + usage 갱신) |
| DB 에 있음 + 시트에 없음 | soft-delete (`BaseEntity.markDeleted("system-sheet-sync")`) |
| 다음 sync 시 시트에 재현 | (현재 PR scope X) — 후속 PR 에서 deleted 복구 룰 추가 가능 |

### 캐시 + 트랜잭션

- Caffeine 5분 TTL — cron sync (1시간 주기) + admin trigger 동시 호출 시 시트 API quota 가드.
- tab 1개씩 별도 트랜잭션 (`@Transactional` on `syncTab`) — 1 tab 실패가 전체 sync 무효화 방지.
- row 단위 실패는 catch + log + skip (sync continuity 우선).

### 부팅 + cron

- `@EventListener(ApplicationReadyEvent.class)` — 부팅 시 1회 sync. 실패 시 catch + log (부팅 차단 X).
- `@Scheduled(cron = "${app.scheduling.product-sync-cron:0 0 * * * *}")` — 매시 정각.
- 환경변수 override: `PRODUCT_SYNC_SCHEDULING_ENABLED`, `PRODUCT_SYNC_CRON`.

## 5. IT 시나리오

`ProductSheetSyncServiceIT` 4 테스트 — `@MockBean GoogleSheetsClient` 격리 (memory `feedback_it_mockbean_external_clients.md` 가드).

| 테스트 | 검증 |
|---|---|
| `sync_첫실행_insert_only` | 시트 1 row → DB 에 insert 1건, productCategory=HOME_MULTI, releasePrice 일치 |
| `sync_재실행_rowHash_동일이면_update_없음` | 동일 시트 응답 2회 sync → 2회차 unchanged=1, updated=0 |
| `sync_가격변경시_update_발생` | 시트 응답 가격 변경 → 2회차 updated=1, releasePrice 신규 값 |
| `sync_시트에서_사라진_row_softDelete` | 시트에서 row 제거 → softDeleted=1, active 조회 시 없음 |

### IT 환경 트랩

- Windows + Docker Desktop npipe 한계로 Testcontainers PostgreSQL skip 가능 (memory `feedback_testcontainers_windows_docker.md`).
- `AbstractPostgresIT.DockerAvailableCondition` 가 자동 disabled 처리 → build SUCCESSFUL.
- CI (Linux runner) 에서는 정상 실행.

## 6. 빌드 결과 (로컬)

| 명령 | 결과 |
|---|---|
| `./gradlew :services:product-service:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:product-service:compileTestJava` | BUILD SUCCESSFUL |
| `./gradlew :services:product-service:assemble` | BUILD SUCCESSFUL (`product-service.jar`) |
| `./gradlew :services:product-service:test` | BUILD SUCCESSFUL (IT 4건 Windows Docker npipe → SKIPPED, 단위 test 통과) |

## 7. 가드 적용

| 가드 | 적용 |
|---|---|
| BaseEntity 7 audit fields | ✓ Product entity 변경 없음 (기존 7 필드 그대로) |
| Soft Delete | ✓ `BaseEntity.markDeleted("system-sheet-sync")` 호출, hard delete 없음 |
| Korean accounting 표준 코드 | (해당 없음 — accounting-service 영역) |
| 한국어 Javadoc + commit + PR | ✓ 모든 신규 파일 한국어 주석 |
| `feedback_it_mockbean_external_clients.md` | ✓ `@MockBean GoogleSheetsClient` 격리 |
| `feedback_testcontainers_windows_docker.md` | ✓ DockerAvailableCondition 사용, Windows skip 허용 |
| `feedback_korean_path_jdk.md` | ✓ worktree 영문 path 에서 assemble + 단위 test 통과 |
| `feedback_uuid_no_user_visibility.md` | ✓ admin endpoint response 는 SyncSummary (UUID 미포함) |
| `feedback_function_documentation.md` | (1) 한국어 Javadoc 모든 service/scheduler/client (2) springdoc-openapi (admin endpoint @Operation) (3) 본 dev-report |
| 시크릿 (Service Account JSON) | ✓ placeholder path 만 (`/etc/samhan/sa-key.json`) — 실 값 SSH 직접 |
| legacy 시트 ID 보존 | ✓ `1RJqO3jT-yJTi3NDBhL60o_cZWlVETGTU7UlvIKXuVNQ` |
| legacy 27 tab → 8 entity 매핑 | ✓ PR #38 의 6 카테고리 매핑 (홈멀티/싱글세트/싱글구성품/상업멀티/상업멀티구성/구형) 그대로 |

## 8. 후속 작업

| 작업 | 책임 | 비고 |
|---|---|---|
| FE PR — desktop/estimate-app 의 mock fallback 제거 | 별도 spawn | 본 PR 머지 후 시트 데이터 DB 도착 확인 필요 |
| BranchPipeLookup / OduRecommendation / MaterialPrice / BundleComponent 시트 → DB sync 확장 | 후속 PR | 본 PR 은 6 카테고리 ProductMaster 만 sync. 나머지 4 entity 는 V3 Flyway 시드 그대로 |
| admin endpoint role 게이트 (ROLE_ADMIN) 강화 | 후속 PR | 현재 anyRequest().authenticated() 만 통과 |
| 시트 schema 변경 시 alert | 후속 PR | sync 실패 N회 연속 시 Slack/email |
| 시트 row 삭제 → DB soft-delete 후 시트 재현 시 복구 룰 | 후속 PR | 현재 unique partial index 위배 가능성 검토 필요 |
| Service Account JSON 운영 배포 | DevOps | `/etc/samhan/sa-key.json` 위치 + 권한 설정 (chmod 600 root) |

## 9. TM 검토 포인트

- [x] 옵션 C-2 채택 근거 명시 (§2)
- [x] legacy 보존 (시트 ID + 27 tab → 8 entity 매핑) 검증
- [x] BaseEntity + Soft Delete + 한국어 Javadoc 가드 적용
- [x] IT 4 시나리오 + @MockBean 격리
- [x] 환경변수 + 시크릿 placeholder
- [x] 후속 작업 분리 (FE mock 제거 / 다른 entity sync / admin role / alert)
