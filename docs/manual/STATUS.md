# SamhanLogis 운영자 매뉴얼 작성 진행 (STATUS)

> **branch (현재)** — `feature/integrated-phase-10-step-7b-operator-manual-stage2`
> **branch (이전 Stage 1)** — `feature/integrated-phase-10-step-7-operator-manual`
> **갱신일** — 2026-05-09 (Stage 2 진행)
> **목적** — 운영자 매뉴얼 작성 stage 별 진행 / 화면 캡처 진행 / 누락 부분 한눈 추적.
> **연관 문서** —
> - `docs/manual/README.md` (사용자 색인)
> - `docs/manual/inventory/backend-feature-inventory.md` (17 service / 145 endpoint)
> - `docs/manual/inventory/frontend-feature-inventory.md` (3 client / 27 desktop 라우트 / 6 mobile)
> - `docs/manual/inventory/missing-features-catalog.md` (P0~P3 종합 누락 — Stage 2 갱신 = 150 sub)
> - `docs/qa/manual-verification/scenarios.md` (Stage 1 검증 시나리오 — 31 항목)
> - `docs/qa/manual-verification/stage2-scenarios.md` (Stage 2 검증 시나리오 — **74 항목**)

---

## 0. 전체 stage 로드맵 (Stage 2 갱신)

| Stage | 범위 | 상태 | PR | 시한 |
|---|---|---|---|---|
| **Stage 1** | 색인 + 시작하기 (로그인/메인) + Inventory + Catalog + 검증 plan | ✅ **완료** | W10-7 | 2026-05-09 |
| **Stage 2** | 영업 5 (거래처/슬립/견적/주문/매출마감) + 창고 4 (입고/출고/재고/실사) + QA plan + Catalog 갱신 + STATUS | 🟡 **진행 중 (본 PR)** | W10-7b | 2026-05-09 |
| **Stage 3** | 회계 4 (분개/보고서/세금계산서/월말마감) + 모바일 4 (기사앱/서명/사진/영업앱) + arologis 3 | ⏳ 미착수 | W10-8 | Phase 11 진입 -2주 |
| **Stage 4** | 트러블슈팅 / FAQ / 부록 (용어집/단축키) + 백업·복원 운영 매뉴얼 부속 | ⏳ 미착수 | W10-9 | Phase 11 진입 후 |

> **변경 사항** — Stage 1 에서 Stage 2 (영업+창고) / Stage 3 (회계+모바일+arologis) / Stage 4 (트러블슈팅+부록) 로 분리 명확화. Stage 1 에서 Stage 2 를 W10-8, Stage 3 을 W10-9 로 표기했던 것 정정.

---

## 1. Stage 1 (Stage 1 PR — W10-7) 산출물 — ✅ 완료

### 1.1 매뉴얼 본문

| # | 파일 | 상태 | 캡처 | 정정 필요 (scenarios 참조) |
|---|---|---|---|---|
| 1 | `README.md` (색인) | ✅ | — | 🟡 R4/R5 *(예정)* 표기 일관 |
| 2 | `00-시작하기/01-로그인.md` | ✅ | ❌ 4 placeholder | 🔴 F1/F2/F3/F4 (비밀번호/잠금/timeout/reset) |
| 3 | `00-시작하기/02-메인-화면.md` | ✅ | ❌ 3 placeholder | 🔴 F5/F6/F9 + 🟡 F7/F8 |
| 4 | `00-시작하기/03-역할별-권한.md` | ❌ 미작성 | — | Stage 2 Designer agent 작업 가능 |

### 1.2 Inventory / Catalog / Plan (Stage 1 핵심 산출물)

| # | 파일 | 상태 | 작성자 |
|---|---|---|---|
| 1 | `inventory/backend-feature-inventory.md` (17 service 145 endpoint) | ✅ | 다른 agent (BE) |
| 2 | `inventory/frontend-feature-inventory.md` (3 client 27 라우트 6 mobile) | ✅ | 다른 agent (FE) |
| 3 | `inventory/missing-features-catalog.md` (P0 50 + P1 37 + P2 27 + P3 17 = **131 sub**) | ✅ | 본 task (TM) |
| 4 | `qa/manual-verification/scenarios.md` (Critical 10 / Major 7 / Minor 11 / Info 3 = 31 항목) | ✅ | 본 task (TM) |
| 5 | `STATUS.md` (본 파일) | ✅ | 본 task (TM) |

### 1.3 화면 캡처

| 항목 | 진행 |
|---|---|
| Playwright capture script setup | ⏳ (다른 agent — DevOps?) |
| 실 스크린샷 (사용자 PC 에서 별도 실행) | ⏳ Stage 1 PR 머지 후 사용자 (개발책임자) PC 별도 실행 후 추가 PR |
| `screenshots/00-시작/01-login-*.png` 4건 | ❌ placeholder |
| `screenshots/00-시작/02-main-*.png` 3건 | ❌ placeholder |
| `screenshots/README.md` (캡처 가이드) | ❌ |

### 1.4 dev-report

| 항목 | 진행 |
|---|---|
| `docs/dev-reports/operator-manual-stage-1.md` | ✅ Stage 1 통합 PR 시 추가 (예상) |

---

## 2. Stage 2 (현재 PR — W10-7b) 산출물 — 🟡 진행 중

### 2.1 매뉴얼 본문 (영업 5 docs)

| # | 파일 | 의존 | 본 PR 상태 | 메모 |
|---|---|---|---|---|
| 1 | `01-영업/01-거래처-등록.md` | **🔴 P0-6 차단** — desktop 4 탭 UI 부재 | ⏳ Designer agent 병렬 작업 | 매뉴얼만 약속 시 운영 실패. UI 신규 PR 의존 |
| 2 | `01-영업/02-슬립-발행.md` | ✅ 9 transition 가능 | ⏳ Designer agent 병렬 작업 | 캡처 우선 (`stage2-scenarios.md` §1.2) |
| 3 | `01-영업/03-견적서.md` | ✅ legacy webview | ⏳ Designer agent 병렬 작업 | 임베드 한계 안내 |
| 4 | `01-영업/04-주문서.md` | ✅ desktop / **❌ mobile** | ⏳ Designer agent 병렬 작업 | mobile 부분은 Stage 3 |
| 5 | `01-영업/05-매출-마감.md` | **❌ 미구현** (P2-4) | ⏳ 미구현 안내 docs (Designer 작성) | Stage 4 보류 권고 / 본 PR 은 안내 docs 만 |

### 2.2 매뉴얼 본문 (창고 4 docs)

| # | 파일 | 의존 | 본 PR 상태 |
|---|---|---|---|
| 1 | `02-창고/01-입고.md` | ✅ slip INBOUND / ⏳ 검수 UI ❌ (P0-9 신규) | ⏳ Designer agent 병렬 작업 |
| 2 | `02-창고/02-출고.md` | ✅ slip OUTBOUND / ⏳ 창고원 모바일 ❌ | ⏳ Designer agent 병렬 작업 |
| 3 | `02-창고/03-재고.md` | ✅ `/warehouses` + `/transfers` | ⏳ Designer agent 병렬 작업 |
| 4 | `02-창고/04-실사.md` | **❌ 미구현** (P2-6 신규) | ⏳ 미구현 안내 docs (Designer 작성) |

### 2.3 QA plan / Catalog 갱신 / STATUS — ✅ 본 task 산출

| # | 파일 | 상태 | 작성자 |
|---|---|---|---|
| 1 | `qa/manual-verification/stage2-scenarios.md` (74 검증 항목 — 🔴 15 / 🟠 29 / 🟡 16 / E 3) | ✅ | **본 task (TM)** |
| 2 | `inventory/missing-features-catalog.md` 갱신 (131 → **150 sub**, +P0-9 +P2-6 슬라이스) | ✅ | **본 task (TM)** |
| 3 | `STATUS.md` Stage 2 진행 갱신 | ✅ | **본 task (TM)** |

### 2.4 화면 캡처 (Stage 2)

| 항목 | 진행 |
|---|---|
| `screenshots/01-영업/01-partner-*.png` ~5건 | ❌ placeholder (P0-6 UI 부재로 mock 사용) |
| `screenshots/01-영업/02-slip-*.png` ~10건 (9 transition + 검색) | ❌ placeholder |
| `screenshots/01-영업/03-estimate-*.png` ~5건 (legacy webview) | ❌ placeholder |
| `screenshots/01-영업/04-order-*.png` ~5건 | ❌ placeholder |
| `screenshots/01-영업/05-monthly-close-mock.png` | ❌ mock only (미구현 안내) |
| `screenshots/02-창고/01-inbound-*.png` ~5건 | ❌ placeholder |
| `screenshots/02-창고/02-outbound-*.png` ~5건 | ❌ placeholder |
| `screenshots/02-창고/03-stock-*.png` ~5건 | ❌ placeholder |
| `screenshots/02-창고/04-cycle-count-mock.png` | ❌ mock only (미구현 안내) |

> Stage 2 캡처 = 약 45 placeholder. 사용자 PC 캡처 작업 후 추가 PR (Stage 1 캡처 PR 과 통합 가능).

### 2.5 dev-report

| 항목 | 진행 |
|---|---|
| `docs/dev-reports/operator-manual-stage-2.md` | ⏳ TM 통합 PR 시 추가 |

---

## 3. Stage 2 검증 종합 (본 task)

`stage2-scenarios.md` 의 74 검증 항목 요약:

| 영역 | docs | 검증 항목 | 🔴 Critical | 🟠 Major | 🟡 Minor |
|---|---|---:|---:|---:|---:|
| 영업 | 거래처 등록 | 8 | 3 | 3 | 2 |
| 영업 | 슬립 발행 | 10 | 1 | 5 | 4 |
| 영업 | 견적서 | 7 | 0 | 4 | 2 |
| 영업 | 주문서 | 8 | 1 | 4 | 1 |
| 영업 | 매출 마감 | 7 | 4 | 3 | 1 |
| 창고 | 입고 | 9 | 1 | 4 | 1 |
| 창고 | 출고 | 10 | 2 | 2 | 2 |
| 창고 | 재고 | 9 | 0 | 2 | 2 |
| 창고 | 실사 | 6 | 3 | 2 | 1 |
| **합계** | **9 docs** | **74** | **15** | **29** | **16** |

> **🔴 Critical 15건** — 매뉴얼만 약속 시 운영 차단 (거래처 4탭 / 매출 마감 / 검수 UI / 실사 / 단가 자동 / 모바일 native 등)
> **🟠 Major 29건** — 우회 안내 / 차후 정정 필수
> **🟡 Minor 16건** — Stage 2 캡처 시 보강

---

## 4. Stage 2 신규 발견 누락 (catalog 입력)

`stage2-scenarios.md` §4 의 13 신규 검증 row → catalog sub 단위 19건 (P0-9 5 + P2-6 5 + 기존 슬라이스 sub 9):

| 분류 | sub 카운트 | 메인 슬라이스 |
|---|---|---|
| 🔴 P0 신규 슬라이스 | +5 | **P0-9 입고 검수 UI** |
| 🟡 P2 신규 슬라이스 | +5 | **P2-6 재고 실사** |
| 🟠 P1 보강 | +5 | P1-2 권한 매트릭스 / P1-4 견적-주문-슬립 chain + 거래처 결재 / P1-6 창고 필터 + Excel export |
| 🟡 P2 보강 | +4 | P2-2 시각화 / P2-4 단가 자동 + 부가세 + 거래처 통합 view |

**→ catalog Stage 1 → Stage 2: 131 → 150 sub (+19). P0 슬라이스 8 → 9 (+P0-9). 권고 PR 13 → 14 (+1).**

---

## 5. Stage 3 backlog list (다음 PR — W10-8)

### 5.1 매뉴얼 본문

| 영역 | docs | 상태 |
|---|---|---|
| 회계 | `03-회계/01-분개-입력.md` | ✅ 작성 가능 |
| 회계 | `03-회계/02-보고서.md` | ⏳ 시산표 ✅ + **재무제표 ❌ 14건** (P0-1) |
| 회계 | `03-회계/03-세금계산서.md` | **❌ 미구현** (P0-4) → 미구현 안내 docs |
| 회계 | `03-회계/04-월말-마감.md` | **❌ 미구현** (P2-3) → 미구현 안내 docs |
| 모바일 | `04-모바일/01-기사-앱.md` | ✅ |
| 모바일 | `04-모바일/02-전자-서명.md` | ✅ |
| 모바일 | `04-모바일/03-영업-앱.md` | **❌ 미구현** (P1-4) — legacy webview 만 |
| 모바일 | `04-모바일/04-사진-첨부.md` | ❌ 미구현 |
| arologis | `05-arologis/01-카카오톡-배차.md` | backend ✅ / **UI ❌** (P1-5) |
| arologis | `05-arologis/02-수동-배차.md` | ⏳ |
| arologis | `05-arologis/03-기사-배정.md` | **❌ 미구현** |

### 5.2 산출물 (Stage 3 — 본 task 패턴 반복)

| 산출물 | 작성자 |
|---|---|
| `qa/manual-verification/stage3-scenarios.md` (회계+모바일+arologis 11 docs ~80-100 검증 항목) | TM (Stage 3 task) |
| `inventory/missing-features-catalog.md` Stage 3 갱신 | TM (Stage 3 task) |
| `STATUS.md` Stage 3 진행 갱신 | TM (Stage 3 task) |

### 5.3 Stage 3 예상 차단

| Stage 3 docs | 예상 차단 |
|---|---|
| 회계 02 보고서 | P0-1 17 보고서 14건 미구현 |
| 회계 03 세금계산서 | P0-4 미구현 |
| 회계 04 월말 마감 | P2-3 미구현 |
| 모바일 03 영업 앱 | P1-4 미구현 |
| 모바일 04 사진 첨부 | 미구현 |
| arologis 01 카카오톡 | UI 미구현 |
| arologis 03 기사 배정 | 미구현 |

---

## 6. Stage 4 backlog list (W10-9 / Phase 11 후)

| 항목 | 비고 |
|---|---|
| 트러블슈팅 5 항목 | scenarios §1.1 R7 정정 후 작성 |
| FAQ 종합 | 3 stage docs 의 FAQ 통합 |
| 용어집 | 한국 회계 / ERP 용어 + SamhanLogis 도메인 메서드 명 |
| 단축키 일람 | desktop electron-vite 단축키 정리 |
| 백업 / 복원 운영자 가이드 | P0-8 의존 — Phase 11 RDS auto backup 정책 결정 후 |
| 매출 마감 정식 docs (replace `01-영업/05` 안내) | P2-4 구현 후 |
| 실사 정식 docs (replace `02-창고/04` 안내) | P2-6 구현 후 |
| 회계 보고서 정식 docs 보강 (replace `03-회계/02` 시산표만) | P0-1 14건 구현 후 |

---

## 7. 의존 / 차단 매트릭스 (Stage 2 갱신)

| Stage | 차단 P0 슬라이스 | 우회 가능 여부 |
|---|---|---|
| Stage 2 영업 | P0-6 거래처 등록 4 탭 / P0-7 품목 등록 7 탭 | ❌ 차단 — 매뉴얼만 작성 시 운영 실패 |
| Stage 2 창고 | **P0-9 입고 검수 UI** *(Stage 2 신규 발견)* | ❌ 차단 — DRAFT → DELIVERED → CONFIRMED 우회만 |
| Stage 3 회계 | P0-1 회계 17 보고서 (14건) / P0-4 세금계산서 | ⏳ 시산표 / 분개만 매뉴얼 가능 |
| Stage 3 모바일 | P1-4 영업 native 앱 | ⏳ legacy webview 만 매뉴얼 |
| Stage 3 arologis | P1-5 카카오톡 UI / 기사 배정 UI | ⏳ backend 만 매뉴얼 (운영자 시점 X) |
| Stage 4 트러블슈팅 | P0-2 비밀번호 재설정 / P0-5 사용자 권한 UI / P0-8 백업 운영 | ❌ IT 관리자 우회 안내만 가능 |

> **개발책임자 결정 필요** — Phase 11 진입 전 P0 14 PR 의 우선순위. `missing-features-catalog.md` §6 권고 표 참조. Stage 2 신규 P0-9 (입고 검수 UI) 추가로 14 PR 권고.

---

## 8. 변경 이력

| 일자 | Stage | 변경 | PR |
|---|---|---|---|
| 2026-05-09 | Stage 1 | 색인 + 로그인 + 메인 + Inventory 4 docs + Catalog (131 sub) + Scenarios (31 항목) + STATUS 작성 | W10-7 |
| 2026-05-09 | Stage 2 | QA plan stage2-scenarios.md (74 항목) + Catalog 갱신 (131 → 150 sub, +P0-9 +P2-6) + STATUS Stage 2 진행 갱신. Designer agent 8~9 docs 매뉴얼 본문 병렬 작성 (별도 task). | W10-7b |
