---
name: external-integration-research
description: 외부 연동 — 전자(세금)계산서 = 홈택스 일괄 엑셀(✅ 이미 구현·머지됨, 직접/ASP 불요) + 법인계좌 입출금 실시간 연동(진행 중). 개발책임자 2026-06-17
metadata:
  type: project
---

2026-06-17 개발책임자 딥리서치 요청(우리 = 하루 수백건 발급 고물량 사업자).

## 전자(세금)계산서 발급 — 자체 vs ASP (완료)
**결론: 고물량이어도 ASP(발급대행) 권고**(총비용+리스크). 자체 직접발급은 제도적 가능하나 ① 국세청 표준인증+시스템사업자 등록(고시 2023-17호) ② 공동인증서 전자서명(PKI) 구현=핵심난관 ③ 익일 전송 의무 ④ 가산세(지연 0.3%/미전송 0.5% 공급가액) 책임을 전부 자체부담. ASP=공개 200~220원/건(국세청 전송 포함, 고물량 대량계약 <50원/건 추정·실견정 필요), Java API 즉시 연동(바로빌 등). 정정: 스마트빌=비즈니스온(더존 아님), 더존=Bill36524. 상세 `docs/research/2026-06-17-etax-invoice-nts-vs-asp.md`. 우리 시스템은 이미 세금계산서 문서 생성/출력 보유([[project_company_config_menu]]).
**🚩 개발책임자 결정 2026-06-17: 직접연동·ASP 모두 폐기 → "홈택스 일괄 업로드용 엑셀 생성" 방식** (기존 GAS 처럼; ASP 수수료 + PKI/익일전송 부담 회피, 누군가 홈택스에 일괄 업로드하는 반자동 단계 수용). **양식은 이미 레거시 GAS `tools/legacy-gas/계산서일괄등록양식 생성/` 에 존재**(홈택스 일괄 업로드 엑셀, 100건 단위 분할 — 종류/작성일자/공급자·공급받는자 정보/공급가액·세액/품목1~4(일자·품목·규격·수량·단가·공급가액·세액·비고)/현금·수표·어음·외상미수금·영수청구 컬럼). **✅ 검증(2026-06-17 개발책임자 지적 → 확인): 이미 완전 구현·머지됨** (PR #161 + PR-E2 BE-A11, 신규 작업 불요). 스택:
- BE `accounting-service`: `HometaxExportService`(상수 `HOMTAX_HEADERS_59` = GAS `HEADER_LIST` **바이트 동일** + 100건 분할 + 제외거래처 CRUD + 저장이력 + gzip스냅샷), 컨트롤러 `/accounting/hometax-export/{preview,{batchId}/split,exclusions,history}` + legacy `/accounting/tax-invoice/hometax-export`(12컬럼), 배치 도메인 `TaxInvoiceBatch(Exclusion/Status)`, 테스트 `HometaxExportServiceTest`/`HometaxExportPreviewIT`/`TaxInvoiceBatchIT/EndToEndIT`.
- **출고전표 기반**: `previewBatch()` → `slipQueryClient.fetchAllSalesRows(from,to)`(=판매/출고전표 조회) → `toHomtaxRow` 59컬럼 매핑(GAS `runProcess()` 동등). `buildHomtaxXlsx`=GAS `exportToExcel()` 동등(안내문 1~5행·황/적/녹 헤더색·항목설명/올바른예시/잘못된예시 시트).
- FE `clients/desktop`: 사이드바 메뉴 "**홈택스 일괄 양식**"(`AppLayout.tsx`) → `HometaxExportPage`(`/accounting/hometax-export`, 4탭) + `api/hometaxExportApi.ts` + mock.
- 직접발급 stub(휴면): `ETaxClient/ETaxClientImpl/TaxInvoiceEmitService/EmitNtsResponse` + `TaxInvoice.eTaxExternalId`(예약컬럼)/`markEmitted`(SP-09-1) — 개발책임자 결정으로 미사용.
- 결론: **현행 Excel 방식 유지**(추가 개발 0). 백로그 #12 폐기. ([[replaces-ecount-gas-was-exporter]] 사례)

## 법인계좌 입출금 실시간 연동 (진행 중)
이카운트 유사 기능(다수 법인계좌 입출금내역 조회 → 거래처 매칭 → 거래원장 자동반영) 자체구축 시 채널(오픈뱅킹/펌뱅킹/스크래핑 CODEF/마이데이터)별 비용·조회한도·실시간성 + 다계좌 고빈도 저비용 방식 딥리서치 진행 중(완료 시 결론 추가).
