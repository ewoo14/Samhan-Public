---
name: project-partner-order-status-model
description: 주문(partner-order) 상태 모델 업무용어 매핑. 진행중=DRAFT / 완료=CONFIRMED / 보류=신규 ON_HOLD. 리스트 기본필터=진행중. 보류 추가+필터는 별도 슬라이스.
metadata:
  type: project
---

# 주문(Partner-Order) 상태 모델 — 업무용어 매핑 (2026-05-30 개발책임자 확정)

## 원리
- 주문이 들어오면 **자동 '진행 중'** 상태.
- 이를 **출고전표(slip)로 전환하면서 '완료'** 상태로 변경.
- 주문서 리스트 창: 기본 **'진행 중'** 주문만 표시 + 상태 선택 필터(진행중/완료/보류)로 전환 조회.

## 코드 enum ↔ 업무용어 매핑

| 업무 용어 | 코드 `PartnerOrderStatus` | 편집/복원 | 비고 |
|---|---|---|---|
| **진행 중** | `DRAFT` | 편집·복원 가능 | 주문 진입 자동(createFromEstimate), 리스트 기본 필터 |
| **완료** | `CONFIRMED` | 복원 가능(slip 경고) | confirm = 출고전표 전환 시점 |
| **보류** | **`ON_HOLD`** (✅ Phase 2.5 구현 완료 — PartnerOrderHoldController hold/release + HoldService + QueryService 상태필터) | 편집·복원 가능 | "진행중에서 멈춘 편집가능 상태" |
| (전환 순간) | `CONFIRMING` | 불가 | advisory lock transient, 사용자 비노출, 복원 409 |
| (취소) | `CANCELED` | 불가 | 유지, 복원 409 |

## RESTORE 복원 가드 (Phase 2.4)
- 복원 허용 = **진행중(DRAFT) + 완료(CONFIRMED) + (추후)보류(ON_HOLD)**.
- 거부(409) = **CONFIRMING·CANCELED 만** (제외 목록 방식 → 보류 추가 시 자동 호환).
- CONFIRMED 복원 시 slip 자동 재발행 안 함 + `slipResyncRequired` 경고. slip 연동 필드(slipNo/slipPublishStatus/confirmedAt/slipPublishedAt) 역적용 제외.
- **삭제된 주문도 복원 가능** (개발책임자 2026-05-30): delete = DELETE revision 캡처(soft-delete 직전). 복원 조회는 soft-deleted 포함(@SQLRestriction 우회) → undelete + 시점 내용 적용. 권한은 기존 RESTORE 동일. revision_type 에 DELETE 추가.

## 별도 슬라이스 (Phase 2.4 RESTORE 와 분리 — 개발책임자 결정)
1. ~~보류(ON_HOLD) 상태 추가~~ — ✅ **Phase 2.5 구현 완료**(enum+HoldService hold/release+Controller POST /{id}/hold·/release 409 가드, edit 권한 재사용)
2. ~~주문 리스트 상태 필터~~ — ✅ **구현 완료**(PartnerOrderQueryService status 필터 DRAFT/CONFIRMED/ON_HOLD)
3. 주문→출고전표 전환 고도화([[project-order-slip-conversion]]) — 품목별 부분전환 + 다중주문 병합
※ 2026-06-20 "전부 권장대로" 결정 시 재확인: 6c ON_HOLD 도입=이미 완료(메모리 stale 정정). self-accept=금지 유지, restore=전체복원(현행), 수동품목 카테고리=카탈로그만(현행) 모두 현행 유지 확정.

## 관련
- [[project-order-slip-conversion]]
- Phase 2.4 spec: `docs/superpowers/specs/2026-05-30-partner-order-restore-version-history-design.md`
