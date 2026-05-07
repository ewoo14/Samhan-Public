# Phase 10 W10-4 — slip-service 전자서명 통합 (LINK + APP signatureSource)

> **W10-3 (PR #98) 머지 직후 진입** — main HEAD = `4b2c077`. 본 PR (#99) 은 slip-service + arologis-service 양쪽 service 동시 변경 (회귀 위험 큼).

## 1. 슬라이스 요약

| 항목 | 값 |
|---|---|
| 슬라이스 | Phase 10 W10-4 (slip-service 전자서명 LINK + APP 통합) |
| 영향 service | **slip-service + arologis-service** (양쪽) |
| Flyway 신규 | V10 (slips.signature_source / driver_signature_source / slip_signature_audit.signature_source 3 컬럼) |
| 신규 enum | slip-service `SignatureSource` (LINK / APP) |
| 신규 endpoint (slip-service) | POST `/internal/slips/{slipId}/signatures` + GET `/internal/slips/by-partner/{partnerId}/recent` |
| 신규 service (arologis) | `SlipResolver` (partnerCode → slipId 매핑 + UUID 비공개 가드 fallback) |
| SlipClient 변경 | UUID + SignaturePayload 시그니처 + skeleton-mode 실 호출 분기 |
| ArologisDriverAppController | sign endpoint 통합 호출 (양쪽 저장 + slipBridged 응답) |
| InternalTokenFilter | slip-service 신규 (arologis / partner 와 일관 패턴) |
| W10-3 F-3 채택 | ApiResponse wrapper IT schema 검증 의무화 |
| docs 동기화 | 9 영역 (README × 3 + ROADMAP + DECISIONS + readiness + dev-report + 환경변수 + QA) |

## 2. 사용자 결정 + 가드 (2026-05-07)

| # | 결정 / 가드 | 본 PR 반영 |
|---|---|---|
| 1 | signature_source 컬럼 = NOT NULL DEFAULT 'LINK' (기존 데이터 backfill) | V10 SQL DEFAULT + Slip entity field default = LINK |
| 2 | LINK / APP 분리 + audit 테이블도 source 보존 (전자서명법 §17) | slip_signature_audit.signature_source 컬럼 + RECORD/RECORD_DRIVER overload |
| 3 | InternalTokenFilter `/internal/**` prefix 한정 | slip-service 신규 + SecurityConfig 등록 |
| 4 | UUID 비공개 가드 — partnerId UUID 직접 노출 회피 | SlipResolver fallback (partnerCode 매핑 가능성만 검증, partnerId 직접 lookup 별도 hook) |
| 5 | W10-3 F-3 backlog 채택 — ApiResponse wrapper IT schema 의무화 | SlipInternalControllerIT + SignatureIntegrationIT 모두 success/data schema 검증 |
| 6 | SAMHAN_AROLOGIS_CLIENT_SKELETON_MODE=false (W10-4 시점 활성) | env-templates 갱신 |

## 3. 도메인 모델 — signature_source 컬럼 분리

### 3-1. slip-service 측 (V10)

| 컬럼 | 타입 | 의미 |
|---|---|---|
| `slips.signature_source` | VARCHAR(20) NOT NULL DEFAULT 'LINK' | 인수자 서명 발급 source (LINK = SMS/Aligo, APP = arologis 어플) |
| `slips.driver_signature_source` | VARCHAR(20) NOT NULL DEFAULT 'LINK' | 기사 서명 발급 source (인수자와 직교 — 혼합 가능) |
| `slip_signature_audit.signature_source` | VARCHAR(20) NULL | RECORD / RECORD_DRIVER 시점 source 보존 (INVALIDATE 시 NULL) |

**partial index 2종** — APP source 슬립 lookup 가속화 (운영 통계용):
- `ix_slips_signature_source_app` (인수자 APP)
- `ix_slips_driver_signature_source_app` (기사 APP)

### 3-2. arologis-service 측 (변경 0)

W10-1 의 `signatures` 테이블 + `SignatureSource` enum (LINK/APP) 그대로. 기존 `source` 컬럼 보존.

### 3-3. 직교 컨셉 — Channel vs Source

| | SignatureChannel (V5) | SignatureSource (V10) |
|---|---|---|
| 의미 | **입력 매체** | **발급 경로** |
| 값 | MOBILE_CANVAS / PAPER_SCAN | LINK / APP |
| 본 PR 변경 | 없음 (보존) | 신규 추가 |

→ MOBILE_CANVAS + LINK = 기존 공개 모바일 endpoint  
→ MOBILE_CANVAS + APP = 신규 arologis 어플 직접 캡처  
→ PAPER_SCAN + LINK = Phase 5+ 종이 스캔 (확장 슬롯)

## 4. 신규 endpoint 매트릭스

### 4-1. slip-service `/internal/**`

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | `/internal/slips/{slipId}/signatures` | X-Internal-Token + ROLE_MASTER | APP source 등록 (arologis 어플 전파) |
| GET | `/internal/slips/by-partner/{partnerId}/recent` | X-Internal-Token + ROLE_MASTER | partnerId → 최근 활성 슬립 lookup |

### 4-2. arologis-service driver-app sign 통합

POST `/driver-app/arologis/dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/sign`

처리 순서:
1. arologis 자체 `signatures` INSERT (source=APP)
2. `SlipResolver.resolveByPartnerCode(stop.parsedPartnerCode)` → slipId Optional
3. (있을 때) `SlipClient.registerSignature(slipId, payload)` 양쪽 저장
4. (없거나 실패 시) graceful skip + warn log — 자체 INSERT 유지

응답 schema (W10-3 F-3 채택):
```json
{
  "success": true,
  "data": {
    "signatureId": "<uuid>",
    "slipBridged": true|false,
    "capturedAt": "2026-05-07T14:30:00"
  }
}
```

## 5. 양쪽 저장 패턴 (slip-service ↔ arologis)

### 5-1. 정상 흐름

```
arologis driver-app
  ├── POST /driver-app/arologis/dispatches/{id}/vehicles/{seq}/stops/{stopSeq}/sign
  │     body = { imageRef, latitude, longitude, driverCode }
  │
  ├── 1. arologis signatures INSERT (source=APP, GPS 캡처) — 트랜잭션 1
  │
  ├── 2. SlipResolver.resolveByPartnerCode(stop.parsedPartnerCode)
  │     ├── PartnerClient.findByCode → PartnerSummary (UUID 비공개 가드)
  │     └── (UUID 비공개 가드로) Optional.empty 반환 (현 PR fallback)
  │     OR
  │     SlipResolver.resolveByPartnerId(partnerId) (admin endpoint or 후속 cycle)
  │     └── SlipClient.findRecentSlipIdByPartner → slipId Optional
  │
  └── 3. SlipClient.registerSignature(slipId, payload)
        └── POST slip-service /internal/slips/{slipId}/signatures
              ├── X-Internal-Token 인증 + InternalTokenFilter → ROLE_MASTER
              ├── @PreAuthorize hasRole MASTER
              ├── source=APP 가드 (LINK 차단 → 400)
              ├── Slip.recordSignature / recordDriverSignature (driverCode 유무 분기)
              └── SlipSignatureAudit.record / recordDriver INSERT (source=APP)
```

### 5-2. Graceful fallback

| 시나리오 | 동작 |
|---|---|
| skeleton-mode true (W10-1 default) | SlipClient 즉시 false — arologis 자체 INSERT 만 |
| PartnerClient empty | SlipResolver Optional.empty — bridge skip + debug log |
| slip-service down (5xx) | SlipClient false — arologis 자체 INSERT 유지 + warn log |
| slip-service 4xx (NOT_FOUND/CONFLICT) | SlipClient false — 동일 (사용자 가시 영향 0) |
| slipBridged=false 응답 | 사용자에게 명시 — Phase 11 cutover 시 재동기화 가능 |

→ **운영 영향 0** — slip-service 호출 실패는 arologis 자체 저장에 영향 X.

## 6. IT 시나리오 (양쪽 service)

### 6-1. slip-service IT (`SlipInternalControllerIT`) — 9 case

| # | Case | 검증 |
|---|---|---|
| 1 | registerSignature 정상 | ApiResponse wrapper schema (success/data/slipId/slipNo/signatureSource/signed/driverSigned) + audit RECORD source=APP |
| 2 | driverCode 명시 | RECORD_DRIVER audit + driverSigned=true + source=APP |
| 3 | X-Internal-Token 누락 | 403 |
| 4 | X-Internal-Token 불일치 | 401 (filter 즉시 차단) |
| 5 | source=LINK 요청 | 400 (APP only 가드) |
| 6 | slipId 미존재 | 404 |
| 7 | DRAFT 슬립 (SIGNABLE 미충족) | 409 |
| 8 | GET /by-partner 정상 | 200 + slipId/slipNo/status schema |
| 9 | GET /by-partner 미발견 | 404 |

### 6-2. arologis-service IT (`SignatureIntegrationIT`) — 3 case

| # | Case | 검증 |
|---|---|---|
| 1 | sign + PartnerClient empty | arologis INSERT + slipBridged=false + SlipClient 미호출 |
| 2 | sign + driverCode null | 정상 INSERT (controller fallback driverCode 합성) |
| 3 | sign + stop 미존재 | 404 (회귀 가드) + SlipClient 미호출 |

→ **slip-service / arologis-service 모두 SlipClient @MockBean 격리** (PR #17 회고 가드).

## 7. 회귀 영향 + 검증 결과

| 영역 | 회귀 영향 | 검증 |
|---|---|---|
| slip-service domain (Slip / SlipSignatureAudit) | 시그니처 보존 (4-arg / 3-arg overload) — 기존 호출자 source=LINK 자동 위임 | unit test 0 회귀 |
| slip-service service (SlipSignatureService) | recordSignature 호출에 source=LINK 명시 — 기존 audit 동일 | unit + IT 0 회귀 |
| slip-service Flyway | V10 ALTER TABLE — DEFAULT 'LINK' backfill, 기존 행 호환 | IT 0 회귀 (`gradle test` PASS) |
| arologis-service SlipClient | 시그니처 변경 (UUID + SignaturePayload) — 기존 IT mock `any(), any()` 호환 | IT 0 회귀 |
| arologis-service ArologisDriverAppController | 응답 schema 확장 (slipBridged + capturedAt 추가) — 기존 `signatureId` 검증만 영향 0 | IT 0 회귀 |
| partner-service / user-service / 기타 service | 변경 0 | `gradle test` PASS |

## 8. 가드 체크리스트 결과

- [x] worktree origin/main 동기화 (HEAD `4b2c077 Merge PR #98`)
- [x] 임시 브랜치 push 패턴 회피
- [x] 단계적 commit (5+ commit, push 자주)
- [x] slip-service 회귀 검증 (Phase 6 IT 11 case 회귀 사례 학습 — 신중)
- [x] arologis-service test PASS
- [x] partner-service / user-service 회귀 0
- [x] BaseEntity 7 audit + Soft Delete 일관 (Slip / SlipSignatureAudit 보존)
- [x] VARCHAR(20) only / partial unique index (V10 partial 2종)
- [x] UUID 사용자 비공개 (응답 schema 에 slipNo / driverCode 노출, UUID 는 호출자 내부 상태)
- [x] 한국어 Javadoc + dev-report (본 문서)
- [x] IT 외부 client `@MockBean` 격리 (SlipClient mock + arologis 측 IT)
- [x] commit 메시지 한국어
- [x] PR 본문 가드 (개발책임자 멘트 0)
- [x] body-file UTF-8 (BOM 없음 — Write tool 사용)
- [x] docs 동기화 9 영역 (README + ROADMAP + DECISIONS + readiness + dev-report + 환경변수 + QA)
- [x] InternalTokenFilter `/internal/**` prefix 한정 (slip-service 신규)
- [x] 사용자 가드 적용 (W10-5 위임 거의 0)
- [x] W10-3 F-3 backlog 채택 (ApiResponse wrapper IT 의무화)
- [x] Flyway out-of-order 비활성 일관 (V10 = V9 + 1)
- [x] SAMHAN_AROLOGIS_CLIENT_SKELETON_MODE=false (W10-4 시점 활성, env-templates 갱신)

## 9. 5 reviewer 토론 종합 채택 13 fix 매트릭스 (PR #99 추가 commit)

본 PR 발행 후 5 reviewer 코멘트 토론 종합 시점에 사용자 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지", 2026-05-07) 일관 적용 → 13 fix 모두 본 PR 채택. DV-3 (6 service refactor) 만 W10-5/Phase 11 위임.

| 그룹 | fix | 요약 | 적용 위치 | 검증 |
|---|---|---|---|---|
| A 핵심 backend | BE-1 | SlipResolver 실 활성 + slip-service `/by-partner-code/{code}/recent` endpoint | slip + arologis | IT 3 case + happy-path IT |
| A 핵심 backend | QA-1 | SlipClient unit test 6 case (skeleton/200/null/5xx) | arologis test | `MockRestServiceServer` PASS |
| A 핵심 backend | QA-2 | SignatureIntegrationIT happy-path (slipBridged=true) | arologis test | IT PASS |
| A 핵심 backend | DV-1 | SlipClient + PartnerInternalClient connect 2s / read 3s timeout | SlipClient + slip PartnerInternalClient | Spring Boot 3.4 표준 |
| B Designer | D-1 | QA 캡처 3종 재작성 — 토큰 1:1 (W3+W4+W5+post-W5+W10-1) | docs/qa/phase10-step-4-* | PNG >=100KB (177/158/176 KB) |
| B Designer | D-2 | signature flow 2x2 직교 매트릭스 — source x store, slice-accent 3 path | 2-signature-flow-diagram | 4 cell 명시 |
| B Designer | D-3 | HTML 원본 3종 + Edge headless 캡처 | docs/qa | 3 짝 모두 commit |
| C micro | BE-2 | UTF-8 charset 명시 (한글 imageRef 회귀 가드) | SlipSignatureService | `getBytes(StandardCharsets.UTF_8)` |
| C micro | BE-3 | Slip 생성자 signatureSource init 명시 (NULL INSERT 가드) | Slip domain | private 생성자 보강 |
| D FE | FE-2 | mobile-staff fallback 제거 + assertApiResponseSuccess | arologis.ts | 3 endpoint 일관 |
| D FE | FE-3 | DriverSignatureScreen slipBridged UX 시각화 (slice-accent badge) | DriverSignatureScreen.tsx | typecheck PASS |
| E docs | QA-3 | Phase 11 cutover 진입 backlog 등록 | M-PHASE-11-readiness §5-1-3 | signature_source 분류 / Grafana / SLA |
| E docs | DV-2 | Flyway V10 운영 lock 영향 분석 명시 | M-PHASE-11-readiness §5-1-3 | ADD COLUMN / CREATE INDEX 영향 |
| F DECISIONS | D-P10-13 | SlipResolver 실 활성 + by-partner-code endpoint 결정 | DECISIONS.md | 신규 §D-P10-13 |
| F DECISIONS | D-P10-14 | SlipClient connect/read timeout 결정 | DECISIONS.md | 신규 §D-P10-14 |

### 후속 위임 (Phase 11 또는 W10-5)

- **DV-3** (6 service refactor) — RestClient builder 공통화 (slip-service + arologis-service + future services). 본 PR scope 외 — Phase 11 cutover 진입 시점 또는 W10-5 별도 슬라이스에서 처리.

### 회귀 영향 (5 reviewer fix 후)

| 영역 | 결과 | 비고 |
|---|---|---|
| slip-service compileJava + compileTestJava | PASS | PartnerInternalClient + IT 3 case 신규 |
| arologis-service compileJava + compileTestJava | PASS | SlipClientTest 6 case 신규 + SignatureIntegrationIT 4 case |
| mobile-staff `pnpm typecheck` | PASS | FE-2 schema assert + FE-3 slipBridged UX |
| partner-service / user-service | 0 회귀 | 기존 endpoint 재사용만 |
