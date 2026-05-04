# Slice B (배송기사 배치 링크 + 자동 SMS) Plan — v2 재설계

> **작성**: 2026-05-05 PM Claude (Plan agent + 사용자 결정 반영).
> **상태**: 결정 완료, 다음 단계 Designer agent 디스패치.
> **PR 후보**: PR #22.

본 슬라이스는 **출고 배송 기사에게 배치 단위로 1건의 SMS 자동 발송 → 기사가 모바일 링크로 인수자에게 서명 받음 → 서명 완료 슬립을 기사가 직접 인수자에게 공유**하는 워크플로 신설.

---

## 1. 사용자 흐름 (확정)

```
1. 관리자가 출고전표 N건 발급 (각 전표에 driverName/driverPhone 입력 — DRAFT/SAVED 단계)
2. 시스템이 같은 driverPhone + 같은 배송일 슬립을 자동 그룹 → DeliveryBatch 1건 + 단일 토큰
   (관리자가 분리/병합 가능)
3. 사이드바 "링크발송" 메뉴 → 관리자 수동 클릭 [SMS 발송]
   → Solapi SMS 1건 → 기사: "오늘 배송 N건: sign.samhan-air.com/d/<batchToken>"
4. 기사 모바일 접속 → 슬립 N건 리스트 → 각 인수자에게 서명 받음 (Slice C)
5. 서명 완료된 전표 → 기사가 [인수자에게 공유] 버튼 → Web Share API
   → 폰의 카톡/SMS 앱 열려 인수자에게 직접 전달 (시스템 미경유)
```

---

## 2. 결정 사항 (사용자 확정)

| ID | 결정 |
| --- | --- |
| N1 | SMS 발송 트리거 — **관리자 수동 클릭** (오발송 방지) |
| N2 | 그룹화 단위 — **driverPhone + batchDate 자동** + 관리자가 분리/병합 가능 |
| N3 | SMS 게이트웨이 — **Solapi** (월 ~5천원 추정, 단일 SDK) |
| N4 | 인수자 공유 — **Web Share API** + 폴백 clipboard 복사 |
| N5 | 토큰 유효기간 — **배송일 +1일 자동 만료** |

비용 추정: 기사 5~10명/일 × 배치 1~3건 = 월 150~450 SMS = 월 1.5~4.5천원.

---

## 3. 도메인 모델

### 3.1 Slip 확장 (slip-service, 기존)

| 신규 필드 | 타입 | 비고 |
| --- | --- | --- |
| driverName | VARCHAR(50) nullable | DRAFT/SAVED 단계 입력 |
| driverPhone | VARCHAR(20) nullable | E.164 정규화 (`010-XXXX-XXXX`) |
| deliveryBatchId | UUID nullable | DeliveryBatch FK |

기존 라이프사이클 메서드 (save/send/accept/process/inspect/complete/...) **무변경**. `editHeader()` 시그니처 4→6 args 확장 (driverName/driverPhone 추가).

### 3.2 DeliveryBatch (slip-service 신규)

| 필드 | 타입 | 비고 |
| --- | --- | --- |
| id | UUID | `@UuidGenerator` |
| batchToken | VARCHAR(64) | base64url, UNIQUE |
| driverName | VARCHAR(50) | 기사명 (그룹 키 — slip 의 driverName 과 동일) |
| driverPhone | VARCHAR(20) | 그룹 키 |
| batchDate | DATE | 그룹 키 |
| tokenExpiresAt | TIMESTAMP | batchDate + 1일 |
| smsSentAt | TIMESTAMP nullable | SMS 발송 완료 시점 |
| smsLastError | VARCHAR(500) nullable | Solapi 응답 에러 |
| BaseEntity 7 audit | — | 표준 |

UNIQUE constraint: `(driverPhone, batchDate)` 부분 인덱스 — 중복 그룹 방지 (`is_deleted = false` 한정).

### 3.3 라이프사이클 표 (Layer 4)

| 메서드 | from status | to status | 부수효과 |
| --- | --- | --- | --- |
| `DeliveryBatch.create(driver, date, slips)` | — | (initial) | batchToken 생성, tokenExpiresAt = date+1일 |
| `DeliveryBatch.markSmsSent()` | smsSentAt=null | smsSentAt=now | Solapi 호출 성공 후만 |
| `DeliveryBatch.markSmsFailed(error)` | smsSentAt=null | smsSentAt=null + smsLastError 기록 | 재시도는 사용자 재클릭 |
| `DeliveryBatch.addSlip(slip)` | unchanged | unchanged | slip.deliveryBatchId 갱신 |
| `DeliveryBatch.removeSlip(slip)` | unchanged | unchanged | slip.deliveryBatchId = null |

---

## 4. API 스펙

### 4.1 외부 REST (slip-service:8086, gateway 라우팅 `/api/slips/**` + `/api/delivery-batches/**`)

| 메서드 | Path | 권한 | 설명 |
| --- | --- | --- | --- |
| POST | `/delivery-batches/auto-group?date=YYYY-MM-DD` | MANAGER/MASTER | 해당 날짜의 driverPhone 별 슬립 자동 그룹 → 신규/기존 batch 반환 |
| GET | `/delivery-batches?date=&sent=` | MANAGER/MASTER | 배치 목록 (링크발송 화면용) |
| GET | `/delivery-batches/{id}` | MANAGER/MASTER | 단건 + 슬립 N건 |
| POST | `/delivery-batches/{id}/send-sms` | MANAGER/MASTER | Solapi SMS 발송 + smsSentAt 기록 |
| POST | `/delivery-batches/{id}/slips` | MANAGER/MASTER | 슬립 수동 추가 |
| DELETE | `/delivery-batches/{id}/slips/{slipId}` | MANAGER/MASTER | 슬립 수동 제거 |
| POST | `/delivery-batches/{id}/regenerate-token` | MANAGER/MASTER | 토큰 재발급 (만료 시) |

### 4.2 공개 모바일 endpoint (no auth, 토큰만 검증)

| 메서드 | Path | 설명 |
| --- | --- | --- |
| GET | `/public/batches/{token}` | 배치 + 슬립 N건 read-only (만료 시 410 GONE) |
| GET | `/public/batches/{token}/slips/{slipId}` | 슬립 단건 상세 (서명 페이지 진입) |
| POST | `/public/batches/{token}/slips/{slipId}/signature` | (Slice C 후속) 서명 PNG base64 + 인수자명 + timestamp |

API Gateway: `/public/**` 는 인증 우회 (API Gateway `SecurityConfig` 에 `/public/**` permitAll).

---

## 5. DB 스키마 (slip_db V3 + V4)

### 5.1 V3 — `V3__add_slip_driver_contact.sql`

```sql
ALTER TABLE slips ADD COLUMN driver_name  VARCHAR(50);
ALTER TABLE slips ADD COLUMN driver_phone VARCHAR(20);
ALTER TABLE slips ADD COLUMN delivery_batch_id UUID;
CREATE INDEX ix_slips_delivery_batch ON slips (delivery_batch_id) WHERE delivery_batch_id IS NOT NULL;
CREATE INDEX ix_slips_driver_phone_date ON slips (driver_phone, slip_date) WHERE driver_phone IS NOT NULL;
```

### 5.2 V4 — `V4__create_delivery_batches.sql`

```sql
CREATE TABLE delivery_batches (
    id UUID PRIMARY KEY,
    batch_token VARCHAR(64) NOT NULL,
    driver_name VARCHAR(50) NOT NULL,
    driver_phone VARCHAR(20) NOT NULL,
    batch_date DATE NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    sms_sent_at TIMESTAMP,
    sms_last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    modified_at TIMESTAMP,
    modified_by VARCHAR(50),
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(50),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_batch_token UNIQUE (batch_token)
);

CREATE UNIQUE INDEX uk_delivery_batches_driver_date
    ON delivery_batches (driver_phone, batch_date)
    WHERE is_deleted = FALSE;

ALTER TABLE slips
    ADD CONSTRAINT fk_slips_delivery_batch
    FOREIGN KEY (delivery_batch_id) REFERENCES delivery_batches(id);
```

`UNIQUE(driver_phone, batch_date)` partial index — 중복 그룹 방지.

---

## 6. Solapi 통합 (slip-service 내부)

추상화 인터페이스:
```java
public interface SmsGateway {
    SmsResult sendSms(String phone, String message);
}
```

구현체:
- `SolapiSmsGateway` — production
- `MockSmsGateway` — test/local (logging only)

환경변수 (`application-pgsql.yml`):
```yaml
app:
  sms:
    vendor: solapi
    api-key: ${SOLAPI_API_KEY:dev-key}
    api-secret: ${SOLAPI_API_SECRET:dev-secret}
    sender-phone: ${SOLAPI_SENDER_PHONE:01000000000}
    base-url: https://api.solapi.com
```

H2 local 프로파일 → MockSmsGateway 자동 활성. PgSQL 프로파일 → SolapiSmsGateway.

본 Slice B 는 별도 notification-service 신설 없음 — slip-service 안에 SMS 발송 로직 포함 (미니 구조). Phase 5 Notification Service 슬라이스에서 분리 가능.

---

## 7. FE 변경

### 7.1 사이드바 메뉴 (재고이동 아래)

```
판매조회
구매조회
재고이동
└─ 링크발송          ← 신규
```

### 7.2 LinkDispatchListPage (`/sales/link-dispatch` 또는 `/inventory/link-dispatch`)

```
| 배송일 | 기사명 | 기사 연락처 | 슬립 수 | 링크 | SMS 발송완료 |
| 2026/05/05 | 김기사 | 010-XXXX-1234 | 3건 | [복사] | ☑ (14:32) |
| 2026/05/05 | 박기사 | 010-XXXX-5678 | 1건 | [복사] | ☐ [SMS 발송] |
```

- 행 클릭 → BatchDetailModal (슬립 N건 리스트 + 슬립 추가/제거)
- [복사] → clipboard 에 `sign.samhan-air.com/d/<token>` 복사
- [SMS 발송] → POST `/delivery-batches/{id}/send-sms` → 성공 시 ☑ + smsSentAt 표시
- 발송완료 ☑ 셀 클릭 → 재발송 confirm dialog (오작동 방지)

상단 [날짜 자동 그룹] 버튼 → POST `/delivery-batches/auto-group?date=오늘` → 미배치 슬립 그룹화.

### 7.3 SlipFormPage / SlipDetailPage

- 헤더에 driverName + driverPhone 2 필드 추가 (DRAFT/SAVED 만 편집)
- DispatchView 인쇄 — 기존 "용달기사" 결재란 셀에 driverName 자동 표시

### 7.4 신규 디자인 시스템 컴포넌트

- **PhoneInput** — 자동 하이픈 (`010-XXXX-XXXX`), maxLength 13, 한국 패턴 검증
- **CopyButton** — clipboard.writeText + 토스트 "복사됨"

### 7.5 공개 모바일 페이지 (Slice B 에서 골격, Slice C 에서 서명 캡처)

- 라우트: `sign.samhan-air.com/d/<token>` (Phase 4 web app deferred 면 단일 desktop 앱 mock)
- Slice B 에서는 **read-only** 슬립 리스트만 — 서명 캡처는 Slice C
- 기사용 [인수자에게 공유] 버튼 placeholder (Slice C 활성화)

---

## 8. 권한 모델

| 작업 | 권한 |
| --- | --- |
| 링크발송 메뉴 보기 | MANAGER / MASTER (관리자 대상 화면) |
| 자동 그룹 / 슬립 추가·제거 / SMS 발송 | MANAGER / MASTER |
| 공개 모바일 페이지 (`/public/...`) | NO AUTH (토큰만 검증) |
| 토큰 만료 후 접근 | 410 GONE |

---

## 9. 회귀 위험 평가

- slip-service 변경: `Slip.editHeader()` 시그니처 4→6 args 확장 — `SlipService.editHeader` 1곳 동기 수정.
- 도메인 라이프사이클 메서드 (save/send/accept/process/inspect/complete/...) **무변경** → 8개 라이프사이클 IT 시나리오 영향 없음.
- driverName/driverPhone/deliveryBatchId 모두 nullable → 기존 데이터 호환.
- DeliveryBatch 신규 — 기존 도메인 무영향.
- API Gateway `/public/**` 인증 우회 추가 — 라우팅 충돌 검토 의무 (`/public/health` 등 기존 path 없음 확인됨).
- Solapi SMS 호출 — `@MockBean SmsGateway` 로 IT 격리 가능.

---

## 10. UUID 비공개 가드

- LinkDispatchListPage 표에 표시: 기사명, 기사 연락처, 배송일, 슬립 번호 (UUID 노출 X)
- batchToken 은 base64url 64자 — UUID 와 다른 형식 (의미상 안전)
- 공개 모바일 페이지: 슬립 번호 + 거래처명 + 모델명/품목명 (UUID 미노출)

---

## 11. 단계별 sub-슬라이스 분해 (선택)

본 Slice B 는 다음 5-team parallel 디스패치 1회로 완성 가능 — sub-slice 미사용:
- BE: slip-service 확장 (driver fields + DeliveryBatch + Solapi 통합 + 9 endpoints + IT)
- Designer: PhoneInput + CopyButton spec + LinkDispatchListPage wireframe + 모바일 공개 페이지 wireframe
- FE: PhoneInput + CopyButton 컴포넌트 + LinkDispatchListPage + SlipForm/Detail driver 필드
- QA: 12개 IT 시나리오 + 8 캡처 (LinkDispatchList / 모바일 공개 / SMS mock 등)
- DevOps: 환경변수 + Solapi key 시크릿 (gitignore) + Flyway V3/V4 검증

---

## 12. 다음 단계

1. ✅ Open Questions 사용자 확정 (2026-05-05)
2. **현재**: Designer agent 단독 호출 (wireframe + spec 산출)
3. 5-team parallel 디스패치 (BE/FE/QA/DevOps + Designer 산출물 인용)
4. PM 통합 (Layer 1+2+3+4 사전 검증) → PR #22 발행
