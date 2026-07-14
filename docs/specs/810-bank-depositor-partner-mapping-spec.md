# #810 — 입출금내역 입금자명↔거래처 매핑 기억 + 자동제안 + 매핑 설정화면

- **상태**: 기획(정찰 완비[이슈 본문]·PM 권고 결정 제시) · **구현 대기(개발책임자 결정 확정)** — 2026-07-15 야간 자율(질문 불가) 준비
- **연관**: 이슈 #810(개발책임자 요청·현재코드 분석 포함)·`accounting-service`(BankTransaction·DepositMatch)·`clients/desktop`
- **민감도**: 🔴 **회계(입금매칭) 도메인** — 실 입금 거래처 배정에 영향. 자율 구현보다 개발책임자 결정 확정 후 착수 권장([[feedback_integrity_domain_policy_preconfirm]]·회계 정책).

## 목표 (이슈 확인)
입출금내역에서 **입금자명↔거래처 매핑을 한 번 수동 지정하면 기억** → 같은 입금자명 재등장 시 자동 거래처. **매핑 설정 화면**에서 별도 CRUD.

## 정찰 요약 (이슈 본문 상세·3요소 모두 부재)
- 수동 지정: `BankTransactionPage` 행별 PartnerAutocomplete → `PATCH match-partner` → `bank_transaction.matched_partner_id`만 세팅(CODEF import는 항상 NULL·매 행 재지정).
- KFTC 자동매칭 `DepositMatchService.resolvePartnerForCounterparty` = 입금자명을 거래처'코드'로 간주한 **정확일치**(퍼지X·상태저장X·매실행 재계산).
- **입금자명 key 저장/제안 테이블·엔티티·로직 없음**·partner-service 별칭 없음·매핑 설정화면 없음.

## 🔑 스코프 결정 (개발책임자 확정 필요 — PM 권고 병기)

| # | 결정 | PM 권고 | 근거 |
|---|---|---|---|
| ① | **매핑 키 정규화** | trim + 내부 공백 1칸 축약 + 대문자화(보수적). raw+normalized 병행 저장 | 과도 정규화(괄호/특수문자 제거)는 상이 입금자 병합 위험 → 보수적 |
| ② | **자동적용 vs 제안** | **자동적용(matched_partner_id 자동 세팅)·사용자 override 항상 가능** | 이슈 의도="자동으로 그 거래처가 나오게". override 시 매핑 갱신 |
| ③ | **학습 시점** | 행 수동지정(`match-partner`) 시 매핑 **자동 upsert**(학습) | "한 번 지정하면 기억" 의도 직결 |
| ④ | **동명이인(1:N)** | 정규화명당 **최신 매핑 1건**(latest-wins). 최신 수동지정이 갱신 | 단순·예측가능. 이력은 감사로그로 보존 |
| ⑤ | **KFTC vs 매핑 우선** | **학습 매핑 우선** > KFTC 코드정확일치 폴백 | 명시적 사용자 지정 > 휴리스틱 코드추정 |
| ⑥ | **관리화면 권한/감사** | `accounting.deposit-mapping`(신규 page-code)·ACCOUNTANT/MANAGER/MASTER·BaseEntity 감사(누가/언제) | 회계 도메인 권한·감사 필수 |

## 구현안 (결정 ①~⑥ 가정·확정 후 조정)
1. **BE(accounting-service)**: `BankDepositorPartnerMapping`(BaseEntity·normalizedName 유니크·rawName·partnerId·updatedBy) + repo + Flyway V+. `BankTransactionService.matchPartner`가 매핑 upsert 동반. import/`DepositMatchService`가 매핑 우선 조회→자동 세팅. 관리 CRUD endpoint(`/accounting/deposit-mappings` GET/POST/PUT/DELETE·@RequirePermission).
2. **FE(clients/desktop)**: `BankTransactionPage` 매핑 자동 세팅 반영 + **매핑 관리 화면 신규**(입금자명↔거래처 목록 CRUD·PartnerAutocomplete).
3. **테스트+라이브 QA**: 입금자명 수동지정→재등장 시 자동 거래처 실증·관리화면 CRUD(#815/#816 패턴·실 회계 DB).

## 캐논 워크플로우
Opus 기획(본 spec) → **개발책임자 ①~⑥ 확정** → 조기 PR → Codex 개발 → Opus 5-agent+fix+라이브QA ↔ Codex 적대 → 0수렴 → PM 종합 → CI → 머지.

## ⚠️ 착수 전 확인 (야간 자율 미착수 사유)
회계(입금매칭) 민감 + 결정 ②(자동적용 vs 제안)·⑥(권한/감사)이 정책성. 특히 자동적용은 잘못된 거래처 자동배정 리스크 → 개발책임자 확정 후 구현. 신규 page-code(⑥)는 권한 seed 동반([[feedback_pgc_c2_widening_option_a]]).
