# SP-01 거래처 관리 메뉴 gap 정합화 — dev-report

작성: 2026-05-16 | 브랜치: `codex/sp-01-partner-ui-menu-gap-audit`

---

## 1. 배경

Samhan Public 거래처 4탭 UI는 P0-6에서 구현되어 있었지만, 실제 진입 경로가 `MASTER + 대표실` 전용 `AdminLayout`에 묶여 있어 `SALES / MANAGER` 사용자가 거래처 목록과 신규 등록 흐름을 자연스럽게 찾기 어려웠다.

또한 매뉴얼은 `SALES / MANAGER / MASTER` 거래처 등록을 안내했지만, `partner-service` 일부 `@PreAuthorize`와 IT는 `SALES` 등록을 403으로 기대하고 있어 문서, 프론트, 백엔드 계약이 어긋나 있었다.

---

## 2. 결정

| 항목 | 결정 |
|---|---|
| 메뉴 위치 | 사이드바 `판매 > 거래처 관리`를 정식 진입점으로 둔다. |
| 공용 라우트 | `/admin/partners`, `/admin/partners/new`는 `AdminLayout` 밖에서 `SALES / MANAGER / MASTER` RoleGuard로 보호한다. |
| 등록 권한 | `POST /api/v1/partners/full`은 `SALES / MANAGER / MASTER` 모두 허용한다. |
| 대표실 quick link | `AdminLayout`의 기존 `admin-nav-partners`는 유지하되 라벨을 `거래처 관리`로 정리하고 공용 화면으로 이동한다. |
| 공개 식별자 | 화면과 API 응답 검증은 `partnerCode / name / bizNo` 중심이며 내부 UUID는 표시하지 않는다. |

---

## 3. 변경 요약

### Backend

- `PartnerAdminController`
  - `GET /admin/partners`
  - `GET /admin/partners/search`
  - 위 목록/검색 endpoint를 `SALES / MANAGER / MASTER`로 확장.
- `Partner4TabController`
  - `POST /api/v1/partners/full` 등록 endpoint를 `SALES / MANAGER / MASTER`로 확장.
- `PartnerAdminControllerIT`
  - SALES 목록/검색/상세 조회 200과 UUID 비노출 assertion 추가.
- `P06ValidationIT`
  - SALES 4탭 등록 성공 201 + `partnerCode` 응답 + `basic.id` 비노출 assertion으로 갱신.

### Desktop

- `partnerApi.ts`
  - `PARTNER_FULL_ROLES`, `canAccessPartnerFull` 공용화.
- `routes/index.tsx`
  - `/admin/partners`, `/admin/partners/new`를 `AdminLayout` 밖 공용 RoleGuard 라우트로 정렬.
- `AppLayout.tsx`
  - `판매` 그룹에 `거래처 관리` entry 추가 (`sidebar-sales-partners`).
- `AdminLayout.tsx`
  - 대표실 인사 셸의 quick link 라벨을 `거래처 관리`로 정리.
- `api/mock.ts`
  - 거래처 목록 mock을 FE `PartnerSummary` 계약과 정렬하고, 4탭 신규 등록/상세 mock 응답을 보강.
- `clients/desktop/playwright/partner-ui-menu-gap/partner-ui-menu-gap.spec.ts`
  - 라우트/사이드바/복귀 경로 static contract 회귀 테스트 추가.

### 문서/QA

- `docs/qa/sp-01-partner-ui-menu-gap-audit/**`
  - SP01-01~14 시나리오, 정합성 SQL, 캡처 체크리스트.
- `docs/manual/01-영업/01-거래처-등록.md`
- `docs/manual/01-영업/02-거래처-조회.md`
- `docs/manual/03-회계/03-세금계산서.md`
- `docs/manual/07-부록/01-FAQ.md`
  - `판매 > 거래처 관리` 진입과 `YYYY/MM/DD-{순번}` 업무번호 원칙 갱신.

---

## 4. 검증 결과

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:partner-service:test --no-daemon --rerun-tasks
```

결과: PASS. `115 tests / 0 failures / 0 errors / 0 skipped`.

```powershell
cd clients\web\design-system
npm run build
```

결과: PASS. Vite production build 및 declaration build 완료.

```powershell
cd clients\desktop
npm run typecheck
npm run lint
npm run build
npx playwright test playwright/partner-ui-menu-gap/partner-ui-menu-gap.spec.ts --reporter=line
```

결과: PASS. typecheck/build 성공, lint는 기존 경고 3건(본 PR 범위 외)만 남음, Playwright static contract 3개 통과.

```powershell
.\scripts\generate-sp-01-partner-ui-menu-gap-screenshots.ps1
Get-ChildItem docs\qa\sp-01-partner-ui-menu-gap-audit\screenshots -Filter *.png
```

결과: PASS. QA 캡처 14장 생성 완료. PR 본문에는 raw 링크 렌더 확인 후 인라인 첨부한다.
