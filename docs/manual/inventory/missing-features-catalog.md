# SamhanLogis 누락 기능 종합 Catalog

> **branch** — `feature/integrated-phase-10-step-7-operator-manual`
> **작성일** — 2026-05-09
> **목적** — 개발책임자가 본 docs 만으로 P0 누락 기능을 즉시 파악 가능하도록 하는 종합 카탈로그.
> **출처** — 이카운트 ERP 16 캡처(`docs/migration/ecount-reference/`) + 메모리 가드(`feedback_*.md` / `project_*.md`) + 한국 일반기업회계기준 / 한국 ERP 표준 + 다른 agent 의 `backend-feature-inventory.md` (17 service / 145 endpoint / 누락 42건) + `frontend-feature-inventory.md` (3 client / 27 desktop 라우트 / 누락 8건) + 본 task 검증.
> **상태 표기** — ✅ 완료 / ⏳ 부분 (stub/skeleton/TODO) / ❌ 미구현 / ⚠️ 미흡 (구현은 됐으나 실 운영 부족)

---

## 0. 우선순위 분류 정의

| 등급 | 의미 | 시한 권고 | Phase 11 진입 가드 |
|---|---|---|---|
| 🔴 **P0** | 실 운영 차단 — 미구현 시 운영자가 작업 불가 | **Phase 11 AWS migration 진입 전 의무 구현** | **차단 (BLOCKER)** |
| 🟠 **P1** | 운영 후 1개월 내 — 사용자 불편 / 우회 방법 존재 | Phase 11 후 1개월 내 | 권고 (NON-BLOCKING) |
| 🟡 **P2** | 운영 후 3개월 내 — 편의 기능 | Phase 11 후 3개월 내 | 정보 |
| 🟢 **P3** | long-term — 차세대 기능 | 6개월~1년 | 정보 |

> **메모리 가드 결정** (`project_phase11_aws.md`) — Phase 11 AWS 단일 환경 진입 시 P0 누락은 운영 즉시 차단. 본 catalog 는 Phase 11 진입 전 사전 슬라이스 PR 의 입력으로 사용.

---

## 1. P0 (실 운영 차단) — Phase 11 진입 전 의무 구현 ⭐

### P0-1. 회계 17 보고서 (이카운트 091847 캡처 기준)

> **이카운트 reference** — `docs/migration/ecount-reference/20260509_091847.png` 의 17 보고서 카테고리 (경영자료 9 + 장부 11 + 주요재무제표 5).
> **현재 SamhanLogis** — `accounting-service` `/accounting/balances` (월 시산표 1건) 만 구현. 16건 미구현.
> **연관 메모리** — `project_korean_accounting.md` (한국 일반기업회계기준 표준 계정과목 코드 100/200/300/400/500/800/900 seed required).

| # | 보고서 | 카테고리 | 상태 | 비고 |
|---|---|---|---|---|
| 1 | 자금일보 | 경영자료 | ❌ | 일별 입출금 합계 (한국 ERP 표준) |
| 2 | 현금흐름(입출금내역) | 경영자료 | ❌ | 영업/투자/재무 활동 분류 |
| 3 | 자금현황표 | 경영자료 | ❌ | |
| 4 | 자금증감내역 | 경영자료 | ❌ | |
| 5 | 월별손익분석 | 경영자료 | ❌ | 월 매출/매입/이익 추이 |
| 6 | 월별원가분석 | 경영자료 | ❌ | |
| 7 | 채권/채무수금기간표 | 경영자료 | ❌ | 거래처별 수금 일자 분석 |
| 8 | 채권/채무잔액분석표 | 경영자료 | ❌ | 거래처별 미수금/미지급금 |
| 9 | 회계집계표 | 경영자료 | ❌ | |
| 10 | 계정별원장 | 장부 | ❌ | 계정과목별 분개 시계열 |
| 11 | 계정별거래처별원장 | 장부 | ❌ | 거래처별 매출/매입 원장 |
| 12 | 거래처별계정별원장 | 장부 | ❌ | 위와 축 반전 |
| 13 | 계정별적요별원장 | 장부 | ❌ | |
| 14 | 분개장 | 장부 | ⏳ | `/accounting/journals` 목록 — 인쇄 양식 ❌ |
| 15 | 시산표 | 장부 / 주요재무제표 | ✅ | `TrialBalanceController.byPeriod(yyyyMM)` 단일 |
| 16 | 재무상태표 | 주요재무제표 | ❌ | 자산/부채/자본 (Plan §4) |
| 17 | 손익계산서 | 주요재무제표 | ❌ | 매출/매출원가/판관비/이익 |
| (추가) | 합계잔액시산표 | 주요재무제표 | ❌ | 시산표 + 합계 |

**→ P0-1 누락: 14건. 시한: Phase 11 진입 전 PR 4개 권고 (재무제표 / 일/월 보고서 / 원장 / 분석표).**

### P0-2. 비밀번호 재설정

> **검증 출처** — `services/auth-service/src/main/java/com/samhanair/logis/auth/web/AuthController.java` 65 line. `/auth/login` + `/auth/register` (MASTER) + `/auth/me` 만 존재. password / reset / change 키워드 zero hit.
> **매뉴얼 영향** — `01-로그인.md` 4-2 / FAQ Q2 / `02-메인-화면.md` §3-3 모두 약속만 하고 실 구현 없음 (`scenarios.md` 1.2.1 F1 / F4).

| # | 기능 | 상태 | 비고 |
|---|---|---|---|
| 1 | 사용자 본인 비밀번호 변경 (current → new) | ❌ | `/auth/password` PUT 엔드포인트 부재 |
| 2 | 첫 로그인 시 강제 변경 (force-change-on-first-login flag) | ❌ | `Account` 도메인에 flag 컬럼 없음 |
| 3 | 비밀번호 분실 — 이메일 reset link | ❌ | `notification-service` 와 미연계 |
| 4 | 관리자 강제 reset (MASTER 만) | ❌ | `/auth/internal/accounts/{id}/disable` 만 존재. reset 별도 |
| 5 | 비밀번호 정책 (8자 이상, 특수문자 강제) | ❌ | BCrypt 해싱만 있음 |
| 6 | 5회 실패 시 계정 잠금 + 잠금 해제 endpoint | ❌ | `failed_login_attempts` / `locked_at` 컬럼 부재 |
| 7 | 비밀번호 변경 이력 (마지막 N개 재사용 금지) | ❌ | `password_history` 테이블 부재 |

**→ P0-2 누락: 7건. 시한: Phase 11 진입 전 1 PR 통합.**

### P0-3. 거래처 첨부파일 실 multipart upload

> **검증 출처** — `services/partner-service/src/main/java/com/samhanair/logis/partner/service/MinioAttachmentStorage.java` (28 line `@ConditionalOnProperty(value = "app.partner.minio.enabled", havingValue = "true")`). 기본값 = `NoopAttachmentStorage` fallback.
> **메모리 가드** — `feedback_continuous_docs_sync.md` PR #80/85 패턴 폐기 후 본 PR 통합 의무.

| # | 기능 | 상태 | 비고 |
|---|---|---|---|
| 1 | Entity + REST endpoint (PR #100) | ✅ | `PartnerAttachmentController` |
| 2 | MinIO 실 multipart upload (production profile) | ⏳ | `NoopAttachmentStorage` default fallback. 실 환경 검증 미완료 |
| 3 | 바이러스 스캔 (ClamAV / 외부 API) | ❌ | 운영 시 보안 위협 |
| 4 | 파일 크기 제한 (서버측 검증) | ⚠️ | `application.yml` `multipart.max-file-size` 만, 검증 메시지 한국어 부재 |
| 5 | MIME type 화이트리스트 | ❌ | |
| 6 | 첨부파일 download endpoint (presigned URL TTL) | ⏳ | MinIO 만 5분 TTL — 검증 필요 |
| 7 | 거래처 외 도메인 (slip / journal / employee) 첨부 | ❌ | partner 만 구현 |

**→ P0-3 누락: 6건. 시한: Phase 11 진입 전 1 PR.**

### P0-4. 슬립 인쇄 양식 (출고전표 / 거래명세서 / 세금계산서)

> **검증 출처** — `clients/desktop/src/renderer/print/InvoiceView.tsx` + `DispatchView.tsx` (2 view). 거래명세서 / 세금계산서 인쇄 양식 부재.
> **메모리 가드** — `feedback_print_design_iteration.md` (인쇄 양식 단번 완성 금지 — 3~5회 iteration 의무, PR #21 회고).

| # | 기능 | 상태 | 비고 |
|---|---|---|---|
| 1 | 출고전표 인쇄 (DispatchView) | ✅ | `print/DispatchView.tsx` |
| 2 | 거래명세서 인쇄 (InvoiceView) | ⏳ | `print/InvoiceView.tsx` — legacy v4 일부 |
| 3 | 세금계산서 인쇄 (한국 국세청 양식) | ❌ | 양식 자체 부재 |
| 4 | 세금계산서 발행 (전자세금계산서 — 국세청 e-Tax 연계) | ❌ | API 연계 없음 (NTS Hometax) |
| 5 | 영수증 인쇄 (간이영수증) | ❌ | |
| 6 | 견적서 인쇄 (PrintPreview DS 컴포넌트만 존재) | ⏳ | `frontend-feature-inventory.md` §1.1 #35 PrintPreview 미사용 |
| 7 | 인쇄 미리보기 표준화 (A4 / 88mm 영수증 분기) | ❌ | |

**→ P0-4 누락: 5건. 시한: Phase 11 진입 전 1 PR (세금계산서 + 견적서 인쇄).**

### P0-5. 사용자 / 권한 관리 화면

> **검증 출처** — `services/user-service/src/main/java/com/samhanair/logis/user/web/EmployeeController.java` (POST/GET/POST lookup/POST terminate). desktop `routes/index.tsx` 27 라우트 중 `/admin/users` / `/admin/roles` 부재.
> **메모리 가드** — `feedback_role_naming_full.md` 9 ROLE 풀네임 사용. desktop UI 부재 시 DB 직접 수정 필요 → IT 관리자 의존.

| # | 기능 | 상태 | 비고 |
|---|---|---|---|
| 1 | 직원 목록 / 등록 / 수정 / 퇴사 | ⚠️ | backend ✅ / desktop UI **❌** |
| 2 | 부서 등록 / 수정 / 조직도 | ⚠️ | backend ✅ (`OrgChartController`) / UI ❌ |
| 3 | ROLE 변경 endpoint | ✅ | `/auth/internal/accounts/{id}/role` |
| 4 | ROLE 변경 UI | ❌ | desktop UI 부재 |
| 5 | 권한 매트릭스 시각화 (어떤 ROLE 이 어떤 endpoint) | ❌ | |
| 6 | 신규 직원 등록 흐름 (직원 + 계정 + ROLE 일괄 등록) | ⚠️ | backend `OrgChartSeeder` 만, UI ❌ |
| 7 | 계정 비활성화 / 활성화 토글 UI | ⚠️ | backend ✅ / UI ❌ |

**→ P0-5 누락: 5건 (UI 만). 시한: Phase 11 진입 전 1 PR.**

### P0-6. 거래처 등록 4 탭 화면 (이카운트 091522 / 091541 / 091555 / 091604)

> **이카운트 reference** — 4 탭 (기본 / 거래처정보 / 여신단가 / 부가정보). 사업자등록번호, 종사업장번호, 종목, 업태, 통화, 영업단가그룹, 출하조정률, 여신한도, 수금/지급예정일.
> **현재 SamhanLogis** — `partner-service` backend `PartnerAdminController` (POST/GET/PUT/DELETE) 완성. desktop UI **부재**.

| # | 탭 | 항목 | 상태 |
|---|---|---|---|
| 1 | **기본** | 거래처코드 / 상호 / 대표자 / 업태 / 전화 / 종목 / Fax / Email / 검색창내용 / 담당자 / 주소1·2 / 거래처계층그룹 / 적요 / 특이사항 | backend Partner entity 부분 ✅ / **UI ❌** |
| 2 | **거래처정보** | 사업자등록번호 / 비사업자(내국인/외국인) / 세무신고거래처 / 종사업장번호 / 모바일 / 업종별구분 (일반/관세사) / 통화 / 파일관리 / 거래처그룹1·2 / 홈페이지 / 출하대상거래처 / 거래유형(영업/구매) | backend 일부 ⏳ / **UI ❌** |
| 3 | **여신/단가** | 담당자 / 수금/지급예정일 (4 옵션) / 채권번호관리 / 채무번호관리 / 여신한도 / 출고조정률 / 입고조정률 / 영업단가그룹 / 구매단가그룹 / 여신기간 | backend `PartnerCreditService` ⏳ / **UI ❌** |
| 4 | **부가정보** | 거래처코드 / 순번 / E-mail 2 / 특이사항 / 주소2 / 등록일자 / 은행 / 숫자형추가항목 1·2·3 | backend ❌ / **UI ❌** |

**→ P0-6 누락: 4 탭 + 부가 필드 약 30개. 시한: Phase 11 진입 전 2 PR (탭 1+2 / 탭 3+4).**

### P0-7. 품목 등록 화면 (이카운트 091955 / 092006 / 092016)

> **이카운트 reference** — 7 탭 (기본 / 품목정보 / 수량 / 단가 / 원가 / 부가정보 / 관리대상). 품목구분(원재료/부재료/제품/반제품/상품/무형상품) / 세트여부 / 안전재고 / 부가세율(매출/매입) / 입고/출고/싱글/실외기/멀티 50%/48%/45% / 단풍 35% / 추가수량당수량 / 안전재고관리 (주문서/판매/생산불출/생산입고/창고이동/자가사용/불량처리 7 항목).
> **현재 SamhanLogis** — `product-service` backend (`ProductAdminController` POST/sync 만 / `ProductController` GET / `ProductCatalogController`). desktop UI **부재**.

| # | 탭 | 항목 | 상태 |
|---|---|---|---|
| 1 | 기본 | 품목코드 / 품목명 / 규격 / 단위 / 품목구분 / 세트여부 / 재고수량관리 / 바코드 / 생산공정 / 단가 (입고/출하/출고/싱글/실외기/멀티 4 단계/단풍) / 검색창내용 / 품목계층그룹 | backend Product entity ⏳ / **UI ❌** |
| 2 | 품목정보 | 부가세율(매출/매입) / C-Portal공유 / 이미지 / 파일관리 / 품목그룹1·2·3 / 적요 / 품질검사유형 / 품질검사방법 (전수/샘플링%) | backend ❌ / **UI ❌** |
| 3 | 수량 | 추가수량당수량 / 안전재고관리 7 항목 / 안전재고수량 / 창고별지정 / C-Portal최소주문수량체크/수량/단위 / 재고수량 / 조달기간 / 최소구매단위 / 구매처 | backend ❌ / **UI ❌** |
| 4 | 단가 | (캡처 미확인 — 추정) 단가 정책 / 적용기간 / 환율 | ❌ |
| 5 | 원가 | (추정) 표준원가 / 평균원가 / FIFO/LIFO 정책 | ❌ |
| 6 | 부가정보 | 추가항목 | ❌ |
| 7 | 관리대상 | 관리대상 항목 (lot / serial / 유효기간) | ⚠️ inventory `StockLot` 일부 ✅ / **UI ❌** |

**→ P0-7 누락: 7 탭 거의 전부. 시한: Phase 11 진입 전 2 PR.**

### P0-8. 백업 / 복원 절차 + 운영 매뉴얼 부속

> **메모리 가드** — `project_phase11_aws.md` (RDS auto backup 명시). 매뉴얼 운영자 시점에서 백업/복원 가이드 부재.

| # | 기능 | 상태 | 비고 |
|---|---|---|---|
| 1 | RDS 자동 백업 (AWS auto) | ✅ (Phase 11 후) | 메모리 가드 |
| 2 | 매뉴얼 백업 dump 절차 (DBA 가이드) | ❌ | `pg_dump` 사용법 운영자 매뉴얼 |
| 3 | 복원 절차 (운영자 시점) | ❌ | RTO / RPO 운영 가이드 |
| 4 | 시스템 장애 시 사용자 대응 매뉴얼 | ❌ | "서버에 연결할 수 없습니다" 발생 시 IT 연락 절차 |
| 5 | 데이터 export (CSV / Excel) — 직원이 백업 보관 | ❌ | endpoint 자체 없음 |

**→ P0-8 누락: 4건 (운영 매뉴얼 부속). 시한: Phase 11 진입 후 1 PR — 단, BC 운영 정책 정립 선행.**

---

## 2. P1 (운영 후 1개월 내) — 사용자 불편 / 우회 가능

### P1-1. 알림 (notification) UI 통합

> backend `notification-service` 시드 + `NotificationAdminController` ✅. desktop AppLayout 알림 벨 UI **❌**.

| # | 기능 | 상태 |
|---|---|---|
| 1 | 헤더 🔔 알림 벨 + 뱃지 카운트 | ❌ |
| 2 | 알림 목록 드롭다운 | ❌ |
| 3 | 별표 (★) 보관 | ❌ |
| 4 | 알림 설정 (사용자별 ON/OFF) | ❌ |
| 5 | SMS 발송 (`AligoSmsGateway` ✅ 구현은 있음) | ⏳ |
| 6 | 카카오 알림톡 | ❌ |

### P1-2. 사용자 프로필 화면

| # | 기능 | 상태 |
|---|---|---|
| 1 | 내 프로필 조회 | ❌ |
| 2 | 본인 정보 수정 (전화 / Email) | ❌ |
| 3 | 프로필 사진 upload | ❌ |
| 4 | 알림 설정 | ❌ |
| 5 | 단축키 설정 | ❌ |

### P1-3. 대시보드 강화

> 현재 `DashboardPage.tsx` + dashboard-service 기본 카드 ✅, 3개 카드 placeholder ("준비중") (`frontend-feature-inventory.md` §6.2).

| # | 카드 | 상태 |
|---|---|---|
| 1 | 오늘 매출 요약 | ✅ |
| 2 | 미처리 슬립 카운트 | ✅ |
| 3 | 저재고 알림 (안전재고 미달) | ⏳ placeholder |
| 4 | 미확인 메시지 | ⏳ placeholder |
| 5 | 결재 대기 (관리자) | ⏳ placeholder |
| 6 | 주간 / 월간 추이 차트 | ❌ |

### P1-4. 영업 — 견적서 / 주문서 모바일

> 메모리 가드(`feedback_*.md` 견적/주문 모바일 분리). 영업직원 native 앱은 코드 미존재.

| # | 기능 | 상태 |
|---|---|---|
| 1 | 영업직원 native 앱 (RN Expo) | ❌ (skeleton 만, screens 없음) |
| 2 | 견적서 모바일 작성 | ⏳ legacy WebView 임베드만 |
| 3 | 주문서 모바일 작성 | ❌ |
| 4 | 거래처 검색 / 모바일 등록 | ❌ |
| 5 | 모바일 인쇄 / PDF export | ❌ |

### P1-5. arologis (배차) 화면 보강

| # | 기능 | 상태 |
|---|---|---|
| 1 | 카카오톡 배차 자동 파싱 | backend ✅ / **UI ❌** |
| 2 | 수동 배차 등록 화면 | ⏳ `LinkDispatchListPage` 일부 |
| 3 | 기사 배정 화면 (드래그 / 자동 배정) | ❌ |
| 4 | 인성데이타 퀵프로그램 vendor 연계 (`project_arologis_phase10.md`) | ❌ |
| 5 | GPS 실시간 위치 지도 (관리자 view) | ⏳ data 만 |

### P1-6. 슬립 검색 / 필터 강화

| # | 기능 | 상태 |
|---|---|---|
| 1 | 거래처 + 기간 + 상태 복합 필터 | ⏳ 기간만 |
| 2 | Excel export | ❌ |
| 3 | 즐겨찾기 거래처 | ❌ |

### P1-7. 한국어 형식 강화

| # | 기능 | 상태 |
|---|---|---|
| 1 | 한국 휴대폰 번호 자동 포맷팅 (010-1234-5678) | ⏳ `PhoneInput` DS 컴포넌트만 |
| 2 | 사업자등록번호 자동 포맷 (123-45-67890) | ❌ |
| 3 | 통화 표기 ₩1,234,567 자동 | ⏳ `MoneyInput` DS |
| 4 | 한국 주소 검색 (도로명 주소 API) | ⏳ 이카운트 capture 의 "주소검색" |

---

## 3. P2 (운영 후 3개월 내) — 편의 기능

### P2-1. 모바일 — 창고원 / 회계원 앱 / 알림톡

| # | 기능 | 상태 |
|---|---|---|
| 1 | 창고원 모바일 (입출고 검수) | ❌ (메모리 가드 — Phase 11 후 검토) |
| 2 | 회계원 모바일 | ❌ (메모리 — 불필요 합의) |
| 3 | 카카오 알림톡 (대량 발송) | ❌ |
| 4 | 푸시 알림 (Expo Push) | ❌ |

### P2-2. 검색 / 자동완성 / UX 강화

| # | 기능 | 상태 |
|---|---|---|
| 1 | 글로벌 검색 (Cmd+K) | ❌ |
| 2 | 단축키 일람 화면 | ❌ |
| 3 | 다국어 (영어 / 중국어) | ❌ |
| 4 | 다크 모드 | ❌ |
| 5 | 화면 크기 / 폰트 조절 사용자 설정 | ❌ |
| 6 | 즐겨찾기 메뉴 (사이드바 핀) | ❌ |

### P2-3. 회계 보강

| # | 기능 | 상태 |
|---|---|---|
| 1 | 시산표 분기 / 년 누적 | ⏳ 월별만 |
| 2 | 전기/당기 비교 | ❌ |
| 3 | 부서별 손익 분석 | ❌ |
| 4 | 분개 자동 (슬립 → 분개 자동 생성) | ⏳ 일부 |
| 5 | 결산 마감 lock | ❌ |

### P2-4. 영업 보강

| # | 기능 | 상태 |
|---|---|---|
| 1 | 단가 자동 적용 (이카운트 091636 단가 자동 + 부가세) | ⏳ |
| 2 | 할인 정책 (라인 / 슬립 / 거래처) | ⏳ |
| 3 | 결제 조건 (외상 / 현금 / 분할) | ⏳ |
| 4 | 매출 마감 / 정산 | ❌ |
| 5 | 영업단가그룹 / 구매단가그룹 (이카운트 091555) | ❌ |

### P2-5. 시스템 관리

| # | 기능 | 상태 |
|---|---|---|
| 1 | 감사 로그 조회 화면 (logging-service) | ⏳ backend 만 |
| 2 | 시스템 헬스 모니터 (운영자용) | ❌ |
| 3 | 환경변수 / 설정 관리 UI (dc-config-service) | ⏳ backend 만 |

---

## 4. P3 (long-term) — 차세대 기능

### P3-1. AI / 자동화

| # | 기능 | 상태 |
|---|---|---|
| 1 | 매출 예측 (시계열 ML) | ❌ |
| 2 | 재고 자동 발주 추천 | ❌ |
| 3 | 영수증 OCR (모바일 카메라) | ❌ |
| 4 | 챗봇 / 보이스 입력 | ❌ |

### P3-2. 통합 / 외부 연계

| # | 기능 | 상태 |
|---|---|---|
| 1 | 국세청 e-Tax 전자세금계산서 (P0-4 와 연결) | ❌ |
| 2 | 은행 API 입출금 자동 분개 | ❌ |
| 3 | EDI 연계 (대형 거래처) | ❌ |
| 4 | 엑셀 import / batch upload | ❌ |

### P3-3. 보안 / 컴플라이언스

| # | 기능 | 상태 |
|---|---|---|
| 1 | 2FA / OTP | ❌ |
| 2 | 다중 device 로그인 관리 | ❌ |
| 3 | 개인정보 보호 (마스킹) | ⏳ UUID 가이드만 |
| 4 | GDPR / PIPA 준수 인증 | ❌ |

### P3-4. 모바일 신규 플랫폼

| # | 기능 | 상태 |
|---|---|---|
| 1 | 태블릿 (iPad) 전용 화면 | ❌ |
| 2 | Apple Watch 알림 | ❌ |
| 3 | 키오스크 (창고 입출고 셀프) | ❌ |

---

## 5. 누락 카운트 종합

| 영역 | 🔴 P0 | 🟠 P1 | 🟡 P2 | 🟢 P3 | 합계 |
|---|---:|---:|---:|---:|---:|
| **회계** | 14 | 0 | 5 | 1 | **20** |
| **영업** | 9 (거래처 4탭 + 품목 7탭 일부) | 5 | 5 | 1 | **20** |
| **창고** | 0 | 1 (Excel export) | 1 | 1 (키오스크) | **3** |
| **모바일** | 0 | 5 | 4 | 1 | **10** |
| **인증/관리** | 12 (비밀번호 7 + 사용자UI 5) | 5 (프로필 5) | 3 | 7 (보안 / 2FA) | **27** |
| **출력/인쇄** | 5 | 1 | 0 | 1 (e-Tax) | **7** |
| **첨부/저장** | 6 | 0 | 0 | 1 (OCR) | **7** |
| **알림/대시보드** | 0 | 11 (대시보드 4 + 알림 6) | 0 | 0 | **11** |
| **arologis** | 0 | 5 | 0 | 0 | **5** |
| **백업/운영** | 4 | 0 | 3 | 0 | **7** |
| **검색/UX** | 0 | 4 | 6 | 0 | **10** |
| **외부 연계** | 0 | 0 | 0 | 4 | **4** |
| **합계** | **50** | **37** | **27** | **17** | **131** |

> **주의** — 본 카운트는 sub-feature 기준 (예: P0-2 비밀번호 재설정 = 7 sub). 메인 슬라이스 기준은 **P0 = 8 슬라이스 / P1 = 7 / P2 = 5 / P3 = 4**.

---

## 6. Phase 11 진입 전 P0 의무 구현 권고 (개발책임자 결정 의제)

> **개발책임자 의제** — 본 catalog 의 P0 50건 (8 슬라이스) 은 Phase 11 AWS migration 진입 시 운영 즉시 차단 위험. 단계별 fix PR 권고:

| # | 슬라이스 | sub 카운트 | 권고 PR | 권고 시한 |
|---|---|---|---|---|
| 1 | 회계 17 보고서 보강 | P0 14 sub | 4 PR (재무제표 / 일·월 보고서 / 원장 / 분석표) | Phase 11-2주 |
| 2 | 비밀번호 재설정 + 정책 + 잠금 | P0 7 sub | 1 PR | Phase 11-1주 |
| 3 | 거래처 첨부파일 실 multipart upload | P0 6 sub | 1 PR (MinIO production profile + 바이러스 스캔 stub) | Phase 11-1주 |
| 4 | 슬립 인쇄 양식 (거래명세서 / 세금계산서 / 견적서) | P0 5 sub | 1 PR | Phase 11-2주 |
| 5 | 사용자 / 권한 관리 desktop UI | P0 5 sub | 1 PR | Phase 11-1주 |
| 6 | 거래처 등록 4 탭 desktop UI | P0 ~30 field | 2 PR (탭 1+2 / 3+4) | Phase 11-3주 |
| 7 | 품목 등록 7 탭 desktop UI | P0 ~30 field | 2 PR (탭 1+2 / 3+나머지) | Phase 11-3주 |
| 8 | 백업 / 복원 운영 매뉴얼 부속 | P0 4 sub | 1 PR (docs only) | Phase 11 직후 |

**→ 합계 13 PR / 약 4~6주 소요 예상.**

---

## 7. 누락 발견 출처

| 출처 | 검증 항목 |
|---|---|
| `docs/migration/ecount-reference/*.png` (16 캡처) | 거래처 등록 4 탭 / 판매입력 / 구매입력 / 창고이동입력 / 영업관리현황 5 / 구매관리현황 3 / 견적서 작성 / 품목등록 7 탭 / 회계 17 보고서 / 사원담당등록 |
| `docs/manual/inventory/backend-feature-inventory.md` (다른 agent) | 17 service × 145 endpoint 매트릭스 / 시드 row 1,750 / 누락 후보 42건 |
| `docs/manual/inventory/frontend-feature-inventory.md` (다른 agent) | desktop 27 라우트 / mobile-staff 6 화면 / DS 35 컴포넌트 / 누락 후보 8건 |
| `docs/qa/manual-verification/scenarios.md` (본 PR) | 매뉴얼 4 docs vs 실 구현 — Critical 10 / Major 7 |
| 메모리 가드 | `feedback_role_naming_full.md` 9 ROLE / `feedback_print_design_iteration.md` 인쇄 iteration / `project_korean_accounting.md` 한국 회계 표준 / `project_phase11_aws.md` AWS 단일 환경 |
| 한국 일반기업회계기준 | 17 보고서 표준 / 계정과목 코드 100/200/300/400/500/800/900 |

---

## 8. 변경 이력

| 일자 | 작성자 | 변경 |
|---|---|---|
| 2026-05-09 | TeamMember (W10-7 Stage 1) | 초안 작성. P0 50 / P1 37 / P2 27 / P3 17 = 총 131 sub. 8 P0 슬라이스 / 13 권고 PR. |

---

**Stage 2 이후 갱신 예정** — 매뉴얼 본문 (영업/창고/회계 docs) 작성 시 추가 누락 발견되면 본 catalog 에 추가 row. 다른 agent (BE/FE inventory) 와 cross-check 시 numerical mismatch 시 본 catalog 가 ground truth.
