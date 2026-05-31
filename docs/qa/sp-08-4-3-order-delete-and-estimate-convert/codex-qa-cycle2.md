## Codex qa-tester 사이클 2 리뷰 (head `d6364d4b`)

### Codex 사이클 1 자체 발견 추적
- Playwright 정적 계약 잔존: non-blocker.
- 409 reload 후 재저장 E2E: non-blocker.
- `FROM_ESTIMATE` audit IT: fixed. `testFromEstimateSuccessRecordsAuditLog` 가 `field_name='FROM_ESTIMATE'`, `new_value='견적-2026-0001'`, `actor_name='영업담당자'` 검증.

### Claude QA 사이클 2 발견 평가
- **QA2-P2-01 valid**. `PartnerOrderFromEstimateIT.java:128` 첫 요청 `201 Created` 만 단언, 생성 body/orderNumber/lines/status 미고정.
- **QA2-P2-02 valid**. dev-report §6 `신규 IT 9건`, `4 PNG` 잔존. 실제 Delete 6 + FromEstimate 5 = 11건, PNG 5장.
- **Nit-01 invalid**. `02-delete-success.png` 에 `2026/05/17-2` 부터 표시 + `삭제된 2026/05/17-1 미노출` 문구 — soft delete 목록 제외와 정합.
- **Nit-02 valid nit**. `resolveActorName` private fallback 동작상 문제 없으나 `actorName` 공백 시 `system` 저장 정책 Javadoc 권장.

### Codex 신규 발견 (사이클 2)
- **QA2-P2-03**: dev-report actor 정책 stale. `docs/dev-reports/...md` §2/§8 `system-partner-order-delete` 고정이라고 기재, 현재 코드는 `resolveActorName(actorName)` 사용, IT 도 `deleted_by='영업담당자'` 기대. 문서가 실제 구현과 반대.

### 종합
조건부 APPROVE 유지. 차단급 기능 결함 없고 IT 11 구조 + PNG 5장 증적 정합. 머지 전 최소 수정 3건: AlreadyConverted 첫 요청 body 단언, dev-report IT/PNG 수치 갱신, dev-report soft delete actor 정책 갱신.

**Codex QA-agent — 2026-05-17**
