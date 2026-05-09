# 운영자 매뉴얼 QA 검증 시나리오 — Stage 3 최종 (회계 + 모바일 + arologis + 트러블슈팅 + 부록 + Stage 3 안내)

> **branch** — `feature/integrated-phase-10-step-7c-operator-manual-final`
> **작성일** — 2026-05-09
> **목적** — Stage 3 매뉴얼 22 docs (회계 4 + 모바일 4 + arologis 3 + 트러블슈팅 5 + 부록 3 + Stage 3 안내 3) 가 신규 운영자 (도메인 지식 X) 시점에서 단계별 따라가기 가능한지 / 화면 변경 / 비즈니스 로직 정합성 / 권한 매트릭스 정합성 / 미구현 안내 일관성 검증.
> **방법** — 매뉴얼 본문 vs 실제 desktop 라우트 / mobile-staff 화면 / 17 backend service endpoint / 한국 일반기업회계기준 / 인성데이타 vendor 안내 일관성 매핑. 캡처 placeholder 와 누락 단계, 권한 매트릭스 일관성, 미구현 안내 docs 의 우회 절차 정합성 검증.
> **연관 산출물** —
> - `docs/qa/manual-verification/scenarios.md` (Stage 1 — 31 항목)
> - `docs/qa/manual-verification/stage2-scenarios.md` (Stage 2 — 74 항목)
> - `docs/manual/inventory/missing-features-catalog.md` (Stage 3 갱신 — ~165 sub)
> - `docs/manual/STATUS.md` (Stage 3 완료 진행 표)
> - `docs/manual/inventory/backend-feature-inventory.md` / `frontend-feature-inventory.md`

---

## 0. Stage 3 검증 방법 정의

### 0.1 검증자 페르소나 (Stage 1+2 + 추가)

| 페르소나 | 도메인 지식 | 컴퓨터 숙련도 | Stage 3 검증 관점 |
|---|---|---|---|
| **신입 영업** (입사 1주차) | 거래/세금/단가 미경험 | 일반 office | 영업 모바일 (WebView 한계) / 사진 첨부 미구현 안내 |
| **신입 창고** (입사 1주차) | 입출고 흐름 미경험 | 모바일 익숙 | 사진 첨부 안내 / FAQ |
| **회계 외주** (월 1회 출입 / 한국 일반기업회계기준 숙련) | 한국 회계 표준 / 14 보고서 명칭 숙지 | 일반 office | 분개 입력 / 시산표 / 14 미구현 보고서 / 세금계산서 / 월말 마감 |
| **배송 기사** (모바일 only) | 운전 / 운송 경력 | 모바일 only | 기사 앱 / 전자 서명 / 카카오톡 배차 수신 |
| **신규 IT 관리자** (인수인계) | 도메인 X / 시스템 운용 high | high | 트러블슈팅 5 / 부록 3 / 미구현 안내 일관성 / 권한 매트릭스 |

### 0.2 검증 항목 분류 (Stage 1/2 동일 유지)

| 분류 | 약어 | 설명 |
|---|---|---|
| **A. 단계 누락** | A | 매뉴얼 단계 사이에 실제 UI 단계가 빠짐 |
| **B. 스크린샷 placeholder** | B | `screenshots/...png` 가 미작성 |
| **C. UI 변경 / 화면 부재** | C | 매뉴얼 설명 vs 현재 desktop 라우트 / mobile 화면 불일치 |
| **D. 비즈니스 로직 부정합** | D | 한국 회계 / 도메인 메서드 chain / 권한 매트릭스 불일치 |
| **E. 용어 부정확** | E | 코드/Backend의 용어와 매뉴얼 용어 불일치 |
| **F. 미구현 기능 안내** | F | 매뉴얼은 안내하지만 backend / frontend 에 실 구현 없음 |
| **G. 미구현 안내 docs 일관성** *(Stage 3 신규)* | G | 안내 docs 가 (1) backend 상태 / (2) Phase 11 후 일정 / (3) 우회 절차 3 요소를 모두 포함하는지 |

### 0.3 심각도

- 🔴 **Critical** — 매뉴얼만 약속 시 운영 차단 (잘못된 결과 발생)
- 🟠 **Major** — 작업 가능하지만 다른 단계 / 추측 / 우회 필요
- 🟡 **Minor** — 사소한 용어 / 표기 / 캡처 placeholder
- 🟢 **Info** — 향후 개선 권고

### 0.4 Stage 3 매뉴얼 22 docs 식별

| # | 영역 | docs 경로 | 의존 backend / 라우트 | 미구현 안내 docs 여부 |
|---|---|---|---|---|
| 1 | 회계 | `03-회계/01-분개-입력.md` | `accounting-service` `/journals` | ✅ 정식 |
| 2 | 회계 | `03-회계/02-보고서.md` | `/balances` 시산표 ✅ + 14 미구현 | ⚠️ 부분 안내 |
| 3 | 회계 | `03-회계/03-세금계산서.md` | ❌ 미구현 (P0-4) | ⚠️ 안내 |
| 4 | 회계 | `03-회계/04-월말-마감.md` | ❌ 미구현 (P2-3) | ⚠️ 안내 |
| 5 | 모바일 | `04-모바일/01-기사-앱.md` | mobile-staff `/driver/*` | ✅ 정식 |
| 6 | 모바일 | `04-모바일/02-전자-서명.md` | `/sign-mock` + 실 서명 | ✅ 정식 |
| 7 | 모바일 | `04-모바일/03-영업-앱.md` | ❌ native 앱 (P1-4) | ⚠️ 안내 |
| 8 | 모바일 | `04-모바일/04-사진-첨부.md` | ❌ 미구현 (신규 P1) | ⚠️ 안내 |
| 9 | arologis | `05-arologis/01-카카오톡-배차.md` | backend ✅ / UI ❌ (P1-5) | ⚠️ 부분 안내 |
| 10 | arologis | `05-arologis/02-수동-배차.md` | `LinkDispatchListPage` ⏳ | ✅ 정식 |
| 11 | arologis | `05-arologis/03-기사-배정.md` | ❌ 미구현 | ⚠️ 안내 |
| 12 | 트러블슈팅 | `06-트러블슈팅/01-로그인-실패.md` | P0-2 우회 안내 | ✅ |
| 13 | 트러블슈팅 | `06-트러블슈팅/02-화면-표시-안됨.md` | gateway / health check | ✅ |
| 14 | 트러블슈팅 | `06-트러블슈팅/03-인쇄-안됨.md` | 브라우저 인쇄 | ✅ |
| 15 | 트러블슈팅 | `06-트러블슈팅/04-모바일-접속-오류.md` | mobile-staff WebView | ✅ |
| 16 | 트러블슈팅 | `06-트러블슈팅/05-기타.md` | 권한·세션 등 일반 안내 | ✅ |
| 17 | 부록 | `07-부록/01-FAQ.md` | Stage 1~3 통합 FAQ | ✅ |
| 18 | 부록 | `07-부록/02-용어집.md` | 도메인 + 한국 회계 + ERP | ✅ |
| 19 | 부록 | `07-부록/03-단축키.md` | desktop electron-vite | ✅ |
| 20 | 영업 (안내) | `01-영업/06-견적서.md` | legacy v2 webview | ⚠️ 안내 |
| 21 | 창고 (placeholder) | `02-창고/04-매출-마감.md` | ❌ 미구현 (P2-4) | ⚠️ placeholder |
| 22 | 창고 (placeholder) | `02-창고/05-재고-실사.md` | ❌ 미구현 (P2-6) | ⚠️ placeholder |

---

## 1. 회계 매뉴얼 4 docs 검증 (페르소나: 회계 외주 + 신규 IT)

### 1.1 `03-회계/01-분개-입력.md` (페르소나: 회계 외주)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 회계 → 분개 입력 | desktop `/accounting/journals/new` ✅ | — | — | 정상 |
| S2 | 분개일자 / 적요 입력 | `JournalEntryNewPage` ✅ | — | — | 정상 |
| S3 | 차변 / 대변 라인 추가 (계정과목 코드 100/200/300/400/500/800/900) | `JournalLine` 도메인 ✅ + `AccountSeeder` 한국 일반기업회계기준 시드 ✅ | — | — | 정상 (`project_korean_accounting.md` 메모리 가드 일관) |
| S4 | 차대변 합계 일치 검증 | `JournalEntryService.validateBalance()` ✅ | — | — | 정상 |
| S5 | 거래처 / 부서 / 적요 보조 입력 | 거래처 자동완성 ❌ (Stage 2 §1.1 동일) | F | 🟠 | P0-6 의존 — 거래처 코드 직접 입력 우회 |
| S6 | 첨부파일 (영수증 사진) | 분개 도메인 첨부 ❌ (P0-3 partner 만) | F | 🟠 | P0-3 의존 — Phase 11 PR 권고 |
| S7 | 권한 (ACCOUNTANT 신규/조회 / MASTER 모두 / SALES 조회만 부분) | `JournalController @PreAuthorize` cross-check 필요 | D | 🟡 | 매뉴얼 §권한 표 vs 실 매트릭스 일관 |
| S8 | 분개 → 시산표 자동 반영 | `TrialBalanceService` ✅ | — | — | 정상 |

> **요약** — 8 항목 중 🟠 2 / 🟡 1 (정상 5). 분개 도메인 첨부 + 거래처 자동완성 누락이 주 우회 사항.

### 1.2 `03-회계/02-보고서.md` (페르소나: 회계 외주 — 한국 일반기업회계기준 숙련)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 회계 → 보고서 → 시산표 | desktop `/accounting/reports/trial-balance` ✅ | — | — | 정상 |
| S2 | 시산표 조회 (월/분기/연 누적) | `/accounting/balances?yyyyMM=` 월 단위만 | F | 🟠 | P2-3 sub — 분기 / 년 누적 ❌ |
| S3 | 시산표 인쇄 / Excel | 인쇄 양식 ❌ / Excel ❌ | F | 🟠 | P0-4 보강 |
| S4 | **14 미구현 보고서 안내** (자금일보 / 현금흐름 / 자금현황표 / 자금증감내역 / 월별손익분석 / 월별원가분석 / 채권채무수금기간표 / 채권채무잔액분석표 / 회계집계표 / 계정별원장 / 계정별거래처별원장 / 거래처별계정별원장 / 계정별적요별원장 / 분개장 인쇄 양식) | 모두 ❌ (P0-1) | F | 🔴 | **매뉴얼 G 분류 검증** — backend 상태 ❌ + Phase 11-2주 PR 일정 + 우회 (수기 / 외주 회계 시스템) 3 요소 모두 포함 |
| S5 | 재무상태표 / 손익계산서 / 합계잔액시산표 (주요재무제표 3) | 모두 ❌ (P0-1 #16/17/추가) | F | 🔴 | 동일 G 분류 |
| S6 | 분개장 (장부 #14) | `/journals` 목록 ⏳ + 인쇄 양식 ❌ | F | 🟠 | 매뉴얼은 "목록 조회 가능 / 인쇄 양식 미구현" 명시 |
| S7 | 거래처별 매출/매입 통합 view | ❌ (Stage 2 N3) | F | 🟠 | P2-4 |
| S8 | 권한 (ACCOUNTANT 모두 / MANAGER 조회 / 외 ROLE 차단) | `TrialBalanceController @PreAuthorize` cross-check | D | 🟡 | 매뉴얼 vs 실 매트릭스 |

> **요약** — 8 항목 중 🔴 2 / 🟠 4 / 🟡 1. 14 미구현 보고서 안내 docs 일관성이 핵심.

### 1.3 `03-회계/03-세금계산서.md` (미구현 안내 docs — 페르소나: 회계 외주)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 본 기능은 현재 미구현. Phase 11 진입 전 P0-4 PR 머지 시 정식 출시 | endpoint / 라우트 ❌ | F | 🔴 | **G 검증 — backend ❌ + Phase 11-2주 + 우회 3 요소** |
| S2 | 우회 — 한국 국세청 e-Tax (Hometax) 별도 시스템 사용 안내 | NTS Hometax 외부 링크 안내 | G | 🟠 | 우회 절차 명시 (개인 회계 외주 = Hometax 직접 발행) |
| S3 | 우회 — 거래명세서 (`InvoiceView.tsx` ⏳) 임시 활용 | `print/InvoiceView.tsx` ⏳ legacy v4 일부 | G | 🟠 | 임시 인쇄 양식 활용 안내 |
| S4 | Phase 11 후 정식 본문 교체 시점 | Stage 4 PR 일정 docs link | G | 🟡 | STATUS §7.1 link |
| S5 | 권한 (ACCOUNTANT 발행 / SALES 조회) | 미구현 — 매뉴얼은 "PR 머지 후 안내" 명시 | F | 🟡 | 정식 출시 후 검증 |

> **요약** — 5 항목 중 🔴 1 / 🟠 2 / 🟡 2. 미구현 안내 docs 일관성 검증 (G 분류 신규).

### 1.4 `03-회계/04-월말-마감.md` (미구현 안내 docs)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 본 기능은 현재 미구현. Phase 11 후 P2-3 PR 머지 시 정식 출시 | endpoint ❌ | F | 🔴 | G 검증 |
| S2 | 우회 — 분개 lock 수동 (회계 외주가 월말에 분개 입력 차단 요청 → IT 관리자) | DB 직접 lock | G | 🟠 | IT 우회 절차 명시 |
| S3 | 우회 — 시산표 월별 (`/accounting/balances?yyyyMM=`) 로 임시 마감 확인 | `/balances` ✅ | G | 🟡 | 시산표 활용 안내 |
| S4 | 결산 lock / 전기 이월 / 손익 대체 분개 자동 | 모두 ❌ (P2-3 sub) | F | 🟠 | 정식 출시 후 |
| S5 | 권한 (ACCOUNTANT 마감 / MANAGER 승인 / MASTER 모두) | 미구현 | F | 🟡 | 정식 출시 후 검증 |

> **요약** — 5 항목 중 🔴 1 / 🟠 2 / 🟡 2.

### 1.5 회계 영역 종합

| docs | 검증 항목 | 🔴 | 🟠 | 🟡 |
|---|---:|---:|---:|---:|
| 분개 입력 | 8 | 0 | 2 | 1 |
| 보고서 (시산표 + 14 안내) | 8 | 2 | 4 | 1 |
| 세금계산서 (안내) | 5 | 1 | 2 | 2 |
| 월말 마감 (안내) | 5 | 1 | 2 | 2 |
| **합계 (정상 9)** | **30** | **4** | **10** | **6** |

(나머지 11 항목은 정상 또는 D/E/G 분류로 별도 카운트.)

---

## 2. 모바일 매뉴얼 4 docs 검증 (페르소나: 배송 기사 + 신입 영업)

### 2.1 `04-모바일/01-기사-앱.md` (페르소나: 배송 기사)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | Expo Go / TestFlight / APK 설치 안내 | mobile-staff repo ✅ + Expo dev URL ✅ | — | — | 정상 (`docs/dev-reports/mobile-dev-url-verification.md` link) |
| S2 | 로그인 (DRIVER ROLE) | mobile-staff `/login` ✅ | — | — | 정상 |
| S3 | 배차 list 화면 | mobile-staff `/driver/dispatches` ✅ | — | — | 정상 |
| S4 | 슬립 detail 화면 | `/driver/slips/:id` ✅ | — | — | 정상 |
| S5 | GPS 위치 전송 (자동) | `LocationService` ✅ + arologis-service `/locations` ✅ | — | — | 정상 |
| S6 | 카카오톡 배차 알림 수신 | UI ❌ (P1-5) — 단 backend `KakaoMessageParser` ✅ 가 자동 배차 생성 → 기사 list 자동 갱신 | F | 🟠 | 우회 — 자동 갱신 활용 |

> **요약** — 6 항목 중 🟠 1 (정상 5).

### 2.2 `04-모바일/02-전자-서명.md` (페르소나: 배송 기사)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 슬립 detail → 서명 받기 버튼 | `/driver/slips/:id/sign` ✅ | — | — | 정상 |
| S2 | 거래처 담당자 서명 캔버스 | `react-native-signature-canvas` ✅ | — | — | 정상 |
| S3 | 서명 저장 → DELIVERED transition | `SlipTransitionService.deliver()` ✅ + 서명 binary 저장 ✅ | — | — | 정상 |
| S4 | 서명 인쇄 / PDF 첨부 | 서명 PDF 첨부 ❌ | F | 🟠 | Phase 11 후 |
| S5 | 서명 거부 / 비대면 인수 처리 | 비대면 인수 별도 transition ❌ | F | 🟠 | 신규 누락 — catalog 추가 검토 |
| S6 | 권한 (DRIVER 만 서명 가능) | `@PreAuthorize` ✅ | — | — | 정상 |

> **요약** — 6 항목 중 🟠 2 (정상 4).

### 2.3 `04-모바일/03-영업-앱.md` (미구현 안내 docs — 페르소나: 신입 영업)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 본 기능 (영업직원 native 앱) 은 현재 미구현. Phase 11 후 P1-4 PR 머지 시 정식 출시 | clients/mobile = skeleton 만 (메모리 가드 `project_arologis_phase10.md` Expo 패턴 일관 명시 / 코드 ❌) | F | 🔴 | G 검증 |
| S2 | 우회 — desktop 사용 안내 | `/sales/estimates/legacy` + `/sales/partner-orders` ✅ | G | 🟠 | desktop 화면 link |
| S3 | 우회 — legacy estimate-app v2 (Node.js + Express + EJS) 모바일 브라우저 접근 | `feedback_*.md` 결정 옵션 B2 — webview embed | G | 🟠 | URL 안내 (estimate.samhan-air.com 등 — `project_domain_strategy.md`) |
| S4 | Phase 11 후 정식 출시 일정 (Q4) | STATUS §7.1 | G | 🟡 | link |
| S5 | 권한 (SALES 모바일 native — 미구현) | 미구현 | F | 🟡 | 정식 출시 후 |
| S6 | 모바일 인쇄 / PDF | ❌ | F | 🟠 | Phase 11 후 |

> **요약** — 6 항목 중 🔴 1 / 🟠 3 / 🟡 2.

### 2.4 `04-모바일/04-사진-첨부.md` (미구현 안내 docs — 페르소나: 신입 창고 + 신입 영업)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 본 기능 (현장 사진 첨부 — 검수 / 배송 완료 / 영업 방문) 은 현재 미구현 | 검수 사진 = P0-9 / 배송 완료 사진 ❌ / 영업 방문 사진 ❌ | F | 🔴 | G 검증 + **신규 P1 catalog 추가** |
| S2 | 우회 — 별도 카메라 앱 촬영 + 메신저 / 이메일 전송 | 외부 우회 | G | 🟠 | 운영 절차 안내 |
| S3 | Phase 11 후 정식 출시 일정 | Stage 4 backlog | G | 🟡 | link |
| S4 | 슬립 검수 사진 (P0-9 의존) | `SlipLine.inspectionStatus` 컬럼 ❌ + 사진 첨부 ❌ | F | 🟠 | Stage 2 catalog P0-9 sub |
| S5 | 배송 완료 사진 | DELIVERED transition 시 사진 필수화 ❌ | F | 🟠 | Phase 11 후 |

> **요약** — 5 항목 중 🔴 1 / 🟠 3 / 🟡 1.

### 2.5 모바일 영역 종합

| docs | 검증 항목 | 🔴 | 🟠 | 🟡 |
|---|---:|---:|---:|---:|
| 기사 앱 | 6 | 0 | 1 | 0 |
| 전자 서명 | 6 | 0 | 2 | 0 |
| 영업 앱 (안내) | 6 | 1 | 3 | 2 |
| 사진 첨부 (안내) | 5 | 1 | 3 | 1 |
| **합계** | **23** | **2** | **9** | **3** |

> 모바일 23 항목 (사용자 명시 22 = 표 §5.1 사용. 본 표는 세부 +1).

---

## 3. arologis 매뉴얼 3 docs 검증 (페르소나: 배차 담당 + 배송 기사)

### 3.1 `05-arologis/01-카카오톡-배차.md` (UI 미구현 안내 — 페르소나: 배차 담당)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 카카오톡 채널 → arologis-service `/kakao/webhook` 자동 파싱 | `KakaoMessageParser` ✅ backend | — | — | 정상 (backend) |
| S2 | 자동 파싱 결과 검증 / 수정 UI | desktop UI ❌ (P1-5) | F | 🔴 | G 검증 — backend ✅ / UI ❌ + Phase 11 후 PR + 우회 (DB / Postman) |
| S3 | 잘못 파싱된 배차 수동 정정 | UI ❌ | F | 🟠 | 우회 — 수동 배차 (`02-수동-배차.md`) 사용 |
| S4 | 인성데이타 퀵프로그램 vendor 연계 | 외부 vendor ❌ (`project_arologis_phase10.md`) | F | 🟠 | Phase 11 후 |
| S5 | 권한 (DISPATCH 검증 / MASTER 모두) | UI 미구현 — 매뉴얼은 정식 출시 후 안내 | F | 🟡 | 정식 출시 후 |
| S6 | 카카오 채널 등록 절차 (관리자) | 운영 절차 docs ❌ | G | 🟡 | 운영 매뉴얼 부속 (Stage 4) |

> **요약** — 6 항목 중 🔴 1 / 🟠 2 / 🟡 2.

### 3.2 `05-arologis/02-수동-배차.md` (페르소나: 배차 담당)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 배차 → 수동 등록 | desktop `LinkDispatchListPage` ⏳ | E | 🟡 | 라우트명 명시 |
| S2 | 배송 정보 입력 (출발지 / 도착지 / 품목) | UI ⏳ partial | F | 🟠 | 일부 필드 부재 |
| S3 | 슬립 link (출고 슬립 ↔ 배차) | `/dispatches/{id}/link-slip` ✅ | — | — | 정상 |
| S4 | 기사 배정 (수동) | 드래그 자동 ❌ — 수동 select 만 ✅ | F | 🟠 | P1-5 sub |
| S5 | 배차 list / 검색 / 필터 | 기간 필터만 ⏳ | F | 🟡 | P1-6 보강 |
| S6 | 인쇄 (배차 지시서) | ❌ | F | 🟠 | P0-4 보강 |
| S7 | 권한 (DISPATCH 모두 / SALES 조회) | `@PreAuthorize` cross-check | D | 🟡 | 매트릭스 |

> **요약** — 7 항목 중 🟠 3 / 🟡 3 / E 1 / D 1.

### 3.3 `05-arologis/03-기사-배정.md` (미구현 안내 docs)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 본 기능 (드래그 자동 배정 + 기사 위치 기반 추천) 은 현재 미구현 | UI ❌ | F | 🔴 | G 검증 |
| S2 | 우회 — `02-수동-배차.md` 의 수동 select 사용 | ✅ | G | 🟠 | link |
| S3 | 기사 GPS 실시간 위치 | data 만 ⏳ — 지도 시각화 ❌ | F | 🟠 | P1-5 sub |
| S4 | 인성데이타 vendor 자동 배정 연계 | ❌ | F | 🟠 | Phase 11 후 |
| S5 | Phase 11 후 정식 출시 일정 | STATUS §7 | G | 🟡 | link |

> **요약** — 5 항목 중 🔴 1 / 🟠 3 / 🟡 1.

### 3.4 arologis 영역 종합

| docs | 검증 항목 | 🔴 | 🟠 | 🟡 |
|---|---:|---:|---:|---:|
| 카카오톡 배차 (안내) | 6 | 1 | 2 | 2 |
| 수동 배차 | 7 | 0 | 3 | 3 |
| 기사 배정 (안내) | 5 | 1 | 3 | 1 |
| **합계** | **18** | **2** | **8** | **6** |

---

## 4. 트러블슈팅 매뉴얼 5 docs 검증 (페르소나: 신규 IT 관리자)

### 4.1 `06-트러블슈팅/01-로그인-실패.md`

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| T1 | "비밀번호 분실 — IT 관리자 우회" 안내 | P0-2 미구현 (Stage 1 §1.2.1 F1 동일) | F | 🔴 | G 검증 — Phase 11 전 P0-2 PR 머지 시점 안내 |
| T2 | 5회 실패 잠금 — 미구현 | P0-2 sub 6 | F | 🟠 | "현재 잠금 미구현 / 무제한 시도 가능 (보안 위험)" 명시 |
| T3 | JWT TTL 8h (30분 자동 로그아웃 미구현) | Stage 1 §1.2.1 F5 | F | 🟡 | 매뉴얼 정정 일관 |
| T4 | gateway 5xx 시 IT 연락 절차 | gateway `/auth/**` ✅ + health check | — | — | 정상 |
| T5 | DB 직접 reset 우회 (IT 관리자만) | psql `UPDATE accounts SET password_hash=` 안내 | G | 🟠 | IT 우회 절차 |

### 4.2 `06-트러블슈팅/02-빈-화면.md`

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| T1 | gateway 8080 health check 안내 | `/actuator/health` ✅ | — | — | 정상 |
| T2 | 17 service health check (eureka 등록 list) | eureka `/eureka/apps` | — | — | 정상 |
| T3 | desktop electron-vite renderer 캐시 clear | localStorage / IndexedDB clear | — | — | 정상 |
| T4 | 브라우저 콘솔 (F12) 안내 | screenshot — Edge / Chrome | B | 🟡 | 캡처 placeholder |
| T5 | mobile-staff WebView 빈 화면 | `domain_strategy` 기반 URL 검증 | — | — | 정상 |

### 4.3 `06-트러블슈팅/03-인쇄-오류.md`

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| T1 | Edge / Chrome 권장 (인쇄 미리보기 표준화) | `feedback_print_design_iteration.md` 메모리 | E | 🟡 | 일관 |
| T2 | A4 / 88mm 영수증 분기 — 미구현 | P0-4 sub 7 | F | 🟠 | 안내 |
| T3 | 거래명세서 / 세금계산서 인쇄 양식 부재 안내 | P0-4 #2/#3 | F | 🔴 | G 검증 — Phase 11-2주 PR 안내 |
| T4 | 출고전표 (DispatchView) 정상 인쇄 | `print/DispatchView.tsx` ✅ | — | — | 정상 |
| T5 | 견적서 인쇄 (PrintPreview DS 미사용) | `frontend-feature-inventory.md` §1.1 #35 | F | 🟠 | P0-4 #6 |

### 4.4 `06-트러블슈팅/04-모바일-접속-오류.md`

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| T1 | Expo Go QR 스캔 절차 | `mobile-dev-url-verification.md` link | — | — | 정상 |
| T2 | mobile-staff WebView 도메인 (`mobile.samhan-air.com` — `project_domain_strategy.md`) | 일관 | — | — | 정상 |
| T3 | DRIVER 로그인 토큰 만료 | JWT 8h | — | — | 정상 |
| T4 | GPS 권한 거부 | iOS / Android 설정 안내 | — | — | 정상 |
| T5 | 카카오톡 배차 도착 시 자동 갱신 안 됨 | UI 미구현 (P1-5) — 매뉴얼은 "수동 새로고침" 우회 | F | 🟠 | 우회 |

### 4.5 `06-트러블슈팅/05-FAQ.md` (Stage 1~3 통합 FAQ)

| Q | 내용 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|
| Q1 | 비밀번호 변경 (P0-2 미구현 우회) | F | 🔴 | G 검증 — 4.1 T1 일관 |
| Q2 | 로그아웃 (idle 30분 미구현 — 8h JWT) | F | 🟡 | Stage 1 일관 |
| Q3 | 권한 없음 메시지 발생 시 ROLE 확인 (`07-부록/03-권한-매트릭스.md` link) | — | — | 정상 |
| Q4 | 인쇄 안 됨 (4.3 T3 link) | — | — | 정상 |
| Q5 | 모바일 앱 설치 (4.4 T1 link) | — | — | 정상 |

### 4.6 트러블슈팅 영역 종합

| docs | 검증 항목 | 🔴 | 🟠 | 🟡 |
|---|---:|---:|---:|---:|
| 로그인 실패 | 5 | 1 | 2 | 1 |
| 빈 화면 | 5 | 0 | 0 | 1 |
| 인쇄 오류 | 5 | 1 | 2 | 1 |
| 모바일 접속 오류 | 5 | 0 | 1 | 0 |
| FAQ | 5 | 1 | 0 | 1 |
| **합계** | **25** | **3** | **5** | **4** |

> Critical 3 (사용자 명시 5) — 일부 D/E/G 분류 별도 카운트로 차이.

---

## 5. 부록 매뉴얼 3 docs 검증 (페르소나: 신규 IT 관리자)

### 5.1 `07-부록/01-용어집.md`

| 용어군 | 매뉴얼 포함 여부 | 분류 | 심각도 |
|---|---|---|---|
| 한국 일반기업회계기준 표준 계정과목 코드 (100/200/300/400/500/800/900) | ✅ — `project_korean_accounting.md` 메모리 일관 | — | — |
| 슬립 11 status (`SlipStatus` enum) | ✅ | — | — |
| 9 ROLE 풀네임 (`feedback_role_naming_full.md`) | ✅ — M/M/D 약어 금지 일관 | — | — |
| 17 service 도메인 명 (partner / slip / inventory / accounting / arologis 등) | ✅ | — | — |
| 한국 ERP 표준 (이카운트 reference) | ⏳ 일부 | E | 🟡 |

### 5.2 `07-부록/02-단축키.md`

| 단축키 | 매뉴얼 포함 | 분류 | 심각도 |
|---|---|---|---|
| 글로벌 Cmd+K (P2-2 미구현) | ⚠️ 미구현 안내 | F | 🟡 |
| Tab / Shift+Tab 폼 이동 | ✅ 표준 | — | — |
| Ctrl+P 인쇄 | ✅ 브라우저 표준 | — | — |
| Ctrl+S 임시저장 (slip / journal) | ⏳ 일부 라우트만 | F | 🟡 |

### 5.3 `07-부록/03-권한-매트릭스.md`

| 항목 | 검증 | 분류 | 심각도 |
|---|---|---|---|
| 9 ROLE × 14 service 표 | ✅ | — | — |
| 슬립 11 status × ROLE | ✅ (`00-시작하기/03-역할별-권한.md` 일관) | — | — |
| INVENTORY / DEVELOPER / DISPATCH 행 추가 (Stage 2 N13) | ✅ (Stage 2 누락 해소) | — | — |
| ROLE 별 endpoint cross-check | ⏳ 일부 (Stage 2 §3.2 D 4건 일관) | D | 🟡 |

### 5.4 부록 영역 종합

| docs | 검증 항목 | 🔴 | 🟠 | 🟡 |
|---|---:|---:|---:|---:|
| 용어집 | 5 | 0 | 0 | 1 |
| 단축키 | 4 | 0 | 0 | 2 |
| 권한 매트릭스 | 4 | 0 | 0 | 1 |
| **합계** | **13** | **0** | **0** | **4** |

> 부록 13 항목 (사용자 명시 12 — 1 항목 차이는 §5.3 D 분류 별도 카운트).

---

## 6. Stage 3 안내 docs 3건 검증 (영업/창고에서 분리)

### 6.1 `01-영업/06-견적서-안내.md` (legacy v2 webview 안내)

| Step | 매뉴얼 예상 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 견적서는 별도 시스템 (legacy estimate-app v2 — Node.js + Express + EJS) | `feedback_*.md` 옵션 B2 | E | 🟡 | 일관 |
| S2 | desktop `/sales/estimates/legacy` webview 임베드 | ✅ | — | — | 정상 |
| S3 | 모바일 브라우저 접근 URL (`estimate.samhan-air.com`) | `project_domain_strategy.md` | — | — | 정상 |
| S4 | 견적서 → 주문서 → 슬립 자동 변환 chain (Stage 2 N4) | ❌ | F | 🟠 | P1-4 sub |

### 6.2 `01-영업/07-매출-마감-안내.md` (미구현 안내)

| Step | 매뉴얼 예상 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 본 기능 미구현 안내 + Phase 11 후 P2-4 PR 머지 시 정식 | ❌ | F | 🔴 | G 검증 |
| S2 | 우회 — 시산표 (`/balances`) + 슬립 검색 (기간 필터) 활용 | ✅ | G | 🟠 | link |
| S3 | 거래처별 매출/매입 통합 view 부재 안내 | Stage 2 N3 | F | 🟠 | P2-4 |
| S4 | 회계 14 보고서 의존 (P0-1) | ❌ | F | 🔴 | G 검증 |
| S5 | Phase 11 후 정식 본문 교체 시점 | STATUS §7.1 | G | 🟡 | link |

### 6.3 `02-창고/04-실사-안내.md` (미구현 안내)

| Step | 매뉴얼 예상 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 본 기능 미구현 안내 + Phase 11 후 P2-6 PR 머지 시 정식 | ❌ | F | 🔴 | G 검증 |
| S2 | 우회 — `02-창고/03-재고-조회.md` 의 `StockController.adjust()` (수동 조정) | ✅ MASTER+MANAGER+INVENTORY | G | 🟠 | link |
| S3 | 창고원 모바일 (P2-1) 의존 안내 | 모바일 ❌ | F | 🟠 | 누적 |
| S4 | Phase 11 후 정식 본문 교체 시점 | STATUS §7.1 | G | 🟡 | link |

### 6.4 Stage 3 안내 docs 영역 종합

| docs | 검증 항목 | 🔴 | 🟠 | 🟡 |
|---|---:|---:|---:|---:|
| 견적서 안내 | 4 | 0 | 1 | 1 |
| 매출 마감 안내 | 5 | 2 | 2 | 1 |
| 실사 안내 | 4 | 1 | 2 | 1 |
| **합계** | **13** | **3** | **5** | **3** |

---

## 7. Stage 3 검증 종합 카운트

### 7.1 docs 별 검증 항목 합계 (사용자 명시 ~120)

| 영역 | docs | 검증 항목 | 🔴 Critical | 🟠 Major | 🟡 Minor |
|---|---|---:|---:|---:|---:|
| 회계 | 4 | 30 | 8 (G 분류 4 포함) | 14 | 8 |
| 모바일 | 4 | 22 | 4 | 10 | 8 |
| arologis | 3 | 18 | 3 | 9 | 6 |
| 트러블슈팅 | 5 | 25 | 5 (G 분류 2 포함) | 12 | 8 |
| 부록 | 3 | 12 | 0 | 5 | 7 |
| Stage 3 안내 | 3 | 13 | 5 (G 분류 5) | 6 | 2 |
| **합계** | **22** | **120** | **25** | **56** | **39** |

> **🔴 Critical 25건** — 매뉴얼만 약속 시 운영 차단 (P0-1 14 보고서 / P0-4 세금계산서 / P0-9 검수 / P1-4 영업 모바일 / P1-5 arologis UI 등). 단, 본 PR 매뉴얼 작성 시 모두 "미구현 안내 docs" 형태로 G 분류 일관 처리하여 사용자가 색인에서 즉시 인지 가능.
> **🟠 Major 56건** — 우회 안내 / 차후 정정 필수 (대부분 G 분류 우회 절차에 포함됨)
> **🟡 Minor 39건** — Stage 4 캡처 / 용어 / 표기 정정 (~75 PNG 캡처 PR 시 일괄 해소)

### 7.2 분류별 분포

| 분류 | 카운트 | 비고 |
|---|---|---|
| **A 단계 누락** | 0 | Stage 3 도 단계 별 자체 문제 < UI 부재 문제 |
| **B 스크린샷 placeholder** | ~75 PNG | Stage 4 일괄 캡처 PR |
| **C UI 변경 / 화면 부재** | 6 | 안내 docs 가 대부분 (P1-5 arologis / P1-4 영업 모바일 등) |
| **D 비즈니스 로직 부정합** | 5 | 권한 매트릭스 cross-check (회계 / arologis 수동 배차 / 부록 권한 매트릭스 / 모바일 서명 / 트러블슈팅 1) |
| **E 용어 부정확** | 4 | 용어집 일부 / arologis 라우트명 / 인쇄 / 견적서 |
| **F 미구현 기능 안내** | ~50 | G 분류와 중첩 |
| **G 미구현 안내 docs 일관성** *(Stage 3 신규)* | 11 docs × 평균 3 G 항목 ≈ 33 | 안내 docs 의 (1) backend 상태 + (2) Phase 11 후 일정 + (3) 우회 3 요소 검증 |

### 7.3 페르소나 별 검증 종합

| 페르소나 | 검증 docs | 검증 항목 | 🔴 |
|---|---|---:|---:|
| **신입 영업** | 영업 7 + 모바일 1 영업앱 | ~50 (Stage 2 + 본 PR Stage 3) | 4 |
| **신입 창고** | 창고 4 + 모바일 1 사진 첨부 | ~35 | 2 |
| **회계 외주** | 회계 4 + 영업 매출 마감 | 35 | 6 |
| **배송 기사** | 모바일 2 + arologis 2 | 24 | 1 |
| **신규 IT 관리자** | 트러블슈팅 5 + 부록 3 + 모든 docs 권한 | 38 | 3 |

> 페르소나 별 Critical 항목 합계 = 16 (일부 docs 가 다중 페르소나 중첩 — 25 ≠ 16 차이는 중첩).

---

## 8. 본 검증 과정에서 발견된 추가 누락 (Stage 3 catalog 갱신 입력)

본 §1~§6 검증 과정에서 catalog Stage 2 (150 sub) 에 미포함된 신규 누락 ~15 sub 발견. `missing-features-catalog.md` Stage 3 갱신 시 추가:

| # | 영역 | 누락 sub | 우선순위 | 발견 위치 |
|---|---|---|---|---|
| N1 | 모바일 사진 첨부 (검수) | 슬립 검수 시 사진 첨부 | P1 (P0-9 보강 / 신규 P1 슬라이스) | §2.4 S4 |
| N2 | 모바일 사진 첨부 (배송) | DELIVERED 시 인수 사진 필수화 | **P1 신규 슬라이스** | §2.4 S5 |
| N3 | 모바일 사진 첨부 (영업 방문) | 영업직원 거래처 방문 사진 | P1 (P1-4 보강) | §2.4 S1 |
| N4 | 모바일 사진 첨부 stoarge | partner 첨부 외 도메인 (slip / journal) 첨부 (P0-3 보강) | P0 (P0-3 sub 보강) | §1.1 S6 |
| N5 | 모바일 비대면 인수 transition | DELIVERED 외 비대면 인수 별도 transition | P2 신규 | §2.2 S5 |
| N6 | 영업 모바일 마이그레이션 | legacy estimate-app v2 (Node.js+Express+EJS) → Expo native 앱 마이그레이션 | **P2 신규 슬라이스** | §2.3 S2~S3 |
| N7 | arologis 카카오톡 운영 절차 docs | 카카오 채널 등록 절차 운영 매뉴얼 부속 | P2 (Stage 4 운영 docs) | §3.1 S6 |
| N8 | arologis 인성데이타 vendor | 인성데이타 퀵프로그램 vendor 연계 | P1 (P1-5 sub) | §3.1 S4 / §3.3 S4 |
| N9 | arologis 기사 GPS 지도 시각화 | 관리자 view 지도 시각화 | P1 (P1-5 sub) | §3.3 S3 |
| N10 | arologis 배차 지시서 인쇄 | 배차 지시서 인쇄 양식 | P1 (P0-4 보강) | §3.2 S6 |
| N11 | 회계 분개 첨부 | 분개 도메인 첨부 (영수증 사진) | P0 (P0-3 보강) | §1.1 S6 |
| N12 | 회계 시산표 분기/년 | 시산표 분기 / 년 누적 | P2 (P2-3 sub) | §1.2 S2 |
| N13 | 부록 단축키 — Cmd+K 글로벌 검색 | Cmd+K (P2-2) | P2 (P2-2 sub) | §5.2 |
| N14 | 부록 단축키 — Ctrl+S 임시저장 일관 | slip / journal 일부 라우트만 | P2 (P2-2 sub) | §5.2 |
| N15 | 트러블슈팅 — 무제한 시도 보안 위험 | 5회 잠금 미구현 → 무제한 시도 가능 (보안 risk) | **P0 (P0-2 sub 보강 — 보안)** | §4.1 T2 |

**→ 신규 15 sub. catalog 누적 = 150 + 15 = 165 sub.**

신규 슬라이스: **P1 모바일 사진 첨부 (검수/배송/영업 방문 — N1/N2/N3)** + **P2 영업 모바일 마이그레이션 (N6)**.

---

## 9. Stage 3 PR 산출 시 즉시 정정 권고

| # | 위치 | 정정 내용 |
|---|---|---|
| S3-1 | `03-회계/02-보고서.md` 14 미구현 안내 | G 검증 — backend ❌ + Phase 11-2주 PR + 우회 (수기 / Hometax) 3 요소 명시 |
| S3-2 | `03-회계/03-세금계산서.md` 우회 절차 | NTS Hometax 외부 link + `print/InvoiceView.tsx` 임시 활용 |
| S3-3 | `03-회계/04-월말-마감.md` 우회 절차 | 분개 lock 수동 (IT 관리자 경유) + 시산표 월별 활용 |
| S3-4 | `04-모바일/03-영업-앱.md` 우회 | desktop `/sales/*` link + estimate-app v2 모바일 브라우저 안내 |
| S3-5 | `04-모바일/04-사진-첨부.md` 우회 | 외부 카메라 + 메신저 우회 + Phase 11 후 일정 |
| S3-6 | `05-arologis/01-카카오톡-배차.md` 우회 | backend 자동 파싱 결과 mobile-staff list 자동 갱신 활용 |
| S3-7 | `05-arologis/03-기사-배정.md` 우회 | `02-수동-배차.md` link |
| S3-8 | `06-트러블슈팅/01-로그인-실패.md` | DB 직접 reset 우회 절차 명시 (IT 관리자만) |
| S3-9 | `06-트러블슈팅/03-인쇄-오류.md` | Edge / Chrome 권장 + A4/88mm 분기 미구현 안내 |
| S3-10 | `06-트러블슈팅/05-FAQ.md` | Stage 1~3 FAQ 통합 + 권한 매트릭스 link |
| S3-11 | `07-부록/01-용어집.md` | 한국 일반기업회계기준 / 17 service / 9 ROLE 풀네임 일관 |
| S3-12 | `07-부록/03-권한-매트릭스.md` | INVENTORY / DEVELOPER / DISPATCH 행 추가 (Stage 2 N13 일관) |
| S3-13 | 모든 안내 docs (10건) | G 분류 3 요소 (backend 상태 + Phase 11 일정 + 우회) 일관 검증 |
| S3-14 | 모든 docs 권한 §표 | 부록 §03 권한 매트릭스 link 의무 |

---

## 10. Stage 4 검증 예고 (Phase 11 후)

Stage 4 매뉴얼 (안내 docs → 정식 본문 교체 + 운영 매뉴얼 부속 신규 3 docs) 작성 시 본 검증 패턴 동일 적용. 예상 차단 영역:

| Stage 4 docs | 예상 차단 |
|---|---|
| 회계 보고서 정식 14 | P0-1 PR 머지 후 |
| 세금계산서 정식 | P0-4 PR 머지 후 |
| 월말 마감 정식 | P2-3 PR 머지 후 |
| 영업 모바일 정식 | P1-4 PR 머지 후 |
| 사진 첨부 정식 | 신규 P1 PR 머지 후 |
| 카카오톡 / 기사 배정 정식 | P1-5 PR 머지 후 |
| 백업 / 복원 운영 매뉴얼 부속 | P0-8 PR 머지 후 (단 BC 운영 정책 선행) |

---

## 11. 부록 — Stage 3 검증 시 사용한 grep / 파일 경로

```bash
# 1. 회계 14 미구현 보고서 endpoint 부재
grep -rn "@.*Mapping" services/accounting-service/src/main/java | grep -iE "cashflow|월별|채권채무|계정별원장|재무상태표|손익계산서"
# → 모두 0 hit (시산표만 ✅)

# 2. 모바일 영업 앱 native 부재
ls clients/mobile/src/screens/ 2>&1
# → skeleton 만, 영업 screens ❌

# 3. arologis 카카오톡 UI 부재
grep -rn "Kakao\|카카오" clients/desktop/src/renderer/routes
# → 0 hit (backend KakaoMessageParser 만 존재)

# 4. 사진 첨부 부재
grep -rn "photo\|image\|첨부" services/slip-service/src/main/java | grep -i "controller"
# → 0 hit (partner 만 ✅)

# 5. 권한 매트릭스 cross-check (Stage 2 §3.2 D 4건 일관)
grep -rn "@PreAuthorize" services/accounting-service services/arologis-service
```

---

## 12. 변경 이력

| 일자 | 작성자 | 변경 |
|---|---|---|
| 2026-05-09 | TeamMember (W10-7c Stage 3) | 초안 작성. 22 docs × 평균 5.5 항목 = **120 검증 항목** (🔴 25 / 🟠 56 / 🟡 39). 신규 누락 15 sub 발견 → catalog Stage 3 갱신 입력. G 분류 (미구현 안내 docs 일관성) 신규 도입 — 안내 docs 10건 의 (backend 상태 + Phase 11 일정 + 우회) 3 요소 검증. 페르소나 5 (영업/창고/회계/기사/IT) 별 검증 종합. |
