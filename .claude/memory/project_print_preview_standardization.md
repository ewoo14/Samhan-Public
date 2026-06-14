---
name: project-print-preview-standardization
description: 미리보기 표준화 방향 — 전표=전표양식(입고=출고통일)/견적=GAS 종합견적서/결재문서=PrintLayout 골격. 전표번호 0제거(전역+저장, 시드100건 마이그). 슬라이스1 머지 #481
metadata:
  type: project
---

2026-06-14 개발책임자 방향 (대화 3차 정정, 미리보기 슬라이스1 머지 PR #481 `8544a76df`).

**문서별 양식 매핑 (절대 혼동 금지)** — "미리보기 표준화" = PrintLayout 결재문서 형식 통일이 아니라 문서 성격별 양식:
- **전표(입고/출고)** = 전표 양식. **입고전표 = 출고전표(OutboundView) 통일**(A4 기본 + 88mm 토글, 결재란 미적용, `.inbound-*`/`.outbound-*` CSS 공통 selector, 입고창고 헤더 1회).
- **견적서** = **GAS 종합견적서 양식**(`tools/legacy-gas/종합견적서/index.html` 19182줄: 로고+제목+품목표+합계+안내문구4줄, 기본/세트상세 2종). 견적서(기본/세트상세)는 **스냅샷 저장 + 웹 종합견적서 재로드** 목적 → 종합견적서 에픽(데이터 모델부터).
- **결재문서(그룹웨어 결재·품의서 등)** = **PrintLayout 결재 골격**(제목/결재란[작성자]/내용/첨부/인사말). `approvalDoc`/`docHeader`/`approvalSteps`/`closingNote`/`print-approval-*` + DESIGN.md + tokens 보존(슬라이스1 미렌더 스캐폴드, mock gate 보존 검증) → 결재문서 후속 에픽.
- 거래명세서·세금계산서 = 현행.

**전표번호 0제거 (개발책임자 '전역+저장 모두')**: `YYYY/MM/DD-{번호}` 의 번호 앞자리 0 제거(`001→1`). 실 DB `slip_db.slips.slip_no` — 운영 1924건 no-pad(`2026/06/08-1908`), **시드 100건만 zero-pad(`001`)**. BE 생성기(SlipNumberService/EstimateNumberService/JournalNumberService/PartnerOrderConfirmService/taskCode) **이미 no-pad**, taxInvoiceNo만 4자리(법정). → ① seeder zero-pad 제거 ② 시드 100건 Flyway 마이그(`001→1`, unique 충돌 검증 + fresh Postgres probe) + 참조(partner_orders.slip_no, serial_compensation_failures) ③ FE 전역(저장 1→목록/상세/검색 자동). 미리보기 표시 = `clients/desktop/src/renderer/utils/orderNo.ts` `stripSlipNoZeros` (print 뷰 9종, mock gate 계약). [[feedback_slip_order_number_format]].

**견적 인쇄 진입 버그(origin 선재)**: `EstimateDetailPage.handlePrint` 이 estimateNo(슬래시)를 path 전달 → encodeURIComponent %2F → 게이트웨이 StrictHttpFirewall 400. 종합견적서 에픽에서 견적 전면 재작업 시 해결.

워크플로우 [[temp-multimodel-workflow]]. 인쇄 양식 iteration [[feedback_print_design_iteration]]. desktop 단위 러너 부재 → orderNo.test.ts CI 미연동(mock gate 우회), desktop vitest 정식 도입 후속.
