---
name: project_accounting_gh_decisions
description: 회계 보고 스위트 G(채권채무)·H(입출금매칭) 도메인 결정 — G=받을어음+수금계획 전부, H=CSV 먼저 후 KFTC
metadata:
  type: project
---

회계 보고 스위트 통일안 G·H 도메인 결정 (2026-06-23 개발책임자, A~F 완결 후).

**G 채권채무 — "수금계획까지 전부"**:
- 기존 PartnerAging 확장(direction=ALL 채권+채무·여신한도/미수·월별 aging 버킷)에 더해 **받을어음(어음) 도메인 신규**(만기·발행일·수취인·금액·상태) + **수금계획**(거래처별 예정 수금일/금액 입력 + 예측). 최대 범위.

**H 입출금매칭 — "CSV 먼저 → KFTC"**:
- **BankTransaction 도메인을 소스 무관(source-agnostic)으로 설계**. ①MVP=통장 거래내역 **CSV/엑셀 수동 import**→BankTransaction 적재→탭(전체/미반영/회계반영/강제)→거래처 수동지정(AsyncAutocomplete)→입출금보고서→거래처원장 POSTED 전기. ②후속=**KFTC 오픈뱅킹 실시간 입출금 조회** 연동(외부 인증/계약, [[external-integration-research]] 법인계좌 리서치 연장). 동일 BankTransaction 도메인에 소스만 추가.

**Why:** 개발책임자가 G/H 착수 전 도메인 결정 상의 요청 → 위 2결정. G는 신규 입력 도메인(어음·수금계획)이라 spec 선행, H는 BankTransaction을 소스 무관 추상화해 CSV→KFTC 단계 확장.

**How to apply:** A~F와 동일 패턴(읽기전용 보고서)이 아니라 **신규 쓰기 도메인**(어음/수금계획/BankTransaction)이라 Flyway 마이그레이션 + 도메인 엔티티 + 입력 UI 동반. brainstorming→spec→writing-plans→듀얼리뷰+라운드별 Docker 실QA. 참조 [[accounting-report-display-conventions]] [[uuid-no-user-visibility]] [[jpa-joinfetch-cartesian-dedup]] [[enum-expansion-check-constraint]].
