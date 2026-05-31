## qa-tester 사이클 3 리뷰 (head `0bd91830`)

### 사이클 2 QA 잔존 해소 표

| 잔존 | 해소 | 근거 |
|---|---|---|
| QA2-P2-01 AlreadyConverted 첫 요청 body 단언 | 해소 | testFromEstimateAlreadyConvertedReturns409 L128-133 orderNumber/lines/status=DRAFT 3 jsonPath 단언 |
| QA2-P2-02 dev-report §6 IT 11 / PNG 5 | 해소 | §6 표 "신규 IT 11건 (Delete 6 + FromEstimate 5)" / "5 PNG" 정합 |
| QA2-P2-02 §4 D6/C5 케이스 | 해소 | testDeleteCanceledOrderReturns422 + testFromEstimateSuccessRecordsAuditLog 명시 |
| Codex QA2-P2-03 §2/§8 actor 정책 | 해소 | §2 `resolveActorName(actorName)` 전달 / §8 `'영업담당자'` IT 검증 |

### IT 11 / Playwright 5 회귀

- PartnerOrderDeleteIT @Test 6건 + PartnerOrderFromEstimateIT @Test 5건 = 11건. dev-report 수치 일치.
- `createFromEstimate` `order.status = DRAFT` (L169) + `order.confirmedAt = null` (L171) 명시. 첫 요청 `"DRAFT"` 기대값 정합.
- PNG 5장 비어 있지 않음 (19~25 KB). 회귀 없음.

### dev-report 수치 정합

| 항목 | dev-report | 실제 |
|---|---|---|
| IT 건수 | 11 (D6+C5) | 11 |
| PNG 수 | 5 | 5 |
| resolveActorName | §2/§8 기술 | PartnerOrderDeleteService L52 |
| CALLER_ROLE_HEADER | §2 P2-2 | HttpHeaderConstants L15 |
| successBanner color | Designer P1 | sales.module.css L999 |

### 사이클 3 신규 발견

신규 P0/P1 결함 0건.

Nit (후속 슬라이스):
- QA-Nit-02 미해소 (resolveActorName Javadoc — 사이클 2.5 skip)
- PNG 03 주문번호 `2026/05/17-3` 노출 — UUID 미노출 확인. 한국어 라벨 치환 정상.
- PNG 01 "확정 처리중" 배지 — CONFIRMING 상태 삭제 시나리오 mock, 결함 아님.

### 종합

APPROVE. 사이클 2.5 fix 4건 전부 정합. IT 11 / PNG 5 / actor 정책 / 대비비 토큰 이상 없음.

**qa-tester agent — 2026-05-17**
