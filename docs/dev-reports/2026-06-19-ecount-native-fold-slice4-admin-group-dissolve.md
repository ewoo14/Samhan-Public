# dev-report: 이카운트 네이티브 편입 슬4 — "회계 관리자" silo 그룹 해체 → 네이티브 메뉴 편입

> 2026-06-19 · PR #521 · 브랜치 `feat/ecount-native-fold-slice4` · 슬5(토글그룹 해체) 흡수
> 에픽: 이카운트 이관 자료 네이티브 편입 + "회계 관리자(MIG-14)" silo 폐기

## 1. 목표 (개발책임자 확정 — 2회 정정 반영)
에픽 원칙([[project-ecount-native-fold]]): **별도 메뉴(silo) 금지 — 기존 회계 메뉴 편입 + "회계 관리자" 삭제.** 슬1/슬2 가 aging/cash page-code 를 제거한 뒤 남은 "회계 관리자" 중첩 토글 그룹(원장대조·운영대시보드·회계수정요청·주문서관리)을 **완전 해체**하고 멤버를 네이티브 카테고리에 평면 편입한다.

## 2. 개발책임자 결정 경위
1. 1차: 슬4 격리 가드 → "메뉴만 이동, 롤 유지"(ACCOUNTANT/MANAGER/MASTER 유지).
2. 2차 정정: "신규 '마이그레이션 운영' 섹션 금지 — 기존 회계 메뉴에 편입하고 회계 관리자 삭제"(에픽 원칙 재확인). → 신규 섹션 철회, 회계 본류 flat 편입.
3. 3차 정정: "주문서 관리는 회계가 아니라 판매" → 주문 silo 는 판매 카테고리로(도메인 정합), 네이티브 '주문서 관리'와 구분 위해 "주문서 관리 (이관)" 라벨.

## 3. 변경 (FE-only)
- `components/AppLayout.tsx`: "회계 관리자" `SidebarGroupToggle` + `accountingAdminOpen` state + `showAccountingAdminGroup` 파생 제거(그룹 완전 해체).
  - `매출/매입 원장 대조`(ecount.mig14.ledger) + `운영 대시보드`(ecount.mig.ops-dashboard) + `회계 수정 요청`(accounting.edit-requests.decide) → **회계 카테고리 flat**.
  - `주문서 관리`(eCount 이관 주문 silo, /accounting/admin/orders, ecount.mig14.order-list) → **판매 카테고리 flat** + 라벨 **"주문서 관리 (이관)"**(네이티브 /sales/partner-orders "주문서 관리"와 구분). `showAccountingAdminOrder`를 `showAccounting`→`showSales` OR-체인으로 이동, activeTarget 동반 조정.
- QA: `menu-relocate/menu-ia-contract.spec.ts`(flat 편입·그룹 제거·"(이관)" 라벨 양방향 잠금) + slice1/2/4 real-qa spec 갱신, `admin-hr-guard` stale 주석 정리.

## 4. 불변 (cutover 전 폐기 금지)
route(`/accounting/admin/*`)·page-code·auth 마이그/RBAC seed·롤·BE 전부 **무변경**. 메뉴 IA 위치만 변경. 슬6 에서 주문 이식 시 "주문서 관리 (이관)" 링크/route 제거 예정.

## 5. 검증
- `npm run typecheck` + `vitest` 97 + `menu-ia-contract` 6 green
- **Docker 실QA**(dev_master): T1=원장대조(매출/매입)·운영·수정요청 회계 flat 편입 + 회계 관리자 그룹 토글 소멸, T2=주문서 관리 (이관) 판매 소속 — `docs/qa/ecount-fold-slice4/`
- 그룹/state/group-testid 잔여 0, slice1/2 그룹-expand 의존 제거(defect-family-sweep), "(이관)" 라벨 계약 박제(양방향)
- 듀얼 리뷰(Opus 3-agent + Codex) P1/P2 blocking 0(P2=라벨 계약 미박제 → 박제 보강), P3(README/주석/실QA 증빙) 해소

## 6. 후속
- 슬6(주문 partner_orders cross-service 이식, 대형): eCount orders/order_lines(MIG-8) → slip-service partner_orders. 이식 후 "주문서 관리 (이관)" 링크/route/page-code 제거. D1=slip partner_orders 확정.
- cutover(Phase11) 후: ledger 대조/운영 대시보드 최종 처리(D4), cash_*/MV 물리 DROP(D3).
