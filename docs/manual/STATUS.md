# SamhanLogis 운영자 매뉴얼 작성 진행 (STATUS)

> **branch** — `feature/integrated-phase-10-step-7-operator-manual`
> **갱신일** — 2026-05-09
> **목적** — 운영자 매뉴얼 작성 stage 별 진행 / 화면 캡처 진행 / 누락 부분 한눈 추적.
> **연관 문서** —
> - `docs/manual/README.md` (사용자 색인)
> - `docs/manual/inventory/backend-feature-inventory.md` (17 service / 145 endpoint)
> - `docs/manual/inventory/frontend-feature-inventory.md` (3 client / 27 desktop 라우트 / 6 mobile)
> - `docs/manual/inventory/missing-features-catalog.md` (P0~P3 종합 누락 — **개발책임자 의제**)
> - `docs/qa/manual-verification/scenarios.md` (단계별 검증 시나리오)

---

## 0. 전체 stage 로드맵

| Stage | 범위 | 상태 | PR | 시한 |
|---|---|---|---|---|
| **Stage 1** | 색인 + 시작하기 (로그인/메인) + Inventory + Catalog + 검증 plan | 진행 중 (본 PR) | W10-7 | 2026-05-09 |
| **Stage 2** | 영업 (거래처 / 슬립 / 견적 / 주문 / 매출 마감) + 창고 (입고/출고/재고/실사) | 미착수 | W10-8 | Phase 11 진입 -2주 |
| **Stage 3** | 회계 (분개/보고서/세금계산서/월말마감) + 모바일 (기사앱/서명/사진) + arologis | 미착수 | W10-9 | Phase 11 진입 -1주 |
| **Stage 4** | 트러블슈팅 / FAQ / 부록 (용어집/단축키) + 백업·복원 운영 매뉴얼 부속 | 미착수 | W10-10 | Phase 11 진입 후 |

---

## 1. Stage 1 (현재 PR — W10-7) 산출물

### 1.1 매뉴얼 본문

| # | 파일 | 상태 | 캡처 | 정정 필요 (scenarios 참조) |
|---|---|---|---|---|
| 1 | `README.md` (색인) | ✅ | — | 🟡 R4/R5 *(예정)* 표기 일관 |
| 2 | `00-시작하기/01-로그인.md` | ✅ | ❌ 4 placeholder | 🔴 F1/F2/F3/F4 (비밀번호/잠금/timeout/reset) |
| 3 | `00-시작하기/02-메인-화면.md` | ✅ | ❌ 3 placeholder | 🔴 F5/F6/F9 + 🟡 F7/F8 |
| 4 | `00-시작하기/03-역할별-권한.md` | ❌ 미작성 | — | Stage 2 |

### 1.2 Inventory / Catalog / Plan (Stage 1 핵심 산출물)

| # | 파일 | 상태 | 작성자 |
|---|---|---|---|
| 1 | `inventory/backend-feature-inventory.md` (17 service 145 endpoint) | ✅ | 다른 agent (BE) |
| 2 | `inventory/frontend-feature-inventory.md` (3 client 27 라우트 6 mobile) | ✅ | 다른 agent (FE) |
| 3 | `inventory/missing-features-catalog.md` (P0 50 + P1 37 + P2 27 + P3 17 = **131 sub**) | ✅ | 본 task (TM) |
| 4 | `qa/manual-verification/scenarios.md` (Critical 10 / Major 7 / Minor 11 / Info 3) | ✅ | 본 task (TM) |
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
| `docs/dev-reports/operator-manual-stage-1.md` | ⏳ TM 통합 PR 시 추가 |

---

## 2. Stage 2 (다음 PR — W10-8) 계획

### 2.1 매뉴얼 본문 (영업)

| # | 파일 | 의존 | 메모 |
|---|---|---|---|
| 1 | `01-영업/01-거래처-등록.md` | **🔴 P0-6 차단** — desktop 4 탭 UI 부재 | 매뉴얼만 약속 시 운영 실패. UI 신규 PR 의존 |
| 2 | `01-영업/02-슬립-발행.md` | ✅ 9 transition 가능 | 캡처 우선 (`scenarios.md` §2.1 #2) |
| 3 | `01-영업/03-견적서.md` | ✅ legacy webview | 임베드 한계 안내 |
| 4 | `01-영업/04-주문서.md` | ✅ desktop / **❌ mobile** | mobile 부분은 Stage 3 |
| 5 | `01-영업/05-매출-마감.md` | **❌ 미구현** (P2-4) | Stage 4 보류 권고 |

### 2.2 매뉴얼 본문 (창고)

| # | 파일 | 의존 |
|---|---|---|
| 1 | `02-창고/01-입고.md` | ✅ slip INBOUND |
| 2 | `02-창고/02-출고.md` | ✅ slip OUTBOUND |
| 3 | `02-창고/03-재고.md` | ✅ `/warehouses` + `/transfers` |
| 4 | `02-창고/04-실사.md` | **❌ 미구현** | Stage 4 보류 권고 |

---

## 3. Stage 3 계획 (W10-9)

| 영역 | docs | 상태 |
|---|---|---|
| 회계 | `03-회계/01-분개-입력.md` | ✅ 작성 가능 |
| 회계 | `03-회계/02-보고서.md` | ⏳ 시산표 ✅ + **재무제표 ❌ 14건** (P0-1) |
| 회계 | `03-회계/03-세금계산서.md` | **❌ 미구현** (P0-4) → Stage 4 보류 |
| 회계 | `03-회계/04-월말-마감.md` | **❌ 미구현** (P2-3) → Stage 4 보류 |
| 모바일 | `04-모바일/01-기사-앱.md` | ✅ |
| 모바일 | `04-모바일/02-전자-서명.md` | ✅ |
| 모바일 | `04-모바일/03-영업-앱.md` | **❌ 미구현** (P1-4) — legacy webview 만 |
| 모바일 | `04-모바일/04-사진-첨부.md` | ❌ 미구현 |
| arologis | `05-arologis/01-카카오톡-배차.md` | backend ✅ / **UI ❌** (P1-5) |
| arologis | `05-arologis/02-수동-배차.md` | ⏳ |
| arologis | `05-arologis/03-기사-배정.md` | **❌ 미구현** |

---

## 4. Stage 4 계획 (W10-10)

| 항목 | 비고 |
|---|---|
| 트러블슈팅 5 항목 | scenarios §1.1 R7 정정 후 작성 |
| FAQ 종합 | 3 stage docs 의 FAQ 통합 |
| 용어집 | 한국 회계 / ERP 용어 + SamhanLogis 도메인 메서드 명 |
| 단축키 일람 | desktop electron-vite 단축키 정리 |
| 백업 / 복원 운영자 가이드 | P0-8 의존 — Phase 11 RDS auto backup 정책 결정 후 |

---

## 5. 의존 / 차단 매트릭스

| Stage | 차단 P0 슬라이스 | 우회 가능 여부 |
|---|---|---|
| Stage 2 영업 | P0-6 거래처 등록 4 탭 / P0-7 품목 등록 7 탭 | ❌ 차단 — 매뉴얼만 작성 시 운영 실패 |
| Stage 3 회계 | P0-1 회계 17 보고서 (14건) / P0-4 세금계산서 | ⏳ 시산표 / 분개만 매뉴얼 가능 |
| Stage 3 모바일 | P1-4 영업 native 앱 | ⏳ legacy webview 만 매뉴얼 |
| Stage 3 arologis | P1-5 카카오톡 UI / 기사 배정 UI | ⏳ backend 만 매뉴얼 (운영자 시점 X) |
| Stage 4 트러블슈팅 | P0-2 비밀번호 재설정 / P0-5 사용자 권한 UI / P0-8 백업 운영 | ❌ IT 관리자 우회 안내만 가능 |

> **개발책임자 결정 필요** — Phase 11 진입 전 P0 13 PR 의 우선순위. `missing-features-catalog.md` §6 권고 표 참조.

---

## 6. 변경 이력

| 일자 | Stage | 변경 | PR |
|---|---|---|---|
| 2026-05-09 | Stage 1 | 색인 + 로그인 + 메인 + Inventory 4 docs + Catalog + Scenarios + STATUS 작성 | W10-7 |
