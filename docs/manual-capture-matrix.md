# 매뉴얼 캡처 매트릭스 — Phase 12 step-6 manual-rewrite

> **상태** — Phase A (캡처 자동화 인프라 + mock 모드 모든 page 가용 보장) 산출물.
> **갱신일** — 2026-05-10
> **소속 PR** — `feature/integrated-phase-12-step-6-manual-rewrite`
> **연관 문서** —
> - `docs/manual/STATUS.md` (43 docs 본문 진행)
> - `docs/manual/inventory/frontend-feature-inventory.md` (route inventory)
> - `tools/manual-capture/capture-manual-all.js` (Phase A 마스터 스크립트)
> - `clients/desktop/src/renderer/api/mock.ts` (mock 데이터)

## 0. 배경

기존 매뉴얼 60+ PNG 의 대부분이 placeholder (27148 / 41046 bytes 동일 size 패턴) 으로 확인되어
사용자 옵션 A — **docs 본문 + 캡처 모두 처음부터 재작성 (3-4주)** 로 결정.

운영 검증 환경 issue (gradle daemon hang) 미해결 → Backend 서비스 가동 없이도 매뉴얼 캡처가 가능해야 함.
→ `clients/desktop` 의 **mock 모드** (`VITE_MOCK_MODE=1`) 를 50+ page 전체에 확장하여
   각 page 가 미가동 BE 환경에서도 한국어 라벨 + 시드 데이터로 mount 가능하도록 보강.

## 1. Phase 분할

| Phase | 산출물 | 시한 | 본 PR |
|---|---|---|---|
| **Phase A** | 캡처 자동화 인프라 + mock 모드 모든 page 가용 + 매트릭스 + sample 캡처 1~2 PNG 검증 | 2026-05-10 | ✅ 본 PR |
| **Phase B** | 80~110 PNG 일괄 캡처 실행 (capture-manual-all.js 전부 통과) | 2026-05-15 | 후속 PR |
| **Phase C** | 43 docs 본문 재작성 (Phase B 산출 PNG 인라인) | 2026-05-31 | 후속 PR |

## 2. 캡처 매트릭스 (43 docs × 80~110 PNG)

| 카테고리 | docs | 화면 (route) | 캡처 PNG (filename) | mock data 의존 |
|---|---|---|---|---|
| 00-시작하기 | 3 | `/login` + `/` + `/admin/users` | 8~10 | login + sidebar + admin |
| 01-영업 | 6 | `/sales` + `/sales/:id` + `/sales/estimates` + `/sales/partner-orders` + `/sales/order-approvals` + `/sales/partner-dc-config` | 15~20 | partner + slip + estimate |
| 02-창고 | 5 | `/purchases` + `/transfers` + `/warehouses` + `/warehouse/closing` + `/warehouse/audit` | 12~15 | inventory + transfer |
| 03-회계 | 4 | `/accounting/accounts` + `/accounting/journals` + `/accounting/journals/:id` + `/accounting/balances` + `/accounting/tax-invoices` | 10~12 | journal + tax-invoice + closing |
| 04-모바일 | 4 | mobile-staff (별도 RN Expo) | 8~10 | driver + signature |
| 05-arologis | 3 | `/arologis/manual` + `/arologis/pre-classify` + `/arologis/dispatch-sms` | 8~10 | dispatch + region |
| 08-실시간 | 10 | 전 50+ page (audit overlay 단편 캡처) | 15~20 | audit + lock + edit-request |
| 06-트러블슈팅 | 5 | 에러 화면 / RoleGuard / 401 / 빈 검색 결과 | 5~8 | 권한 부족 / 로그인 실패 |
| **합계** | **43 docs** | **50+ route** | **80~110 PNG** | |

### 2.1 카테고리별 세부 PNG 명세

#### 00-시작하기 (8~10 PNG)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 01-로그인.md | `00-login-empty.png` | `/#/login` 진입 직후 |
| 01-로그인.md | `00-login-id.png` | ID 입력 강조 박스 |
| 01-로그인.md | `00-login-password.png` | 비밀번호 입력 강조 박스 |
| 01-로그인.md | `00-login-submit.png` | 로그인 버튼 호버 |
| 02-메인-화면.md | `00-main-full.png` | 사이드바 + 본문 전체 |
| 02-메인-화면.md | `00-main-sidebar.png` | 좌측 사이드바 zoom |
| 02-메인-화면.md | `00-main-header.png` | 우상단 사용자 dropdown |
| 03-역할별-권한.md | `00-role-master.png` | MASTER (kimmiseon) 진입 |
| 03-역할별-권한.md | `00-role-sales.png` | SALES (salesuser) 진입 |
| 03-역할별-권한.md | `00-role-warehouse.png` | WAREHOUSE 진입 |

#### 01-영업 (15~20 PNG)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 01-거래처-등록.md | `01-partner-list.png` | `/admin/partners` |
| 01-거래처-등록.md | `01-partner-form.png` | 거래처 신규 등록 Modal |
| 02-거래처-조회.md | `01-partner-search.png` | 검색 결과 + status filter |
| 02-거래처-조회.md | `01-partner-detail.png` | 거래처 상세 (history) |
| 03-슬립-발행.md | `01-slip-list.png` | `/sales` 출고전표 목록 |
| 03-슬립-발행.md | `01-slip-form.png` | `/sales/new` 신규 작성 |
| 03-슬립-발행.md | `01-slip-detail.png` | `/sales/slip-001` 상세 |
| 03-슬립-발행.md | `01-slip-print.png` | `/sales/slip-001/print/dispatch` |
| 04-슬립-결재-라인.md | `01-slip-edit-request.png` | `/admin/slip-edit-requests` |
| 05-거래처-주문.md | `01-partner-order-list.png` | `/sales/partner-orders` |
| 05-거래처-주문.md | `01-partner-order-detail.png` | `/sales/partner-orders/:id` |
| 05-거래처-주문.md | `01-partner-order-approvals.png` | `/sales/order-approvals` |
| 06-견적서.md | `01-estimate-list.png` | `/sales/estimates` |
| 06-견적서.md | `01-estimate-form.png` | `/sales/estimates/new` |
| 06-견적서.md | `01-estimate-detail.png` | `/sales/estimates/:id` |
| 06-견적서.md | `01-estimate-print.png` | 견적서 인쇄 미리보기 |

#### 02-창고 (12~15 PNG)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 01-입고-처리.md | `02-purchase-list.png` | `/purchases` 입고전표 목록 |
| 01-입고-처리.md | `02-purchase-form.png` | `/purchases/new` 신규 |
| 01-입고-처리.md | `02-purchase-detail.png` | `/purchases/slip-003` |
| 02-출고-처리.md | `02-outbound-flow.png` | 9 transition 흐름 (audit) |
| 03-재고-조회.md | `02-warehouse-list.png` | `/warehouses` 4 창고 |
| 03-재고-조회.md | `02-transfer-list.png` | `/transfers` 이동전표 |
| 03-재고-조회.md | `02-transfer-detail.png` | `/transfers/tr-001` |
| 03-재고-조회.md | `02-dps-compare.png` | `/warehouse/dps-compare` |
| 04-매출-마감.md | `02-month-end-closing.png` | `/warehouse/closing` |
| 05-재고-실사.md | `02-audit-list.png` | `/warehouse/audit` |
| 05-재고-실사.md | `02-audit-form.png` | `/warehouse/audit/new` |
| 05-재고-실사.md | `02-audit-detail.png` | `/warehouse/audit/:id` |

#### 03-회계 (10~12 PNG)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 01-분개-입력.md | `03-account-tree.png` | `/accounting/accounts` |
| 01-분개-입력.md | `03-journal-list.png` | `/accounting/journals` |
| 01-분개-입력.md | `03-journal-form.png` | `/accounting/journals/new` |
| 01-분개-입력.md | `03-journal-detail.png` | `/accounting/journals/jv-001` |
| 02-보고서.md | `03-trial-balance.png` | `/accounting/balances` |
| 02-보고서.md | `03-partner-ledger.png` | `/accounting/partner-ledger` |
| 02-보고서.md | `03-statement-batch.png` | `/accounting/statement-batch` |
| 03-세금계산서.md | `03-tax-invoice-list.png` | `/accounting/tax-invoices` |
| 03-세금계산서.md | `03-tax-invoice-form.png` | `/accounting/tax-invoices/new` |
| 03-세금계산서.md | `03-tax-invoice-detail.png` | `/accounting/tax-invoices/:id` |
| 03-세금계산서.md | `03-hometax-export.png` | `/accounting/hometax-export` |

#### 04-모바일 (8~10 PNG, 별도 RN Expo)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 01-기사-앱.md | `04-driver-dashboard.png` | mobile-staff `/` |
| 01-기사-앱.md | `04-driver-list.png` | mobile-staff dispatch list |
| 02-전자-서명.md | `04-driver-signature.png` | `/signature` canvas |
| 02-전자-서명.md | `04-recipient-view.png` | `/mobile/share/:token` |
| 03-영업-앱.md | `04-sales-estimate.png` | mobile estimate webview |
| 04-사진-첨부.md | `04-photo-attach.png` | TBD (P1 backlog) |

> 주: 본 카테고리는 `mobile-staff` RN Expo 별도 client. Phase B 에서 별도 capture-mobile-all.js 로 분리.

#### 05-arologis (8~10 PNG)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 01-카카오톡-배차.md | `05-aro-pre-classify.png` | `/arologis/pre-classify` |
| 01-카카오톡-배차.md | `05-aro-regions.png` | `/admin/regions` |
| 02-수동-배차.md | `05-aro-manual.png` | `/arologis/manual` |
| 02-수동-배차.md | `05-aro-unassigned.png` | `/arologis/unassigned` |
| 02-수동-배차.md | `05-aro-dispatch-sms.png` | `/arologis/dispatch-sms` |
| 03-기사-배정.md | `05-aro-reconcile.png` | `/arologis/dispatch-reconcile` |

#### 06-트러블슈팅 (5~8 PNG)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 01-로그인-실패.md | `06-login-fail.png` | login 실패 모달 |
| 02-화면-표시-안됨.md | `06-empty-list.png` | 빈 검색 결과 |
| 02-화면-표시-안됨.md | `06-loading.png` | loading skeleton |
| 03-인쇄-안됨.md | `06-print-fail.png` | 인쇄 실패 안내 |
| 04-모바일-접속-오류.md | `06-mobile-401.png` | mobile 401 |
| 05-기타.md | `06-role-denied.png` | RoleGuard 거절 |

#### 08-실시간-협업 (15~20 PNG, audit overlay 단편)
| docs 파일 | PNG | 화면 |
|---|---|---|
| 00-실시간-협업-개요.md | `08-overview.png` | sse 연결 + 사용자 list |
| 01-실시간-동기화.md | `08-sse-toast.png` | SSE 수신 toast |
| 02-수정-이력-보기.md | `08-audit-overlay-slip.png` | slip detail audit |
| 02-수정-이력-보기.md | `08-audit-overlay-journal.png` | journal detail audit |
| 02-수정-이력-보기.md | `08-audit-overlay-tax-invoice.png` | tax-invoice detail audit |
| 02-수정-이력-보기.md | `08-audit-overlay-dispatch.png` | arologis dispatch audit |
| 02-수정-이력-보기.md | `08-audit-overlay-user.png` | admin/users audit |
| 03-수정-횟수-카운트.md | `08-revision-chip.png` | revision badge zoom |
| 04-수정-복원.md | `08-revert.png` | 복원 modal |
| 05-수정-요청-워크플로우.md | `08-edit-request-list.png` | `/admin/slip-edit-requests` |
| 05-수정-요청-워크플로우.md | `08-edit-request-form.png` | 수정 요청 form |
| 06-잠금-정책.md | `08-lock-banner-locked.png` | LOCKED_REQUIRES_APPROVAL |
| 06-잠금-정책.md | `08-lock-banner-fully.png` | FULLY_LOCKED |
| 07-창고-직원-수락.md | `08-warehouse-accept.png` | 창고 수락 modal |
| 08-모바일-실시간-알림.md | `08-mobile-push.png` | mobile push toast |
| 09-적용-범위.md | `08-domain-coverage.png` | 9 도메인 매트릭스 |

## 3. 출력 디렉토리 구조

```
docs/manual/screenshots/
├── 00-시작하기/
│   ├── 00-login-empty.png        (≥ 10KB 실 캡처 의무)
│   ├── ...
├── 01-영업/
├── 02-창고/
├── 03-회계/
├── 04-모바일/        (Phase B 후속)
├── 05-arologis/
├── 06-트러블슈팅/
└── 08-실시간-협업/
```

## 4. 검증 기준 (Phase A)

- [x] `tools/manual-capture/capture-manual-all.js` 신규 (Playwright 마스터 스크립트)
- [x] `clients/desktop/src/renderer/api/mock.ts` 보강 (50+ page mock 가용)
- [x] `docs/manual-capture-matrix.md` (본 문서)
- [x] sample 캡처 1~2 PNG 실 검증 (≥ 10KB)
- [x] typecheck PASS

## 5. 실행 절차 (Phase B)

```powershell
# 1) clients/desktop mock 모드 부팅
cd clients/desktop
$env:VITE_MOCK_MODE='1'; npx vite --port 5173 --host 127.0.0.1

# 2) 별도 터미널 — 마스터 캡처 실행
cd tools/manual-capture
node capture-manual-all.js

# 3) 산출물 확인
ls docs/manual/screenshots/**/*.png | wc -l   # 80~110 PNG
```

## 6. PNG 크기 검증 정책

| 크기 | 의미 |
|---|---|
| **< 10KB** | 빈 화면 / mock 미연결 — 재캡처 의무 |
| **10KB ~ 25KB** | 단순 폼 / 빈 list — OK (검수자 판단) |
| **25KB ~ 100KB** | 일반 page (sidebar + 본문) — OK |
| **≥ 100KB** | 상세 화면 / table 다수 row — OK |
| **27148 bytes** | placeholder svg → png — **재캡처 의무 (구 패턴)** |
| **41046 bytes** | placeholder svg → png — **재캡처 의무 (구 패턴)** |

## 7. mock data 보강 범위 (Phase A 본 PR)

기존 mock.ts (slip / partner-orders / journals / accounts / batches / signature) 위에 추가:

- 거래처 (`/admin/partners`) — list 6건 + ACTIVE/SUSPENDED/TERMINATED 분포
- 사용자 (`/admin/users`) — list 8건 + 7 ROLE 분포 + 부서 5건
- region (`/admin/regions`) — 6건 (서울/경기/부산/대구/인천/광주)
- chat-room (`/admin/chat-rooms`) — 4건
- blocked-partners (`/admin/blocked-partners`) — 2건
- warehouses admin (`/admin/warehouses`) — search endpoint
- audit-logs — slip / journal / tax-invoice / dispatch / user 5 도메인 각 3+ revision
- edit-requests — slip 2건 PENDING + WAREHOUSE 수락 1건
- closing — 5월 마감 mock + 시산표 한국 일반기업회계기준 표준 계정코드
- estimate — 3건 DRAFT/SENT/ACCEPTED + line 풍부화
- tax-invoice — 3건 + hometax export csv
- arologis dispatch — 3건 + 권역 분류 + SMS preview
- mobile-staff (별도 client — Phase B 분리)

> **Production 영향 0** — `VITE_MOCK_MODE=1` 환경변수 미설정 시 `getMockResponse` 가
> null 반환 → 실 BE 호출 fallback. mock fixture 는 모두 `mock.ts` 내부 const 로
> tree-shaking 미가능 영역에 둠 (vite minify 후에도 ~30KB 증가, 사용자 dev-only).
