# Slice C (모바일 전자서명) Plan

> **작성**: 2026-05-05 PM Claude (Plan agent 산출 + 사용자 결정 대기).
> **상태**: Open Question 8건 사용자 확정 대기 → Designer agent 디스패치 예정.
> **PR 후보**: PR #23.

본 슬라이스는 **Slice B (배송기사 배치 링크) 위에 모바일 Canvas 서명 캡처 + SHA-256 해시 + 인수자 share 토큰 + DispatchView 인쇄 통합** 추가.

---

## 1. 핵심 결정 (Plan agent 권장)

### 1.1 도메인 모델 — C안 (Slip 확장 + audit 이력)
`Slip` 에 5필드 1:1 추가 + 별도 `slip_signature_audit` 이력 테이블 (감사용).

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| `signedAt` | TIMESTAMP nullable | 서명 시점 (서버 발급) |
| `signerName` | VARCHAR(50) nullable | 인수자명 (자유 입력) |
| `signaturePng` | BYTEA nullable | PNG 바이너리 (≤ 50KB) |
| `signatureHash` | VARCHAR(64) nullable | SHA-256 hex |
| `signatureChannel` | VARCHAR(20) nullable | MOBILE_CANVAS / PAPER_SCAN (확장) |
| `signatureShareToken` | VARCHAR(64) nullable | 인수자 share 토큰 (base64url, +30일 만료) |

**이유**: ① Slip 라이프사이클 대시보드 1쿼리 노출 ② 재서명·무효화 이력은 전자서명법 시행령 §17 무결성 입증 의무로 별도 테이블 필수 ③ B안 (1:N) 은 "어느 서명이 활성?" 컬럼 별도 필요해 복잡도 증가.

### 1.2 PNG 저장 — A안 (DB bytea)
초기 ≤1000건/월 × 평균 30KB = 월 30MB. PostgreSQL `bytea` 컬럼 + SHA-256 hash 컬럼. MinIO/S3 는 Phase 6 deferred (월 1만건 초과 시 마이그).

### 1.3 라이프사이클 표 (Layer 4 의무)

| 메서드 | from status | to status | 부수효과 |
| --- | --- | --- | --- |
| `Slip.recordSignature(signerName, png, hash)` | INSPECTING / COMPLETED / SHIPPING | unchanged | signedAt=now, 5필드 갱신 + audit log INSERT |
| `Slip.invalidateSignature(reason, by)` | signedAt!=null | unchanged | 5필드 NULL + audit log INSERT (action=INVALIDATE) |
| 기존 lifecycle 메서드 | — | — | **변경 없음** (서명은 라이프사이클 직교 메타) |

### 1.4 표준 전자서명 평가
KISA `전자서명법` (2020 개정) 기준 본 Slice C 는 "공인전자서명" 이 아닌 **"일반 전자서명"** — Canvas PNG + SHA-256 hash + 서버 timestamp + 토큰 추적성 = **민사상 추정 효력 (전자서명법 §3②)** 확보. PKI/공인인증/RFC 3161 TSA 는 Phase 6+ deferred. 본 Slice 는 "납품 인수 확인" 1차 효력으로 충분.

---

## 2. API 신규 4건

| 메서드 | Path | 권한 | 설명 |
| --- | --- | --- | --- |
| POST | `/public/batches/{token}/slips/{slipNo}/signature` | NO AUTH | body `{signerName, signaturePngBase64}` → 서명 저장 + shareToken 발급 |
| GET | `/public/signatures/{shareToken}` | NO AUTH | 인수자 view (read-only 슬립 + 서명 PNG, +30일 만료) |
| GET | `/api/slips/{id}/signature` | MANAGER/MASTER | 관리자 조회 |
| DELETE | `/api/slips/{id}/signature?reason=` | MASTER only | 무효화 (audit 강제) |

---

## 3. DB 스키마 (slip_db V5)

### 3.1 V5 — `V5__add_slip_signature.sql`

```sql
ALTER TABLE slips ADD COLUMN signed_at TIMESTAMP;
ALTER TABLE slips ADD COLUMN signer_name VARCHAR(50);
ALTER TABLE slips ADD COLUMN signature_png BYTEA;
ALTER TABLE slips ADD COLUMN signature_hash VARCHAR(64);
ALTER TABLE slips ADD COLUMN signature_channel VARCHAR(20);
ALTER TABLE slips ADD COLUMN signature_share_token VARCHAR(64);
ALTER TABLE slips ADD COLUMN signature_share_expires_at TIMESTAMP;

CREATE UNIQUE INDEX uk_slip_signature_share_token
    ON slips (signature_share_token)
    WHERE signature_share_token IS NOT NULL;

CREATE TABLE slip_signature_audit (
    id UUID PRIMARY KEY,
    slip_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL,  -- RECORD / INVALIDATE
    signer_name VARCHAR(50),
    signature_hash VARCHAR(64),
    reason VARCHAR(500),
    actor_user_id VARCHAR(50),    -- 공개 endpoint 시 NULL
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(50) NOT NULL
);

CREATE INDEX ix_signature_audit_slip ON slip_signature_audit (slip_id, created_at DESC);
```

---

## 4. FE 구조 — Slice B mini bundle 확장

### 4.1 Mobile 라우트 (sign.samhan-air.com 또는 mock)
- `/d/{token}` — 배치 슬립 리스트 (Slice B 구현 완료, 본 슬라이스에서 [상세보기 →] 활성)
- `/d/{token}/s/{slipNo}` — 서명 페이지 (NEW)
- `/share/{shareToken}` — 인수자 view (NEW)

### 4.2 Mobile mini bundle 추가 (≤6KB gzip)
- `signature.js` — vanilla canvas (signature_pad lib 미사용, 의존성 zero 유지)
  - touch + mouse 통합 이벤트
  - "다시 서명" / "서명 완료" 버튼 (disabled until non-empty)
- 디자인 시스템 컴포넌트 신규 X (mobile mini bundle 자체 완결)

### 4.3 DispatchView 인쇄 통합
- 기존 인수자 결재 셀에 `signaturePng` 있으면 `<img>` 렌더 (data URI)
- 서명 메타 (서명자명 + 시각) 작은 글씨로 셀 하단

### 4.4 SlipDetailPage (desktop 관리자)
- 서명 정보 카드 (서명자명, 시각, hash, channel) — 서명 완료 시
- "서명 무효화" 버튼 (MASTER only, confirm dialog + reason 입력)

---

## 5. 권한 모델

| 작업 | 권한 |
| --- | --- |
| 공개 서명 저장 (`POST /public/.../signature`) | NO AUTH (token 검증) |
| 공개 인수자 view (`GET /public/signatures/...`) | NO AUTH (shareToken 검증) |
| 관리자 서명 조회 | MANAGER / MASTER |
| 관리자 서명 무효화 | MASTER only |

---

## 6. 단계별 sub-슬라이스 분해

| 슬라이스 | 범위 | PR |
| --- | --- | --- |
| **C1+C2** (MVP) | BE 서명 저장 endpoint + Slip 확장 + V5 + Mobile 서명 페이지 + Canvas | PR #23 후보 |
| **C3** | 인수자 share 페이지 + Web Share API + 폴백 clipboard | PR #24 |
| **C4** | 관리자 서명 조회/무효화 + audit 로그 + DispatchView 인쇄 | PR #25 |

본 Slice C 는 **C1+C2 통합 진행 권장** (MVP — 서명 저장 + 모바일 페이지). C3/C4 별도 슬라이스.

---

## 7. Open Question — **사용자 확정 (2026-05-05)**

| Q | 결정 |
| --- | --- |
| Q1 도메인 모델 | **C안** (Slip 5필드 + audit 이력 테이블) |
| Q2 PNG 저장 | **A안** DB bytea — MinIO 마이그 트리거: 월 1만건 초과 |
| Q3 Slip status | **무변경** (서명은 라이프사이클 직교 메타) |
| Q4 인수자 share 만료 | **30일** (BE 영구 보존 + share token만 30일) |
| Q5 Web Share API | **`{title, text, url}` 3필드** (iOS/Android 호환성 ↑) |
| Q6 RFC 3161 TSA | **Phase 6 deferred** (일반 전자서명 1차 효력 충분) |
| Q7 무효화 권한 | **MASTER only + 감사 로그** (이메일 알림은 Phase 5) |
| Q8 sign.samhan-air.com | **현 슬라이스: desktop 앱 라우트 mock** + Phase 5 nginx 분리 |

---

## 8. 회귀 위험 평가
- DeliveryBatch / Slip 라이프사이클 메서드 **무변경**
- 신규 nullable 컬럼 7개 + audit 테이블 → 기존 데이터 호환
- PublicSlipController 신규 endpoint 2건 추가 (기존 GET 무영향)

---

## 9. 다음 단계
1. ✅ Open Questions 사용자 확정
2. Designer agent 단독 호출 (서명 페이지 + 인수자 view + DispatchView 인쇄 wireframe)
3. 5-team parallel 디스패치
4. PM 통합 → PR #23 발행
