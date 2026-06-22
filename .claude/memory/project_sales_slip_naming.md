# 판매전표 명칭 (출고 SLIP_OUTBOUND 사용자 노출명)

> 2026-06-22 개발책임자 확정. PR #560 머지(슬1).

출고 전표(`SLIP_OUTBOUND`)의 **사용자 노출 명칭 = "판매전표"** (판매조회에서 조회 가능한 전표). 기존 "출고전표"·"작업지시서" 표기를 판매 도메인(판매/주문/견적/대시보드) 전반에서 "판매전표"로 통일.

- **기술 키 불변**: `SLIP_OUTBOUND` documentType·식별자·주석·CSS 클래스·groupware `ApprovalReferenceDocType`('출고전표')·BE 는 그대로. **사용자 노출 텍스트만** 변경(라벨≠식별자, [[feedback_jeonpyo_not_slip]] 식).
- **입고는 "입고전표" 유지**(isOutbound 게이트). 비대칭(판매 vs 입고) 수용.
- **양식 통일**: 출고 인쇄 = `DispatchView`(작업지시서 양식, 금액 없음, 결재란 5칸) 단일 = "판매전표". `OutboundView`(금액 단 "출고전표")는 **폐기**(거래명세서와 역할 중복). 거래명세서(`/print/invoice`=SalesInvoicePrintPage)·세금계산서 별도.
- **print-renderer**(`PrintRendererApp.tsx`, 헤드리스 사본 합성)는 OutboundView 금액 레이아웃 자체복제 → **슬3에서 판매전표 양식 재타깃 예정**(슬1 비범위).
- 범위외 유지: groupware 결재 문서참조 picker 라벨(`slipSearch.ts`/`documentReferenceSearch.ts`)·accounting mock memo.

상위 에픽 = 동적 결재라인 + 설정=결재란 진실원([spec](../../docs/superpowers/specs/2026-06-22-dynamic-approval-line-config-rendering-design.md)).
