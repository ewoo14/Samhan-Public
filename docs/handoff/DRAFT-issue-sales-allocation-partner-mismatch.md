## 배경

전역 입력 UX 에픽(단수/복수 카디널리티) 정찰 중 **본 에픽과 무관한 회계 무결성 결함**을 발견했다. 개발책임자 결정(2026-07-15): **별도 이슈로 등록 후 순차 처리**.

## 결함

**매출전표에 원천 출고전표를 배분할 때, 원천 전표의 거래처가 매출전표 헤더의 거래처와 일치하는지 검증하지 않는다.**

- `SalesAccountingSlipCreateAttemptService.verifySourceAndAllocation`(`services/accounting-service/.../SalesAccountingSlipCreateAttemptService.java:71-92`) 은 **slipType / status / 과할당** 만 확인한다.
- 더 근본적으로 `client/SlipLineSnapshot.java:20-30` 이 **`partnerId` 를 아예 실어오지 않아** 검증 자체가 **구조적으로 불가능**하다.

### 실패 시나리오
1. 거래처 **A** 로 출고전표를 발행(CONFIRMED)
2. 거래처 **B** 의 매출전표를 만들면서 그 출고전표를 배분
3. 검증이 없으므로 **통과** → 거래처 B 의 매출로 귀속
4. → 세금계산서·분개·일마감이 **전부 잘못된 거래처로 집계**

### 왜 중대한가
매출전표는 회계 반영 체인의 시작점이고, 뒤로 갈수록 `partner_id NOT NULL` 로 조여진다:
- 매출전표 `V18__add_sales_accounting_slips.sql:8` `partner_id UUID NOT NULL`
- 세금계산서 `V2__add_tax_invoice.sql:21` `NOT NULL`
- 분개 라인 `JournalLine.java:60-61` 라인당 스칼라
- 일마감 `V21__alter_daily_closings_add_kinds.sql:27-29` **`UNIQUE (closing_date, partner_id, closing_kind, source_kind)`** ← 거래처가 집계 UNIQUE 키

즉 **잘못 귀속된 매출은 마감·세금계산서까지 그대로 전파**되며, 사후 추적이 어렵다.

## 참고 — 이 repo 는 같은 규칙을 다른 곳에선 이미 지키고 있다
| 규칙 | 코드 | 테스트 |
|---|---|---|
| 병합해도 거래처는 단수 | `PartnerOrderMergeConvertService.java:123-127` → `"병합은 같은 거래처 주문만 가능합니다"` 409 | ✅ `PartnerOrderMergeConvertServiceTest.java:99` |
| 통장거래 N건→입금보고서 1건, 거래처 동일 강제 | `BankDepositReceiptService.java:150-155` | ✅ `BankDepositReceiptServiceTest` |
| 거래처 모호 시 거부 | `CashReceiptService.java:477-479` → `"조회 결과가 2건 이상입니다. 거래처코드로 다시 선택하세요"` | |

**패턴**: 복수 선택은 **원천 문서**(주문/통장거래/전표)에만 허용되고, **거래처 같은 귀속 키는 항상 단수**이며, 묶을 때는 **동일성 검증 후 1개로 수렴**시킨다. 매출전표 배분만 이 패턴에서 빠져 있다.

## 조치 (착수 시 설계)
1. `SlipLineSnapshot` 에 `partnerId` 를 실어온다(계약 확장 — slip-service `/internal` 응답 확인 필요).
2. `verifySourceAndAllocation` 에 **거래처 일치 검증** 추가 → 불일치 시 명확한 4xx(한국어 메시지).
3. **기존 데이터 조사**: 이미 거래처가 섞여 배분된 행이 운영 DB 에 있는지 확인하고, 있으면 정리 방안 별도 협의.
4. 회귀 테스트: 거래처 불일치 배분 거부 + 일치 시 정상 통과.

## 범위
`accounting-service` (+ 필요 시 `slip-service` 의 internal 스냅샷 계약). #809 와 무관.

## 민감도
🔴 **회계 무결성** — 착수 전 개발책임자 확인 필요 항목이 있으면 선확인.
