# 회계 보고 스위트 G·H 설계 — 채권채무(받을어음·수금계획) / 입출금매칭(BankTransaction)

> 2026-06-23. 회계 보고 스위트 A~F 완결 후 마지막 2 통일안. 개발책임자 결정([[project_accounting_gh_decisions]]): G=받을어음+수금계획 전부, H=CSV 먼저→KFTC.
> A~F와 달리 **신규 쓰기 도메인**(Flyway 마이그레이션 + 엔티티 + 입력 UI). 읽기전용 보고서 아님.

## 공통 원칙
- BaseEntity 7 audit + Soft Delete. UUID 미노출(거래처=partnerCode/명, [[uuid-no-user-visibility]]). 표시규약([[accounting-report-display-conventions]]: 음수 '-X' 빨강·0='—'·거래처코드(bizNo) 열·계정명 코드없음). enum 영속 시 CHECK 제약 동반([[enum-expansion-check-constraint]]). 적용 마이그레이션 불변([[applied-migration-immutable]]) — fresh Postgres probe 검증([[migration-fresh-postgres-probe]]). 듀얼리뷰(Opus+Codex)+라운드별 Docker 실QA.

---

## G — 채권채무 (받을어음·수금계획·aging)

### G-1. 받을어음 도메인 (notes_receivable)
- 엔티티 `NotesReceivable`: partnerId(UUID 내부)/partnerCode·name(표시), 어음번호, 발행일, 만기일, 금액, 종류(약속어음/환어음), 상태(BOARDING 보유/COLLECTING 추심/SETTLED 결제완료/DISHONORED 부도), 비고. Flyway VXX.
- 입력 UI: 받을어음 등록/목록(만기 임박 정렬·상태 필터). 거래처 AsyncAutocomplete.
- aging/채권 현황에 만기 기준 편입(만기 도래분 = 회수 예정).

### G-2. 수금계획 도메인 (collection_plan)
- 엔티티 `CollectionPlan`: partnerId/code·name, 예정 수금일, 예정 금액, 근거(외상매출 잔액/어음 만기/수동), 상태(PLANNED/COLLECTED/OVERDUE). Flyway VXX.
- 입력 UI: 거래처별 수금계획 입력(예정일/금액). 자동 제안(미수 잔액·어음 만기 기반)+수동 조정.
- 예측: 월별/주별 수금 예상 캐시플로 (수금계획 합산).

### G-3. 채권채무 현황 보고서 (PartnerAging 확장)
- 기존 PartnerAgingService 확장: **direction=ALL**(채권 외상매출금/미수금 + 채무 외상매입금/미지급금 동시), 여신한도(거래처 creditLimit)/미수 잔액, **월별 aging 버킷**(당월/1개월/2개월/3개월+ 경과). 거래처코드(bizNo)+명+관리코드(partnerCode) 3열.
- 받을어음 보유/만기 + 수금계획 예정을 거래처 행에 병기.
- 읽기 endpoint `GET /accounting/reports/receivables-payables?asOfDate&direction&...`.

### G 슬라이스 분할
- 슬G-1: 받을어음 도메인+입력+목록(Flyway·엔티티·CRUD·UI).
- 슬G-2: 수금계획 도메인+입력+자동제안(Flyway·엔티티·UI).
- 슬G-3: 채권채무 현황 보고서(aging 확장+어음/수금계획 병기).

---

## H — 입출금매칭 (BankTransaction, CSV→KFTC)

### H-1. BankTransaction 도메인 (소스 무관)
- 엔티티 `BankTransaction`: 거래일시, 입출구분(DEPOSIT/WITHDRAWAL), 금액, 잔액, 적요(통장 표시명), 상대계좌/예금주, 은행계좌(우리 측), **소스(CSV_IMPORT/KFTC)**, 외부참조키(중복방지), 매칭상태(UNREFLECTED 미반영/REFLECTED 회계반영/FORCED 강제), 매칭 거래처(partnerId nullable), 매칭 분개(journalId nullable). Flyway VXX. 중복 가드(은행계좌+거래일시+금액+외부키 unique).
- 소스 무관 = import 어댑터만 다름(CSV 파서 / KFTC 클라이언트), 도메인 동일.

### H-2. 통장 CSV/엑셀 import (MVP)
- 업로드 → 은행별 컬럼 매핑(표준화: 일자/입금/출금/잔액/적요/상대) → BankTransaction 적재(중복 skip). 은행 포맷 프로파일(국민/신한/우리 등) 또는 표준 매핑 UI.
- 멱등(재업로드 시 외부참조키로 중복 방지).

### H-3. 입출금 매칭 화면
- 탭: **전체 / 미반영 / 회계반영 / 강제**. 거래 목록(일자·입출·금액·적요·상대·매칭상태).
- 거래처 수동지정: AsyncAutocomplete(거래처코드 bizNo+명). 적요/금액 기반 자동제안(후보).
- 선택 → **입출금보고서**(매칭분 요약) → **거래처원장 POSTED 전기**(매칭 거래처+계정으로 분개 생성). 강제=거래처 없이 임의계정 반영.

### H-4. KFTC 오픈뱅킹 (후속)
- BankTransaction 소스=KFTC 추가. 오픈뱅킹 인증/계좌등록/입출금내역 조회 API. [[external-integration-research]] 법인계좌 리서치 연장. 동일 도메인·매칭 화면 재사용.

### H 슬라이스 분할
- 슬H-1: BankTransaction 도메인+CSV import(Flyway·엔티티·파서·업로드 UI·중복가드).
- 슬H-2: 매칭 화면(탭·거래처 수동지정·자동제안).
- 슬H-3: 입출금보고서+거래처원장 POSTED 전기.
- 슬H-4(후속): KFTC 연동.

---

## 착수 순서 권장
G(읽기 보고서 토대 있음)→H(신규 도메인 무겁고 외부연동) 또는 개발책임자 지정. 각 슬라이스 조기 PR+듀얼리뷰+라운드별 Docker 실QA.

## 미해결(개발책임자 확인 대기)
- G: 어음 부도/배서양도 등 상태 전이 범위(MVP는 보유/추심/결제/부도 4상태로 제안).
- H: CSV 은행 포맷 — 표준 매핑 UI(범용) vs 주거래은행 1~2개 프로파일 먼저? 거래처원장 전기 시 상대계정 결정(적요 규칙/수동).
