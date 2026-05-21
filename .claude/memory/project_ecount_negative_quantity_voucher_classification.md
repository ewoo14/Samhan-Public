---
name: ecount-negative-quantity-voucher-classification
description: 이카운트 마이너스 수량 처리 + 전표 자동 구분 규칙 (2026-05-21 사용자 명시, MIG-22 머지 후). 판매전표 음수 = 입고 / 구매전표 음수 = 출고 / 전표명 회차·판매·차용·대여·반납 자동 분류 필요. AWS 배포 후 전체 마이그레이션.
metadata:
  type: project
---

# 이카운트 마이너스 수량 처리 + 전표 자동 구분 (2026-05-21 사용자 명시)

> 사용자 명시 (2026-05-21, MIG-22 진행 중): 
> - 이카운트 **판매(출고)전표에 마이너스 수량 = 실제 입고됨**
> - **구매(입고)전표에 마이너스 수량 = 실제 출고됨**
> - 우리 시스템은 **마이너스 수량 사용 X** — 부호 정정 + 전표 방향 전환 의무
> - 전표명에 **회차 / 판매 / 차용 / 대여 / 반납** 등 적혀있음 — 자동 분류 필요
> - 현재는 **샘플 데이터만** — AWS 배포 후 전체 데이터 마이그레이션 예정

## 핵심 규칙

### 1. 마이너스 수량 처리 (방향 전환)

| 이카운트 raw | quantity | 우리 시스템 변환 |
|---|---|---|
| 판매전표 | 양수 (예: +10) | 출고 (sales/outbound) +10 |
| 판매전표 | **음수 (예: -5)** | **입고 (purchase/inbound) +5 (방향 전환 + abs)** |
| 구매전표 | 양수 (예: +10) | 입고 (purchase/inbound) +10 |
| 구매전표 | **음수 (예: -5)** | **출고 (sales/outbound) +5 (방향 전환 + abs)** |

**의무**:
- 모든 importer/transform service 가 quantity 부호 검사 + 절대값 + 전표 종류 swap
- ProductInventoryService / OrderLine / TaxInvoiceLine / SalesAccountingSlipLine / PurchaseAccountingSlipLine 모두 적용
- 변환 staging 에 원본 부호 보존 (audit), 도메인은 양수 + 방향 enum 만

### 2. 전표 자동 구분 (kind classification)

전표 description/적요 의 키워드 매칭:

| 키워드 | TransactionKind | 비고 |
|---|---|---|
| **판매** | SALES | 일반 매출 |
| **회차** | RECYCLE | 재활용/순환 |
| **차용** | LOAN | 차용 (반환 의무) |
| **대여** | RENTAL | 대여 (반환 의무) |
| **반납** | RETURN | 반납 (LOAN/RENTAL 의 역방향) |
| (없음) | DEFAULT | 일반 |

**의무**:
- 전표 importer 가 적요/transactionType/description 정규식 매칭 후 kind enum 자동 설정
- 매칭 실패 시 DEFAULT + warning log (운영자 확인)
- LOAN/RENTAL/RETURN 은 향후 별도 도메인 (대여 추적) 필요 — MIG-N+ 후속

### 3. 현재 상태 (2026-05-21)

- **샘플 데이터** 만 import 완료 (`docs/migration/ecount-data/raw/`)
- AWS 배포 후 **전체 데이터 마이그레이션** 예정
- MIG-22 까지 본 규칙 미적용 — **MIG-23+ 후속 슬라이스 의무**:
  - MIG-23: 마이너스 수량 부호 정정 + 방향 전환
  - MIG-24: 전표 kind 자동 분류 (정규식 매칭)
  - MIG-25+: 대여/반납 추적 도메인

## How to apply (MIG-23+)

각 importer/transform service:

```java
// 마이너스 수량 정정
BigDecimal rawQty = parseQuantity(row.get("수량"));
boolean isInbound = (rawQty.signum() < 0) ^ "판매전표".equals(voucherType);
BigDecimal absQty = rawQty.abs();
// → CashDisbursement/Receipt 방향 swap 또는 OrderLine direction enum

// 전표 kind 자동 분류
String description = row.get("적요");
TransactionKind kind = classifyKind(description);  // 정규식 매칭
```

`TransactionKind` enum 신규:
```java
public enum TransactionKind {
    SALES,    // 판매
    RECYCLE,  // 회차
    LOAN,     // 차용
    RENTAL,   // 대여
    RETURN,   // 반납
    DEFAULT
}
```

## 관련 메모리

- [[ecount-product-identity-rule]] — 품목 식별 (alias)
- [[korean-accounting]] — 한국 회계 표준
- [[build-conventions]] — soft-delete + audit

## 운영 영향

AWS 배포 후 전체 데이터 마이그레이션 시 본 규칙 미적용 시:
- 마이너스 수량 reject 또는 잘못된 방향 저장 → 재고 불일치
- 전표 kind 미분류 → DEFAULT 만 사용, 차용/대여/반납 추적 불가
- **운영 critical** — MIG-23+ 전 절대 production migration 금지
