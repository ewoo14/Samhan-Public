### Codex QA 사이클 1 2a 리뷰 (head `a29bc83e`)

#### Claude 발견 평가

| 항목 | Codex 평가 | 사유 |
|---|---|---|
| D-01 | PASS | `SlipDetailResponse.version` 노출 + `SlipDetailResponse.from()` 매핑 + IT `$.data.version=1` 단언 |
| D-02 | **FAIL** | dev-report §6 "8 tests" 정정됐으나 실 `SlipUpdateIT.java` `@Test` method **9개** (`testUpdateNonInboundForbidden` 포함). 9 로 되돌리거나 실 실행 로그 근거 필요 |
| D-03 | PASS | stale timestamp 의도 주석 추가 정적 검토 충분 |
| BE-6 | PASS | `extracting(...).containsOnly(1)` Integer == 박싱 제거 |

#### Codex 자체 신규 발견 (QA 영역)

- **MEDIUM (T1 stale)**: `clients/desktop/playwright/sp-08-5-2-purchase-slip-edit-put/sp-08-5-2-purchase-slip-edit-put.spec.ts:26` T1 spec 이 여전히 `service` 에 `slip.getSlipType() != SlipType.INBOUND` 문자열 기대. 1c BE-4 service guard 제거 → 도메인 메서드 위임 변경 → T1 fail 가능. **수정 권고**: 도메인 `Slip.updateHeader`/`replaceLines` INBOUND guard 또는 service Javadoc 기준 갱신.

- **LOW (T1 회귀 방지 미비)**: T1 1c 핵심 변경 (validateLines 외부 이동, `after = summarize(saved)`, ChronoUnit.MICROS truncation) 정적 단언 부재.

- **LOW (T2 `purchaseUpdatedAt`)**: T2 FE spec 이 `purchaseUpdatedAt` state 신설 직접 검증 누락. data-testid 필수 아님 — 회귀 방지 단언 추가 권고.

- **LOW (T3 `purchaseIsConflict`)**: T3 409 spec 이 boolean state 미검증, 메시지/handler 만 봄. 문자열 의존 회귀 방지 단언 권고.

- **LOW (T5 라인 add/remove)**: T5 신규 add/remove UX (`purchase-slip-edit-add-line`, `removePurchaseLine`) 검증 누락. 1c 신규 UX 라 Playwright case 또는 T2 확장 필요.

- **MEDIUM (PNG 02 mojibake)**: 재생성됐으나 한글 mojibake — QA evidence 부적합. UUID 노출 없음. 재생성 필요.

- **LOW (dev-report verification)**: §6 verification table 에 PNG 4장 + Spring test 수치만, Playwright 5 case 실행/정적 검증 결과 없음. Spring 수치도 9 tests 와 불일치.

#### 종합

사이클 2 필요. D-02 test count 실제와 반대로 정정 (9 → 8), T1 stale assertion (1c BE-4 후 fail 위험), PNG 02 mojibake — 3건 P1 우선순위. 나머지 LOW 는 옵션.
