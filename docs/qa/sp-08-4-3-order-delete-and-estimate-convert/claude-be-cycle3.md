## backend-engineer 사이클 3 리뷰 (head `0bd91830`)

### 사이클 2 BE 결함 해소 표

| 번호 | 내용 | 상태 |
|------|------|------|
| P2-1 | `createFromEstimate` status=DRAFT + confirmedAt=null | 해소 — L169 `order.status = DRAFT`, L171 `order.confirmedAt = null` 명시 |
| P2-2 | `HttpHeaderConstants.CALLER_ROLE_HEADER` 추가 | 해소 — L15 추가 확인 |
| P2-2 | `HeaderAuthenticationFilter` 상수 치환 | **부분 해소** — `USER_ROLE_HEADER` 는 `HttpHeaderConstants.CALLER_ROLE_HEADER` 로 치환, `USER_ID_HEADER` (L25) 는 여전히 지역 리터럴 `"X-User-Id"` 잔류 |
| QA2-P2-01 | `testFromEstimateSuccess` DRAFT 기대값 | 해소 — L95 `value("DRAFT")` |
| QA2-P2-01 | `testFromEstimateAlreadyConverted` 첫 요청 body 단언 | 해소 — L131-133 lines/status 단언 |

### 사이클 3 신규 발견

**P3-1 (Low)**: `HeaderAuthenticationFilter` `USER_ID_HEADER = "X-User-Id"` (L25) 지역 리터럴 잔존. `HttpHeaderConstants.CALLER_ID_HEADER` 와 값 동일 — 동작 영향 없음. 상수화 일관성 위해 교체 1줄.

**P3-2 (Info)**: IT @Test 합산 44 case (AbstractPostgresIT 제외 10 파일). 사이클 2 기준 "IT 11 회귀 없음" 과의 기준 불일치 없으나, `PartnerOrderBootstrapIT` 1 case ApplicationContextLoad — 실질 회귀 coverage 43.

### 종합

P2 결함 5건 중 4건 완전 해소, 1건 부분. P3-1 Low 정합성. 단독 사이클 4 임계치 미달.

**APPROVE 조건부** — P3-1 1줄 교체로 즉시 머지 가능. 별도 사이클 4 불필요.

**backend-engineer agent — 2026-05-17**
