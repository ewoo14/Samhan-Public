# Phase 10 W10-step-9 — 시트 흐름 보강 + 노션 4 CSV 이식 + partner_code 매핑 정정

> 본 dev-report 는 PR-D (`feature/integrated-phase-10-step-9-sheet-notion-import`) 의 종합 작업 보고. 11 commit (Phase A 6 + Phase B 5) + TM Phase C (partner_code 매핑 정정).

## 1. 배경

PR #114 (W10-step-8) 머지 후 두 가지 우려 처리:

1. **시트 비동기 회귀** — `partner-order-service` + `product-service` 의 sheet 동기화 누락 (사용자 옵션 C 의도 미충족) → Part 1 (BE-A) 슬라이스
2. **노션 4 운영 CSV 이식** — Samhan Public 운영 환경의 Notion DB 4건 (REGION / DC / CHAT / BLOCK) 을 native MSA 로 이식 (Notion 의존성 제거) → Part 2 (BE-B/C/D/E + FE-A/B/C/D/E + Designer)
3. **사용자 명시 정정** (2026-05-10) — 단톡방/발송금지 import 가 거래처명이 아니라 **거래처코드** 컬럼 우선 매핑하도록 정정 → TM Part 3

## 2. 산출물

### 2-1. Phase A — Backend 5 슬라이스

| Commit | 범위 | Flyway |
|---|---|---|
| `8b6ac60` | partner-order + product 시트 흐름 보강 (BE-A Part 1) | — |
| `645428e` | arologis REGION 가배차 지역별 분류 (BE-B Part 2-1) | arologis V3 |
| `b44ede3` | dc-config 거래처 할인 정보 CSV import (BE-C Part 2-2) | dc-config V2 |
| `9c38506` | notification CHAT 단톡방 매핑 (BE-D Part 2-3) | notification V2 |
| `d05c0ae` | partner BLOCK 발송금지 import (BE-E Part 2-4) — **commit 메시지 오류**: 실제 변경은 partner-service 의 BlockedPartner + PartnerBlockImportService + findByName, 메시지에는 잘못 "notification CHAT" 로 표기됨 (rebase 회피, PR body 안내) | partner V4 |

### 2-2. Phase B — Desktop FE 5 admin UI

| Commit | 범위 |
|---|---|
| `6060c12` | 시트 동기화 admin UI (FE-A) |
| `fb61b86` | 지역 분류 admin UI (FE-B) |
| `34ce7fc` | DC 설정 CSV 일괄 업로드 (FE-C) |
| `5e59b4e` | 단톡방 매핑 admin UI (FE-D) |
| `cca8192` | 발송금지 거래처 admin UI (FE-E) |
| `fa42fdf` | Designer CsvUploadDialog 컴포넌트 (Designer 공유) |

### 2-3. Phase C — TM 종합 (partner_code 매핑 정정)

사용자 명시 (2026-05-10): "단톡방리스트와 발송금지리스트의 경우 추후 거래처명이 아니라 거래처코드로 매핑할 수 있도록 관련 코드들 수정"

#### 정책

| 우선순위 | 분기 | 메서드 |
|---|---|---|
| 1 | CSV 의 `거래처코드` 또는 `partner_code` 컬럼 값 검증 | `PartnerLookupClient.verifyPartnerCode` (notification) / `PartnerService.findByCodeForLookup` (partner) |
| 2 (fallback) | 코드 미공급 또는 검증 실패 시 사업자명 lookup | `findPartnerCodeByName` / `findByNameForLookup` |
| 3 (reject) | 둘 다 매칭 실패 시 reject row 누적 | `LOOKUP_MISS` 사유 + 입력 코드/사업자명 노출 |

#### snapshot 처리

코드만 공급 + 사업자명 미공급 시 `partnerBusinessNameSnapshot` 은 `[partnerCode]` placeholder 로 기록 (entity invariant 보호 + admin UI 후속 보완 경로).

#### 변경 범위

- **shared interface**:
  - `services/notification-service/.../client/PartnerLookupClient.java` — `verifyPartnerCode(String)` 메서드 추가
  - `services/notification-service/.../client/NoopPartnerLookupClient.java` — Lambda → Anonymous class (2 메서드 구현)
- **import service**:
  - `services/notification-service/.../service/ChatRoomImportService.java` — 거래처코드 컬럼 우선 + fallback 분기
  - `services/partner-service/.../service/PartnerBlockImportService.java` — 거래처코드 컬럼 우선 + fallback 분기
- **lookup service**:
  - `services/partner-service/.../service/PartnerService.java` — `findByCodeForLookup(String)` Optional 형 추가
- **테스트** (단위 8 case 추가):
  - `ChatRoomImportServiceTest` — 코드 우선 매칭 / 코드 miss → 이름 fallback / 코드만 공급 (placeholder) / 영문 헤더 4 case
  - `PartnerBlockImportServiceTest` — 코드 우선 매칭 / 코드 miss → 이름 fallback / 코드만 공급 (placeholder) / 양쪽 miss reject 4 case
  - `ChatRoomMappingAdminControllerIT` — `verifyPartnerCode` lenient mock 추가

#### 범위 외 (별도 PR 위임)

- **R2** — `arologis-service` `KakaoDispatchParser` 의 "-214" 카톡 슬립번호 식별자 (Long) vs `partner-service` 의 `partner_code` (String "P-2026-0001") 명칭 충돌. 본 PR 범위 외 — entity 컬럼 rename + 마이그레이션 동시 진행 필요 (사용자 명시 격리)
- **ManualDispatchRequest.partnerCode** (Long) — 카톡 슬립번호 source 그대로 보존 (R2 별도 PR 시 String partner_code 분리)
- **PartnerLookupClient 실 구현** — 현재는 `NoopPartnerLookupClient` placeholder (모든 row miss). 실 RestClient impl 은 별도 BE-E follow-up PR 위임

## 3. Flyway V 락 (충돌 회피)

| Service | 본 PR 신규 |
|---|---|
| arologis-service | V3 (regions seed + classified_region_group 컬럼) |
| dc-config-service | V2 (partner_dc_overrides 테이블 + seed) |
| notification-service | V2 (partner_chat_room_mappings 테이블) |
| partner-service | V4 (blocked_partners 테이블 + soft-delete unique index) |

## 4. 검증 결과

### 4-1. 단위 테스트

| 슬라이스 | case | 결과 |
|---|---|---|
| REGION (arologis) | 11 | GREEN |
| DC (dc-config) | 8 | GREEN |
| CHAT (notification ChatRoomImport + service) | 13 + 4 (TM 추가) | GREEN |
| BLOCK (partner PartnerBlockImport + service) | 17 + 4 (TM 추가) | GREEN |
| Designer Storybook (CsvUploadDialog) | 3 | GREEN |
| FE typecheck (admin pages) | 5 | GREEN |

### 4-2. 풀빌드

- `./gradlew assemble` — exit 0
- `pnpm --filter desktop typecheck` — exit 0

### 4-3. TM 사후 검증 (Part 3 매핑 정정)

```
./gradlew :services:notification-service:compileJava :services:notification-service:compileTestJava \
          :services:partner-service:compileJava :services:partner-service:compileTestJava
→ BUILD SUCCESSFUL

./gradlew :services:notification-service:test --tests "*.ChatRoomImportServiceTest" \
          :services:partner-service:test --tests "*.PartnerBlockImportServiceTest"
→ BUILD SUCCESSFUL
```

## 5. memory 가드 준수

- `feedback_continuous_docs_sync` — ROADMAP + DECISIONS + dev-report 동시 갱신 (본 PR)
- `feedback_integrated_pr_pattern` — 11 commit 단일 통합 PR (단편 PR 분할 회피)
- `feedback_uuid_no_user_visibility` — 모든 admin UI 응답 DTO 가 partner_code 위주 (UUID 비공개)
- `feedback_korean_commits` — 모든 commit / docs / PR body 한국어
- `feedback_function_documentation` — 모든 신규 메서드 한국어 Javadoc + dev-report 누적
- `feedback_it_mockbean_external_clients` — `PartnerLookupClient` IT 에서 `@MockBean` 격리 (`verifyPartnerCode` 포함)

## 6. PR body 안내

### d05c0ae commit 메시지 오류

`d05c0ae` 의 commit 메시지가 "feat(notification-service): PR-D 2-3 CHAT 단톡방 매핑" 으로 표기되어 있으나, 실제 diff 는 partner-service 의 BLOCK (BE-E) 작업. rebase 회피 (memory 가드 — interactive rebase 금지) 로 본 PR body 에 사실 명시 + dev-report 에 기록.

### Phase 11 진입 가드 보강

- 별도 PR 위임 backlog 2건 (R2 + BE-E 실 구현체) — 사용자 강화 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지") 와 충돌 검토:
  - R2 = entity 컬럼 rename + 마이그레이션 회귀 위험 큼, 사용자 직접 격리 명시
  - BE-E 실 구현체 = partner-service 측 endpoint 신규 + 인증/캐시 정책 결정 필요, 별도 슬라이스 적정
- 두 건 모두 **Phase 11 진입 전** 별도 PR 처리 의무 (Phase 11 위임 X, 본 PR 머지 후 즉시 슬라이스)

## 7. 후속 작업

| 우선 | 작업 | 비고 |
|---|---|---|
| 1 | BE-E 실 RestClient `PartnerLookupClient` 구현체 등록 | partner-service 측 `GET /api/v1/partners/by-name` + `GET /api/v1/partners/{partnerCode}` endpoint 발행 + 인증 정책 + 캐시 TTL 결정 |
| 2 | R2 — KakaoDispatchParser 슬립번호 vs partner_code 명칭 충돌 정리 | `VehicleStop.parsed_partner_code` (Long, 슬립번호) → 별도 컬럼명 또는 partner_code (String) 분리, V Flyway + entity 마이그레이션 |
| 3 | 운영자 가이드 — CSV 거래처코드 컬럼 사용법 | admin UI 의 CSV upload dialog 에 거래처코드 컬럼 우선 매핑 안내 추가 |
